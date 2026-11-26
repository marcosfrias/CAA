package com.caa.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lee los datos de empresas y fechas de libros .xlsx sin fijar columnas por posición. */
final class ImportadorExcel {

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final Pattern ANIO = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern REFERENCIA_CELDA = Pattern.compile("([A-Z]+)");
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            new DateTimeFormatterBuilder().appendPattern("d/M/").appendValue(ChronoField.YEAR, 4)
                    .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE,
            new DateTimeFormatterBuilder().appendPattern("d/M/")
                    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                    .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT));

    private ImportadorExcel() {
    }

    static Resultado leer(Path archivo) throws IOException {
        try (ZipFile libro = new ZipFile(archivo.toFile())) {
            List<String> textos = leerTextosCompartidos(libro);
            Document relacionesDoc = xml(libro, "xl/_rels/workbook.xml.rels");
            Map<String, String> relaciones = new HashMap<>();
            for (Element rel : elementos(relacionesDoc, NS_PKG_REL, "Relationship")) {
                relaciones.put(rel.getAttribute("Id"), rel.getAttribute("Target"));
            }

            Document libroDoc = xml(libro, "xl/workbook.xml");
            for (Element hojaXml : elementos(libroDoc, NS, "sheet")) {
                String relacion = hojaXml.getAttributeNS(NS_REL, "id");
                String rutaHoja = rutaHoja(relaciones.get(relacion));
                Document hoja = xml(libro, rutaHoja);
                List<Element> filas = elementos(hoja, NS, "row");
                Cabeceras cabeceras = buscarCabeceras(filas, textos);
                if (cabeceras == null) continue;

                List<Registro> registros = new ArrayList<>();
                int fechasInvalidas = 0;
                for (Element fila : filas) {
                    int numeroFila = entero(fila.getAttribute("r"), 0);
                    if (numeroFila <= cabeceras.fila) continue;
                    Map<Integer, String> valores = celdas(fila, textos);
                    String empresa = valor(valores, cabeceras.empresa).trim();
                    if (empresa.isEmpty()) continue;

                    Registro registro = new Registro(numeroFila, empresa,
                            valor(valores, cabeceras.actividad).trim(),
                            valor(valores, cabeceras.expediente).trim(),
                            valor(valores, cabeceras.email).trim(),
                            valor(valores, cabeceras.domicilio).trim(),
                            valor(valores, cabeceras.descripcion).trim(),
                            valor(valores, cabeceras.consultor).trim());
                    for (ColumnaFecha columna : cabeceras.fechas) {
                        String contenido = valor(valores, columna.indice).trim();
                        if (contenido.isEmpty()) continue;
                        LocalDate fecha = fecha(contenido);
                        if (fecha == null) fechasInvalidas++;
                        else registro.fechas.add(new Fecha(columna.nombre, fecha));
                    }
                    for (ColumnaResolucion columna : cabeceras.resoluciones) {
                        String contenido = valor(valores, columna.indice).trim();
                        if (!contenido.isEmpty()) registro.resoluciones.add(new Resolucion(columna.anio, contenido));
                    }
                    registros.add(registro);
                }
                return new Resultado(hojaXml.getAttribute("name"), registros, fechasInvalidas);
            }
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("No se pudo leer el Excel: " + error.getMessage(), error);
        }
        throw new IOException("No encontré una hoja con columnas de empresa y actividad.");
    }

    private static Cabeceras buscarCabeceras(List<Element> filas, List<String> textos) {
        int maximo = Math.min(filas.size(), 30);
        for (int i = 0; i < maximo; i++) {
            Element fila = filas.get(i);
            Map<Integer, String> celdas = celdas(fila, textos);
            Cabeceras resultado = new Cabeceras(entero(fila.getAttribute("r"), i + 1));
            for (Map.Entry<Integer, String> entrada : celdas.entrySet()) {
                String nombre = normalizar(entrada.getValue());
                int indice = entrada.getKey();
                if (nombre.contains("RAZON SOCIAL") || nombre.equals("EMPRESA") || nombre.equals("EMPRESAS")) {
                    resultado.empresa = indice;
                } else if (nombre.equals("RUBRO") || nombre.equals("ACTIVIDAD") || nombre.startsWith("ACTIVIDAD ")) {
                    resultado.actividad = indice;
                } else if (nombre.contains("EXPTE") || nombre.contains("EXPEDIENTE")) {
                    resultado.expediente = indice;
                } else if (nombre.contains("CORREO") || nombre.contains("EMAIL") || nombre.contains("CONTACTO")) {
                    resultado.email = indice;
                } else if (nombre.contains("DOMICILIO")) {
                    resultado.domicilio = indice;
                } else if (nombre.contains("DESCRIPCION") && nombre.contains("PROCESO")) {
                    resultado.descripcion = indice;
                } else if (nombre.contains("CONSULTOR")) {
                    resultado.consultor = indice;
                }
                if (nombre.contains("FECHA")) resultado.fechas.add(new ColumnaFecha(indice, entrada.getValue().trim()));
                if (nombre.contains("RESOLUCION")) {
                    Matcher matcher = ANIO.matcher(nombre);
                    if (matcher.find()) {
                        resultado.resoluciones.add(new ColumnaResolucion(indice, Integer.parseInt(matcher.group())));
                    }
                }
            }
            if (resultado.empresa >= 0 && resultado.actividad >= 0) return resultado;
        }
        return null;
    }

    private static LocalDate fecha(String contenido) {
        try {
            double numero = Double.parseDouble(contenido.replace(',', '.'));
            if (numero >= 1 && numero < 200000) {
                LocalDate candidata = LocalDate.of(1899, 12, 30).plusDays((long) Math.floor(numero));
                if (candidata.getYear() >= 1900 && candidata.getYear() <= 2200) return candidata;
            }
        } catch (RuntimeException ignorado) {
            // Puede ser una fecha escrita como texto.
        }
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                LocalDate candidata = LocalDate.parse(contenido, formato);
                if (candidata.getYear() >= 1900 && candidata.getYear() <= 2200) return candidata;
            } catch (RuntimeException ignorado) {
                // Prueba el formato siguiente.
            }
        }
        return null;
    }

    private static List<String> leerTextosCompartidos(ZipFile libro) throws Exception {
        ZipEntry entrada = libro.getEntry("xl/sharedStrings.xml");
        if (entrada == null) return List.of();
        Document documento;
        try (InputStream datos = libro.getInputStream(entrada)) {
            documento = xml(datos);
        }
        List<String> resultado = new ArrayList<>();
        for (Element item : elementos(documento, NS, "si")) {
            StringBuilder texto = new StringBuilder();
            for (Element nodo : elementos(item, NS, "t")) texto.append(nodo.getTextContent());
            resultado.add(texto.toString());
        }
        return resultado;
    }

    private static Document xml(ZipFile archivo, String ruta) throws Exception {
        ZipEntry entrada = archivo.getEntry(ruta);
        if (entrada == null) throw new IOException("Falta el archivo interno " + ruta);
        try (InputStream datos = archivo.getInputStream(entrada)) {
            return xml(datos);
        }
    }

    private static Document xml(InputStream entrada) throws Exception {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return fabrica.newDocumentBuilder().parse(entrada);
    }

    private static List<Element> elementos(Document documento, String espacio, String nombre) {
        return elementos(documento.getDocumentElement(), espacio, nombre);
    }

    private static List<Element> elementos(Element raiz, String espacio, String nombre) {
        NodeList nodos = raiz.getElementsByTagNameNS(espacio, nombre);
        List<Element> resultado = new ArrayList<>(nodos.getLength());
        for (int i = 0; i < nodos.getLength(); i++) resultado.add((Element) nodos.item(i));
        return resultado;
    }

    private static Map<Integer, String> celdas(Element fila, List<String> textos) {
        Map<Integer, String> resultado = new HashMap<>();
        for (Element celda : elementos(fila, NS, "c")) {
            int columna = columna(celda.getAttribute("r"));
            String tipo = celda.getAttribute("t");
            String valor = textoHijo(celda, "v");
            if ("s".equals(tipo) && !valor.isEmpty()) {
                int indice = entero(valor, -1);
                valor = indice >= 0 && indice < textos.size() ? textos.get(indice) : "";
            } else if ("inlineStr".equals(tipo)) {
                StringBuilder texto = new StringBuilder();
                for (Element nodo : elementos(celda, NS, "t")) texto.append(nodo.getTextContent());
                valor = texto.toString();
            } else if ("b".equals(tipo)) {
                valor = "1".equals(valor) ? "TRUE" : "FALSE";
            }
            resultado.put(columna, valor);
        }
        return resultado;
    }

    private static String textoHijo(Element raiz, String nombre) {
        NodeList nodos = raiz.getElementsByTagNameNS(NS, nombre);
        return nodos.getLength() == 0 ? "" : nodos.item(0).getTextContent();
    }

    private static String rutaHoja(String destino) throws IOException {
        if (destino == null || destino.isBlank()) throw new IOException("La hoja no tiene una ruta válida.");
        String ruta = destino.replace('\\', '/');
        if (ruta.startsWith("/")) return ruta.substring(1);
        return ruta.startsWith("xl/") ? ruta : "xl/" + ruta;
    }

    private static int columna(String referencia) {
        Matcher matcher = REFERENCIA_CELDA.matcher(referencia);
        if (!matcher.find()) return -1;
        int resultado = 0;
        for (char letra : matcher.group(1).toCharArray()) resultado = resultado * 26 + letra - 'A' + 1;
        return resultado - 1;
    }

    private static int entero(String valor, int predeterminado) {
        try {
            return Integer.parseInt(valor);
        } catch (RuntimeException ignorado) {
            return predeterminado;
        }
    }

    private static String valor(Map<Integer, String> valores, int indice) {
        return indice < 0 ? "" : valores.getOrDefault(indice, "");
    }

    static String normalizar(String texto) {
        String plano = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        return plano.replaceAll("[^A-Z0-9]+", " ").trim();
    }

    static final class Resultado {
        final String hoja;
        final List<Registro> registros;
        final int fechasInvalidas;

        Resultado(String hoja, List<Registro> registros, int fechasInvalidas) {
            this.hoja = hoja;
            this.registros = registros;
            this.fechasInvalidas = fechasInvalidas;
        }
    }

    static final class Registro {
        final int fila;
        final String empresa;
        final String actividad;
        final String expediente;
        final String email;
        final String domicilio;
        final String descripcion;
        final String consultor;
        final List<Fecha> fechas = new ArrayList<>();
        final List<Resolucion> resoluciones = new ArrayList<>();

        Registro(int fila, String empresa, String actividad, String expediente, String email,
                 String domicilio, String descripcion, String consultor) {
            this.fila = fila;
            this.empresa = empresa;
            this.actividad = actividad;
            this.expediente = expediente;
            this.email = email;
            this.domicilio = domicilio;
            this.descripcion = descripcion;
            this.consultor = consultor;
        }
    }

    static final class Fecha {
        final String etiqueta;
        final LocalDate fecha;

        Fecha(String etiqueta, LocalDate fecha) {
            this.etiqueta = etiqueta;
            this.fecha = fecha;
        }
    }

    static final class Resolucion {
        final int anio;
        final String texto;

        Resolucion(int anio, String texto) {
            this.anio = anio;
            this.texto = texto;
        }
    }

    private static final class Cabeceras {
        final int fila;
        int empresa = -1;
        int actividad = -1;
        int expediente = -1;
        int email = -1;
        int domicilio = -1;
        int descripcion = -1;
        int consultor = -1;
        final List<ColumnaFecha> fechas = new ArrayList<>();
        final List<ColumnaResolucion> resoluciones = new ArrayList<>();

        Cabeceras(int fila) {
            this.fila = fila;
        }
    }

    private static final class ColumnaFecha {
        final int indice;
        final String nombre;

        ColumnaFecha(int indice, String nombre) {
            this.indice = indice;
            this.nombre = nombre;
        }
    }

    private static final class ColumnaResolucion {
        final int indice;
        final int anio;

        ColumnaResolucion(int indice, int anio) {
            this.indice = indice;
            this.anio = anio;
        }
    }
}
