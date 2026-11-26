package com.caa.app;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/** Botón de usuario con un indicador gris/rojo preparado para conectar su lógica. */
public final class BotonUsuario extends Button {

    private static final Color SIN_AVISO = Color.web("#b0b0b0");
    private static final Color CON_AVISO = Color.web("#e03131");

    private final Circle punto = new Circle(3.6);

    public BotonUsuario() {
        FontIcon usuario = new FontIcon(FontAwesomeSolid.USER);
        usuario.setIconSize(14);
        usuario.setIconColor(Color.web("#4a5568"));

        punto.setFill(SIN_AVISO);
        punto.setStroke(Color.WHITE);
        punto.setStrokeWidth(1);
        punto.setMouseTransparent(true);

        StackPane icono = new StackPane(usuario, punto);
        StackPane.setAlignment(punto, Pos.BOTTOM_RIGHT);
        punto.setTranslateX(3);
        punto.setTranslateY(2);

        setGraphic(icono);
        setStyle("-fx-background-color: transparent; -fx-padding: 3 6 3 6; -fx-cursor: hand;");
        setTooltip(new Tooltip("Usuario"));
        setFocusTraversable(false);
    }

    /** Cambia el indicador a rojo o gris cuando se conecte la lógica de usuario. */
    public void marcar(boolean hayAviso) {
        punto.setFill(hayAviso ? CON_AVISO : SIN_AVISO);
    }
}
