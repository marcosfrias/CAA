package com.caa.documentos;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/** Permite definir cómo se dimensionan y ordenan las imágenes al exportar. */
final class DialogoDisposicionImagenes {

    private DialogoDisposicionImagenes() { }

    static void abrir(Window owner, DisposicionImagenes actual,
                      java.util.function.Consumer<DisposicionImagenes> alAceptar) {
        ComboBox<Integer> tamano = new ComboBox<>();
        tamano.getItems().setAll(4, 6, 8);
        tamano.setValue(actual.getTamanoCentimetros());
        tamano.setPrefWidth(150);

        ComboBox<Integer> columnas = new ComboBox<>();
        columnas.getItems().setAll(1, 2, 3);
        columnas.setValue(actual.getColumnas());
        columnas.setPrefWidth(150);

        ComboBox<DisposicionImagenes.Orden> orden = new ComboBox<>();
        orden.getItems().setAll(DisposicionImagenes.Orden.values());
        orden.setValue(actual.getOrden());
        orden.setPrefWidth(180);

        Label nota = new Label("El tamaño define el ancho máximo; se conserva la proporción original.");
        nota.setWrapText(true);
        GridPane campos = new GridPane();
        campos.setHgap(12);
        campos.setVgap(12);
        campos.setPadding(new Insets(12));
        campos.addRow(0, new Label("Tamaño máximo:"), tamano, new Label("cm"));
        campos.addRow(1, new Label("Imágenes por fila:"), columnas);
        campos.addRow(2, new Label("Orden:"), orden);
        campos.add(nota, 0, 3, 3, 1);

        ButtonType aceptar = new ButtonType("Aplicar", ButtonBar.ButtonData.OK_DONE);
        Dialog<DisposicionImagenes> dialogo = new Dialog<>();
        dialogo.setTitle("Disposición de imágenes");
        dialogo.setHeaderText("Configurar imágenes de exportación");
        dialogo.initOwner(owner);
        dialogo.getDialogPane().setContent(campos);
        dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);
        dialogo.setResultConverter(boton -> boton == aceptar
                ? new DisposicionImagenes(tamano.getValue(), columnas.getValue(), orden.getValue())
                : null);
        dialogo.showAndWait().ifPresent(alAceptar);
    }
}
