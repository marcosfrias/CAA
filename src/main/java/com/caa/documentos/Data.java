package com.caa.documentos;

import com.caa.app.Almacenamiento;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Guarda el texto, las imágenes y las firmas de un expediente asociado a un evento. */
public final class Data {

    private final Path carpetaExpediente;
    private final Path carpetaImagenes;
    private final Path carpetaLibros;
    private final Path carpetaFirmas;
    private final Path carpetaAdjuntosAntiguos;
    private final Path archivoElementos;
    private final Path archivoGuardado;

    public Data(String idExpediente) {
        if (idExpediente == null || !idExpediente.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Identificador de expediente inválido.");
        }
        carpetaExpediente = Almacenamiento.carpetaDatos().resolve("expedientes").resolve(idExpediente);
        carpetaImagenes = carpetaExpediente.resolve("imagenes");
        carpetaLibros = carpetaExpediente.resolve("Libros");
        carpetaFirmas = carpetaExpediente.resolve("firmas");
        carpetaAdjuntosAntiguos = carpetaExpediente.resolve("archivos");
        archivoElementos = carpetaExpediente.resolve("elementos.tsv");
        archivoGuardado = carpetaExpediente.resolve("guardado.txt");
        try {
            Files.createDirectories(carpetaImagenes);
            Files.createDirectories(carpetaLibros);
            Files.createDirectories(carpetaFirmas);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el expediente.", e);
        }
    }

    public static boolean estaGuardado(String idExpediente) {
        if (idExpediente == null || !idExpediente.matches("[A-Za-z0-9_-]+")) return false;
        Path marcador = Almacenamiento.carpetaDatos().resolve("expedientes")
                .resolve(idExpediente).resolve("guardado.txt");
        return Files.isRegularFile(marcador);
    }

    public void marcarGuardado() {
        try {
            Almacenamiento.escribir(archivoGuardado, List.of("guardado"));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo marcar el expediente como guardado.", e);
        }
    }

    public List<Item> cargarElementos() {
        List<Item> elementos = new ArrayList<>();
        if (Files.notExists(archivoElementos)) return elementos;
        try {
            for (String linea : Files.readAllLines(archivoElementos, StandardCharsets.UTF_8)) {
                String[] campos = linea.split("\\t", -1);
                if (campos.length >= 2) {
                    List<String> imagenes = campos.length >= 3
                            ? decodificarLista(campos[2]) : Collections.emptyList();
                    String firma = campos.length >= 4 ? decodificar(campos[3]) : "";
                    double anchoFirma = 6.0;
                    if (campos.length >= 5) {
                        try {
                            anchoFirma = Math.max(3.0, Math.min(12.0,
                                    Double.parseDouble(decodificar(campos[4]))));
                        } catch (NumberFormatException ignorada) {
                            anchoFirma = 6.0;
                        }
                    }
                    elementos.add(new Item(decodificar(campos[0]), decodificar(campos[1]),
                            imagenes, firma, anchoFirma,
                            campos.length >= 6 ? decodificarTablas(campos[5]) : Collections.emptyList()));
                }
            }
            return elementos;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo leer este expediente.", e);
        }
    }

    public void guardarElementos(Collection<Item> elementos) {
        List<String> lineas = new ArrayList<>();
        for (Item elemento : elementos) {
            lineas.add(codificar(elemento.getNombre()) + "\t" + codificar(elemento.getTexto())
                    + "\t" + codificar(String.join("\n", elemento.getImagenes()))
                    + "\t" + codificar(elemento.getFirma())
                    + "\t" + codificar(Double.toString(elemento.getAnchoFirmaCm()))
                    + "\t" + codificar(String.join("\u0000", elemento.getTablas())));
        }
        try {
            Almacenamiento.escribir(archivoElementos, lineas);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar este expediente.", e);
        }
    }

    /** Copia una imagen al expediente y devuelve su ruta relativa. */
    public String copiarImagen(File imagen) {
        if (imagen == null || !imagen.isFile() || !esImagen(imagen.toPath())) {
            throw new IllegalArgumentException("Seleccioná un archivo de imagen válido.");
        }
        try {
            Path destino = destinoUnico(carpetaImagenes, imagen.getName());
            Path copiada = Files.copy(imagen.toPath(), destino, StandardCopyOption.COPY_ATTRIBUTES);
            return rutaRelativa(copiada);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo copiar la imagen al expediente.", e);
        }
    }

    /** Copia un libro de Excel al expediente y devuelve su ruta relativa. */
    public String copiarLibroExcel(File libro) {
        if (libro == null || !libro.isFile() || !esLibroExcel(libro.toPath())) {
            throw new IllegalArgumentException("Seleccioná un libro de Excel válido.");
        }
        try {
            Path destino = destinoUnico(carpetaLibros, libro.getName());
            return rutaRelativa(Files.copy(libro.toPath(), destino, StandardCopyOption.COPY_ATTRIBUTES));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo copiar el libro de Excel al expediente.", e);
        }
    }

