package com.caa.app;

import javafx.application.Application;
import java.util.Locale;

/**
 * Punto de entrada usado por el ejecutable empaquetado.
 *
 * <p>JavaFX 19 comprueba que la clase principal no extienda {@link Application}
 * cuando JavaFX está en el classpath (como ocurre en la imagen generada con
 * jpackage). Este puente evita ese chequeo y delega en la aplicación real.</p>
 */
public final class Lanzador {

    private Lanzador() {
    }

    public static void main(String[] args) {
        Locale argentina = Locale.forLanguageTag("es-AR");
        Locale.setDefault(argentina);
        Locale.setDefault(Locale.Category.DISPLAY, argentina);
        Locale.setDefault(Locale.Category.FORMAT, argentina);
        Application.launch(CalendarApp.class, args);
    }
}
