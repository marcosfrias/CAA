package com.caa.app;

import javafx.application.Application;

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
        Application.launch(CalendarApp.class, args);
    }
}
