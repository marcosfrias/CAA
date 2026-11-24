package com.eia.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

public final class Almacenamiento {

    private static final Path CARPETA = resolverCarpeta();

    private Almacenamiento() {
    }

    public static Path carpetaDatos() {
        return CARPETA;
    }

    public static void inicializar() throws IOException {
        Files.createDirectories(CARPETA);
        migrarDatosAnteriores();
    }

    public static void escribir(Path archivo, List<String> lineas) throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        Files.createDirectories(carpeta);
        Path temporal = Files.createTempFile(carpeta, "." + archivo.getFileName() + ".", ".tmp");
        try {
            Files.write(temporal, lineas, StandardCharsets.UTF_8);
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static Path resolverCarpeta() {
        String rutaConfigurada = System.getProperty("eia.datos");
        if (rutaConfigurada != null && !rutaConfigurada.isBlank()) {
            return Path.of(rutaConfigurada).toAbsolutePath().normalize();
        }

        Path inicio = Path.of(System.getProperty("user.home"));
        String sistema = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (sistema.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = localAppData == null || localAppData.isBlank()
                    ? inicio.resolve("AppData").resolve("Local") : Path.of(localAppData);
            return base.resolve("EIA").resolve("datos");
        }
        if (sistema.contains("mac")) {
            return inicio.resolve("Library").resolve("Application Support").resolve("EIA").resolve("datos");
        }
        return inicio.resolve(".local").resolve("share").resolve("EIA").resolve("datos");
    }

    private static void migrarDatosAnteriores() throws IOException {
        Path anterior = Path.of("datos").toAbsolutePath().normalize();
        if (anterior.equals(CARPETA) || anterior.startsWith(CARPETA) || CARPETA.startsWith(anterior)
                || !Files.isDirectory(anterior) || !carpetaVacia(CARPETA)) {
            return;
        }

        try (var rutas = Files.walk(anterior)) {
            for (Path ruta : rutas.toArray(Path[]::new)) {
                Path destino = CARPETA.resolve(anterior.relativize(ruta));
                if (Files.isDirectory(ruta)) {
                    Files.createDirectories(destino);
                } else if (Files.notExists(destino)) {
                    Files.createDirectories(destino.getParent());
                    Files.copy(ruta, destino);
                }
            }
        }
    }

    private static boolean carpetaVacia(Path carpeta) throws IOException {
        try (var rutas = Files.list(carpeta)) {
            return rutas.findAny().isEmpty();
        }
    }
}
