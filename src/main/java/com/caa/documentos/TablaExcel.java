package com.caa.documentos;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee hojas de Excel y detecta bloques de tabla insertados en el texto del expediente. */
final class TablaExcel {
    private static final int MAX_FILAS_EXCEL = 10_000;
    private static final int MAX_COLUMNAS_EXCEL = 40;
    private static final int FILAS_VISTA_PREVIA = 30;
    private static final int FILAS_POR_PAGINA_PDF = 14;
    private static final int COLUMNAS_POR_PAGINA_PDF = 4;
    private static final Pattern MARCADOR = Pattern.compile("\\[Tabla (\\d+): .*\\]");
    private TablaExcel() { }

    static List<String> hojas(Path archivo) throws IOException {
        try (Workbook libro = WorkbookFactory.create(archivo.toFile())) {
            List<String> resultado = new ArrayList<>();
            for (int i = 0; i < libro.getNumberOfSheets(); i++) resultado.add(libro.getSheetName(i));
            return resultado;
        }
    }

    static List<List<String>> leer(Path archivo, String nombreHoja) throws IOException {
        try (Workbook libro = WorkbookFactory.create(archivo.toFile())) {
            Sheet hoja = libro.getSheet(nombreHoja);
            if (hoja == null) throw new IOException("No se encontró la hoja seleccionada.");
            int ultimaColumna = 0;
            for (Row fila : hoja) ultimaColumna = Math.max(ultimaColumna, fila.getLastCellNum());
            if (ultimaColumna <= 0 || hoja.getLastRowNum() < 0) throw new IOException("La hoja está vacía.");
            if (ultimaColumna > MAX_COLUMNAS_EXCEL) {
                throw new IOException("La hoja supera el máximo de " + MAX_COLUMNAS_EXCEL + " columnas.");
            }
            DataFormatter formato = new DataFormatter();
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();
            List<List<String>> filas = new ArrayList<>();
            for (Row fila : hoja) {
                List<String> celdas = new ArrayList<>();
                for (int c = 0; c < ultimaColumna; c++) {
                    Cell celda = fila == null ? null : fila.getCell(c);
                    celdas.add(celda == null ? "" : formato.formatCellValue(celda, evaluador));
                }
                while (!celdas.isEmpty() && celdas.get(celdas.size() - 1).isEmpty()) celdas.remove(celdas.size() - 1);
                if (celdas.stream().anyMatch(valor -> !valor.isBlank())) filas.add(celdas);
                if (filas.size() > MAX_FILAS_EXCEL) {
                    throw new IOException("La hoja supera el máximo de " + MAX_FILAS_EXCEL + " filas con datos.");
                }
            }
            return filas;
        }
    }

    static String aTexto(List<List<String>> filas) {
        StringBuilder texto = new StringBuilder();
        int columnas = filas.stream().mapToInt(List::size).max().orElse(0);
        if (columnas == 0) return "";
        for (int r = 0; r < filas.size(); r++) {
            texto.append('|');
            for (int c = 0; c < columnas; c++) {
                String valor = c < filas.get(r).size() ? filas.get(r).get(c) : "";
                texto.append(' ').append(valor.replace("|", "\\|").replace("\n", " ")).append(" |");
            }
            texto.append('\n');
            if (r == 0) {
                texto.append('|');
                for (int c = 0; c < columnas; c++) texto.append(" --- |");
                texto.append('\n');
            }
        }
        return texto.toString().stripTrailing();
    }

    static String serializar(List<List<String>> filas) {
        StringBuilder texto = new StringBuilder();
        for (List<String> fila : filas) {
            if (texto.length() > 0) texto.append('\n');
            for (int c = 0; c < fila.size(); c++) {
                if (c > 0) texto.append('\t');
                texto.append(fila.get(c).replace('\t', ' ').replace('\r', ' ').replace('\n', ' '));
            }
        }
        return texto.toString();
    }

    static List<List<String>> deserializar(String tabla) {
        List<List<String>> filas = new ArrayList<>();
        if (tabla == null || tabla.isEmpty()) return filas;
        for (String fila : tabla.split("\\R", -1)) filas.add(List.of(fila.split("\\t", -1)));
        return filas;
    }

    static String marcador(int indice, String nombre) {
        return "[Tabla " + indice + ": " + nombre.replace("]", "") + "]";
    }

    static int indiceMarcador(String texto) {
        Matcher matcher = MARCADOR.matcher(texto == null ? "" : texto.trim());
        if (!matcher.matches()) return -1;
        try {
            return Integer.parseInt(matcher.group(1)) - 1;
        } catch (NumberFormatException ignorada) {
            return -1;
        }
    }

    static List<List<String>> tablaEn(List<String> lineas, int inicio) {
        if (inicio >= lineas.size() || !esFila(lineas.get(inicio))) return null;
        List<List<String>> filas = new ArrayList<>();
        boolean separador = false;
        int i = inicio;
        while (i < lineas.size() && esFila(lineas.get(i))) {
            String linea = lineas.get(i).trim();
            if (linea.matches("\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?")) {
                separador = true;
            } else {
                filas.add(celdas(linea));
            }
            i++;
        }
        return separador && !filas.isEmpty() ? filas : null;
    }

    static boolean esFila(String linea) {
        return linea != null && linea.trim().startsWith("|") && linea.trim().endsWith("|");
    }

