package com.caa.app;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Botón con ícono de notificaciones.
 *
 * <ul>
 *   <li>{@link Uso#ESTADO}: campana con un punto de estado (gris/rojo). Se usa en
 *       la barra principal.</li>
 *   <li>{@link Uso#AGREGAR}: ícono de "agregar notificación" (calendario con +).
 *       Se usa en el renglón "Detalles" del evento.</li>
 * </ul>
 */
public class BotonNotificacion extends Button {

    /** Ícono para agregar una notificación desde el evento. */
    private static final FontAwesomeSolid ICONO_AGREGAR = FontAwesomeSolid.CALENDAR_PLUS;

    /** Color del punto cuando no hay notificaciones. */
    public static final Color SIN_NOTIFICACIONES = Color.web("#b0b0b0");

    /** Color del punto cuando hay notificaciones. */
    public static final Color CON_NOTIFICACIONES = Color.web("#e03131");

    public enum Uso {
        /** Campana con punto de estado. */
        ESTADO,
        /** Ícono para agregar una notificación. */
        AGREGAR
    }

    private final Circle punto;

    public BotonNotificacion() {
        this(Uso.ESTADO);
    }

    public BotonNotificacion(Uso uso) {
        FontIcon icono = new FontIcon(uso == Uso.AGREGAR ? ICONO_AGREGAR : FontAwesomeSolid.BELL);
        icono.setIconSize(14);
        icono.setIconColor(Color.web("#4a5568"));

        Node grafico;
        if (uso == Uso.ESTADO) {
            punto = new Circle(3.6);
            punto.setStroke(Color.WHITE);
            punto.setStrokeWidth(1);
            punto.setMouseTransparent(true);

            StackPane conPunto = new StackPane(icono, punto);
            StackPane.setAlignment(punto, Pos.BOTTOM_RIGHT);
            punto.setTranslateX(3);
            punto.setTranslateY(2);
            grafico = conPunto;
        } else {
            punto = null;
            grafico = icono;
        }

        setGraphic(grafico);
        setStyle("-fx-background-color: transparent; -fx-padding: 3 6 3 6; -fx-cursor: hand;");
        setTooltip(new Tooltip(uso == Uso.AGREGAR ? "Agregar notificación" : "Notificaciones"));
        setFocusTraversable(false);
        marcar(false);
    }

    /** Pone el punto rojo si hay notificaciones o gris si no hay ninguna. */
    public void marcar(boolean hayNotificaciones) {
        if (punto != null) {
            punto.setFill(hayNotificaciones ? CON_NOTIFICACIONES : SIN_NOTIFICACIONES);
        }
    }

    /** Permite fijar un color puntual para el punto de estado. */
    public void setColorPunto(Color color) {
        if (punto != null) {
            punto.setFill(color);
        }
    }
}
