package com.caa.app;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Campana de notificaciones con un punto de estado.
 * El punto es gris cuando no hay notificaciones y rojo cuando las hay.
 * La lógica que decide cuándo cambia se conecta con {@link #marcar(boolean)}.
 */
public class BotonNotificacion extends Button {

    /** Color del punto cuando no hay notificaciones. */
    public static final Color SIN_NOTIFICACIONES = Color.web("#b0b0b0");

    /** Color del punto cuando hay notificaciones. */
    public static final Color CON_NOTIFICACIONES = Color.web("#e03131");

    private final Circle punto = new Circle(3.6);

    public BotonNotificacion() {
        FontIcon campana = new FontIcon(FontAwesomeSolid.BELL);
        campana.setIconSize(14);
        campana.setIconColor(Color.web("#4a5568"));

        punto.setStroke(Color.WHITE);
        punto.setStrokeWidth(1);
        punto.setMouseTransparent(true);

        StackPane icono = new StackPane(campana, punto);
        StackPane.setAlignment(punto, Pos.BOTTOM_RIGHT);
        punto.setTranslateX(3);
        punto.setTranslateY(2);

        setGraphic(icono);
        setStyle("-fx-background-color: transparent; -fx-padding: 3 6 3 6; -fx-cursor: hand;");
        setTooltip(new Tooltip("Notificaciones"));
        setFocusTraversable(false);
        marcar(false);
    }

    /** Pone el punto rojo si hay notificaciones o gris si no hay ninguna. */
    public void marcar(boolean hayNotificaciones) {
        punto.setFill(hayNotificaciones ? CON_NOTIFICACIONES : SIN_NOTIFICACIONES);
    }

    /** Permite fijar un color puntual para el punto de estado. */
    public void setColorPunto(Color color) {
        punto.setFill(color);
    }
}