    static BufferedImage imagen(List<List<String>> filas) {
        int columnas = filas.stream().mapToInt(List::size).max().orElse(1);
        int[] anchos = new int[columnas];
        for (int c = 0; c < columnas; c++) {
            int max = 8;
            for (List<String> fila : filas) {
                if (c < fila.size()) max = Math.max(max, fila.get(c).length());
            }
            anchos[c] = Math.min(280, Math.max(80, max * 8 + 18));
        }
        int ancho = 1;
        for (int a : anchos) ancho += a;
        int alto = 1 + filas.size() * 30;
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            for (int r = 0; r < filas.size(); r++) {
                int x = 1;
                for (int c = 0; c < columnas; c++) {
                    int w = anchos[c];
                    if (r == 0) {
                        g.setColor(new Color(225, 235, 245));
                        g.fillRect(x, 1 + r * 30, w, 30);
                    }
                    g.setColor(new Color(165, 175, 185));
                    g.drawRect(x, 1 + r * 30, w, 30);
                    g.setColor(Color.BLACK);
                    String valor = c < filas.get(r).size() ? filas.get(r).get(c) : "";
                    g.drawString(valor.length() > 32 ? valor.substring(0, 29) + "..." : valor,
                            x + 6, 21 + r * 30);
                    x += w;
                }
            }
        } finally {
            g.dispose();
        }
        return imagen;
    }

    static BufferedImage imagenPrevia(List<List<String>> filas) {
        return imagen(filas.subList(0, Math.min(filas.size(), FILAS_VISTA_PREVIA)));
    }

    static int filasVistaPrevia() {
        return FILAS_VISTA_PREVIA;
    }

    static int filasPorPaginaPdf() {
        return FILAS_POR_PAGINA_PDF;
    }

    static int columnasPorPaginaPdf() {
        return COLUMNAS_POR_PAGINA_PDF;
    }

    static int columnas(List<List<String>> filas) {
        return filas.stream().mapToInt(List::size).max().orElse(1);
    }

    static BufferedImage imagenPdf(List<List<String>> filas, int columnaInicio, int columnaFin) {
        int columnas = Math.max(1, columnaFin - columnaInicio);
        int anchoCelda = 192;
        int[] altosFilas = new int[filas.size()];
        List<List<List<String>>> textos = new ArrayList<>();
        BufferedImage medicion = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D gm = medicion.createGraphics();
        gm.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (List<String> fila : filas) {
            List<List<String>> textosFila = new ArrayList<>();
            int altoFila = 28;
            for (int c = 0; c < columnas; c++) {
                int indiceColumna = columnaInicio + c;
                String valor = indiceColumna < fila.size() ? fila.get(indiceColumna) : "";
                List<String> lineas = envolverCelda(gm, valor, anchoCelda - 12);
                textosFila.add(lineas);
                altoFila = Math.max(altoFila, lineas.size() * 13 + 12);
            }
            textos.add(textosFila);
            altosFilas[textos.size() - 1] = altoFila;
        }
        gm.dispose();
        int altoTotal = 1;
        for (int alto : altosFilas) altoTotal += alto;
        BufferedImage imagen = new BufferedImage(columnas * anchoCelda + 1, altoTotal, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            int y = 0;
            for (int r = 0; r < filas.size(); r++) {
                for (int c = 0; c < columnas; c++) {
                    int x = c * anchoCelda;
                    if (r == 0) {
                        g.setColor(new Color(225, 235, 245));
                        g.fillRect(x, y, anchoCelda, altosFilas[r]);
                    }
                    g.setColor(new Color(165, 175, 185));
                    g.drawRect(x, y, anchoCelda, altosFilas[r]);
                    g.setColor(Color.BLACK);
                    List<String> lineas = textos.get(r).get(c);
                    for (int l = 0; l < lineas.size(); l++) {
                        g.drawString(lineas.get(l), x + 6, y + 15 + l * 13);
                    }
                }
                y += altosFilas[r];
            }
        } finally {
            g.dispose();
        }
        return imagen;
    }

    private static List<String> envolverCelda(Graphics2D g, String texto, int ancho) {
        List<String> resultado = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : (texto == null ? "" : texto).split("\\s+")) {
            String candidato = actual.length() == 0 ? palabra : actual + " " + palabra;
            if (g.getFontMetrics().stringWidth(candidato) <= ancho) {
                if (actual.length() > 0) actual.append(' ');
                actual.append(palabra);
            } else {
                if (actual.length() > 0) resultado.add(actual.toString());
                actual.setLength(0);
                StringBuilder parte = new StringBuilder();
                for (int i = 0; i < palabra.length(); i++) {
                    parte.append(palabra.charAt(i));
                    if (g.getFontMetrics().stringWidth(parte.toString()) > ancho) {
                        char ultimo = parte.charAt(parte.length() - 1);
                        parte.setLength(parte.length() - 1);
                        if (parte.length() > 0) resultado.add(parte.toString());
                        parte.setLength(0);
                        parte.append(ultimo);
                    }
                }
                actual.append(parte);
            }
        }
        if (actual.length() > 0) resultado.add(actual.toString());
        if (resultado.isEmpty()) resultado.add("");
        return resultado;
    }

    private static List<String> celdas(String linea) {
        String[] valores = linea.substring(1, linea.length() - 1).split("\\|", -1);
        List<String> resultado = new ArrayList<>();
        for (String valor : valores) resultado.add(valor.trim().replace("\\|", "|"));
        return resultado;
    }
}