    public String copiarFirma(File firma) {
        if (firma == null || !firma.isFile() || !esImagen(firma.toPath())) {
            throw new IllegalArgumentException("Seleccioná una firma en formato PNG o JPG.");
        }
        try {
            Path destino = destinoUnico(carpetaFirmas, firma.getName());
            Path copiada = Files.copy(firma.toPath(), destino, StandardCopyOption.COPY_ATTRIBUTES);
            return rutaRelativa(copiada);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la firma.", e);
        }
    }

    public String guardarFirmaDibujada(BufferedImage firma) {
        if (firma == null) throw new IllegalArgumentException("Dibujá una firma antes de guardarla.");
        Path destino = destinoUnico(carpetaFirmas, "firma-dibujada.png");
        try {
            if (!ImageIO.write(firma, "png", destino.toFile())) {
                throw new IOException("No se pudo escribir la imagen PNG.");
            }
            return rutaRelativa(destino);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la firma dibujada.", e);
        }
    }

    /** Lista imágenes nuevas y las que se habían cargado en versiones anteriores. */
    public List<String> listarImagenes() {
        List<String> imagenes = new ArrayList<>();
        agregarImagenes(carpetaImagenes, imagenes);
        agregarImagenes(carpetaAdjuntosAntiguos, imagenes);
        return imagenes;
    }

    /** Devuelve el directorio y las imágenes para mostrarlas en el árbol del expediente. */
    public List<String> listarAdjuntos() {
        List<String> adjuntos = new ArrayList<>();
        List<String> imagenes = listarImagenes();
        if (!imagenes.isEmpty()) adjuntos.add(rutaRelativa(carpetaImagenes));
        adjuntos.addAll(imagenes);
        List<String> libros = listarLibros();
        if (!libros.isEmpty()) adjuntos.add(rutaRelativa(carpetaLibros));
        adjuntos.addAll(libros);
        List<String> firmas = new ArrayList<>();
        agregarImagenes(carpetaFirmas, firmas);
        if (!firmas.isEmpty()) adjuntos.add(rutaRelativa(carpetaFirmas));
        adjuntos.addAll(firmas);
        return adjuntos;
    }

    public Path resolverArchivo(String rutaRelativa) {
        Path resuelta = carpetaExpediente.resolve(rutaRelativa).normalize();
        if (!resuelta.startsWith(carpetaExpediente)) {
            throw new IllegalArgumentException("La ruta no pertenece a este expediente.");
        }
        return resuelta;
    }

    public void eliminarImagen(String rutaRelativa) throws IOException {
        Path imagen = resolverArchivo(rutaRelativa);
        if (!esRutaImagen(imagen) || !esImagen(imagen) || !Files.isRegularFile(imagen)) {
            throw new IOException("La imagen seleccionada no pertenece a este expediente.");
        }
        Files.delete(imagen);
    }

    /** Elimina una imagen, una firma o vacía su carpeta. */
    public void eliminarAdjunto(String rutaRelativa) throws IOException {
        Path destino = resolverArchivo(rutaRelativa);
        if (destino.equals(carpetaImagenes)) {
            vaciarCarpeta(carpetaImagenes);
            return;
        }
        if (destino.equals(carpetaFirmas)) {
            vaciarCarpeta(carpetaFirmas);
            return;
        }
        if (destino.equals(carpetaLibros)) {
            vaciarCarpeta(carpetaLibros);
            return;
        }
        boolean imagen = esRutaImagen(destino) && esImagen(destino);
        boolean firma = esRutaFirma(destino) && esImagen(destino);
        boolean libro = esRutaLibro(destino) && esLibroExcel(destino);
        if ((!imagen && !firma && !libro) || Files.notExists(destino)) {
            throw new IOException("Solo se pueden eliminar imágenes, libros de Excel o firmas del expediente.");
        }
        Files.delete(destino);
    }

    /** Vacía todos los elementos y elimina las imágenes y firmas del expediente. */
    public void eliminarTodo() throws IOException {
        vaciarCarpeta(carpetaImagenes);
        vaciarCarpeta(carpetaLibros);
        vaciarCarpeta(carpetaFirmas);
        vaciarCarpeta(carpetaAdjuntosAntiguos);
        Files.deleteIfExists(archivoGuardado);
        guardarElementos(Collections.emptyList());
    }

    private void vaciarCarpeta(Path carpeta) throws IOException {
        if (Files.notExists(carpeta)) return;
        try (Stream<Path> rutas = Files.list(carpeta)) {
            for (Path ruta : rutas.toArray(Path[]::new)) eliminarRuta(ruta);
        }
    }

    private void eliminarRuta(Path ruta) throws IOException {
        if (Files.isDirectory(ruta) && !Files.isSymbolicLink(ruta)) {
            try (Stream<Path> rutas = Files.list(ruta)) {
                for (Path hija : rutas.toArray(Path[]::new)) eliminarRuta(hija);
            }
        }
        Files.deleteIfExists(ruta);
    }

    private void agregarImagenes(Path carpeta, List<String> imagenes) {
        if (Files.notExists(carpeta)) return;
        try (Stream<Path> rutas = Files.walk(carpeta)) {
            rutas.filter(Files::isRegularFile).filter(Data::esImagen)
                    .map(this::rutaRelativa).forEach(imagenes::add);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron listar las imágenes.", e);
        }
    }

    private List<String> listarLibros() {
        List<String> libros = new ArrayList<>();
        if (Files.notExists(carpetaLibros)) return libros;
        try (Stream<Path> rutas = Files.walk(carpetaLibros)) {
            rutas.filter(Files::isRegularFile).filter(Data::esLibroExcel)
                    .map(this::rutaRelativa).forEach(libros::add);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron listar los libros de Excel.", e);
        }
        return libros;
    }

    private boolean esRutaImagen(Path ruta) {
        return ruta.startsWith(carpetaImagenes) || ruta.startsWith(carpetaAdjuntosAntiguos);
    }

    private boolean esRutaFirma(Path ruta) {
        return ruta.startsWith(carpetaFirmas);
    }

    private boolean esRutaLibro(Path ruta) {
        return ruta.startsWith(carpetaLibros);
    }

    private static boolean esLibroExcel(Path ruta) {
        String nombre = ruta.getFileName().toString().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".xlsx") || nombre.endsWith(".xlsm") || nombre.endsWith(".xls");
    }

    private static Path destinoUnico(Path carpeta, String nombre) {
        Path destino = carpeta.resolve(nombre);
        if (!Files.exists(destino)) return destino;
        int punto = nombre.lastIndexOf('.');
        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
        String extension = punto > 0 ? nombre.substring(punto) : "";
        int indice = 2;
        do {
            destino = carpeta.resolve(base + "-" + indice++ + extension);
        } while (Files.exists(destino));
        return destino;
    }

    private String rutaRelativa(Path ruta) {
        return carpetaExpediente.relativize(ruta).toString().replace('\\', '/');
    }

    private static boolean esImagen(Path ruta) {
        String nombre = ruta.getFileName().toString().toLowerCase(Locale.ROOT);
        return nombre.matches(".*\\.(png|jpe?g|gif|bmp)$");
    }

    public static final class Item {
        private final String nombre;
        private final String texto;
        private final List<String> imagenes;
        private final String firma;
        private final double anchoFirmaCm;
        private final List<String> tablas;

        public Item(String nombre, String texto) {
            this(nombre, texto, Collections.emptyList());
        }

        public Item(String nombre, String texto, List<String> imagenes) {
            this(nombre, texto, imagenes, "", 6.0);
        }

        public Item(String nombre, String texto, List<String> imagenes, String firma, double anchoFirmaCm) {
            this(nombre, texto, imagenes, firma, anchoFirmaCm, Collections.emptyList());
        }

        public Item(String nombre, String texto, List<String> imagenes, String firma, double anchoFirmaCm,
                    List<String> tablas) {
            this.nombre = nombre;
            this.texto = texto;
            this.imagenes = new ArrayList<>(imagenes);
            this.firma = firma == null ? "" : firma;
            this.anchoFirmaCm = anchoFirmaCm;
            this.tablas = new ArrayList<>(tablas);
        }

        public String getNombre() { return nombre; }
        public String getTexto() { return texto; }
        public List<String> getImagenes() { return Collections.unmodifiableList(imagenes); }
        public String getFirma() { return firma; }
        public double getAnchoFirmaCm() { return anchoFirmaCm; }
        public List<String> getTablas() { return Collections.unmodifiableList(tablas); }
    }

    private static String codificar(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodificar(String texto) {
        return new String(Base64.getDecoder().decode(texto), StandardCharsets.UTF_8);
    }

    private static List<String> decodificarLista(String texto) {
        String lista = decodificar(texto);
        if (lista.isEmpty()) return Collections.emptyList();
        return List.of(lista.split("\\n", -1));
    }

    private static List<String> decodificarTablas(String texto) {
        String datos = decodificar(texto);
        if (datos.isEmpty()) return Collections.emptyList();
        return List.of(datos.split(String.valueOf((char) 0), -1));
    }
}
