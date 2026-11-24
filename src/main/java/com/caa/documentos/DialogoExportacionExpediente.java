package com.caa.documentos;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Vista previa y selección de formatos para exportar el contenido de un expediente. */
final class DialogoExportacionExpediente {

    private DialogoExportacionExpediente() {
    }

    static void abrir(String nombreExpediente, String contenido, List<File> imagenes,
                      DisposicionImagenes disposicion, File firma, double anchoFirmaCm, Window owner) {
        List<File> imagenesOrdenadas = disposicion.ordenar(imagenes);
        CheckBox word = new CheckBox("Word (.docx)");
        CheckBox pdf = new CheckBox("PDF (.pdf)");
        CheckBox excel = new CheckBox("Excel (.xlsx)");

        TextArea vistaPrevia = new TextArea(contenido);
        vistaPrevia.setEditable(false);
        vistaPrevia.setWrapText(true);
        vistaPrevia.setFocusTraversable(false);
        vistaPrevia.setStyle("-fx-control-inner-background: white; -fx-background-color: white; "
                + "-fx-border-color: transparent; -fx-padding: 0; -fx-font-family: Arial; -fx-font-size: 12px;");
        vistaPrevia.setPrefSize(475, 620);

        VBox cuerpoHoja = new VBox(16, vistaPrevia);
        GridPane cuadrillaImagenes = new GridPane();
        cuadrillaImagenes.setHgap(12);
        cuadrillaImagenes.setVgap(12);
        cuadrillaImagenes.setAlignment(Pos.TOP_CENTER);
        double ladoVistaPrevia = disposicion.getTamanoCentimetros() * 37.8;
        for (int indice = 0; indice < imagenesOrdenadas.size(); indice++) {
            File archivoImagen = imagenesOrdenadas.get(indice);
            Image fuente = new Image(archivoImagen.toURI().toString(),
                    ladoVistaPrevia * 2, ladoVistaPrevia * 2, true, true, true);
            double ladoFuente = Math.min(fuente.getWidth(), fuente.getHeight());
            ImageView imagen = new ImageView(fuente);
            imagen.setViewport(new Rectangle2D((fuente.getWidth() - ladoFuente) / 2,
                    (fuente.getHeight() - ladoFuente) / 2, ladoFuente, ladoFuente));
            imagen.setPreserveRatio(false);
            imagen.setFitWidth(ladoVistaPrevia);
            imagen.setFitHeight(ladoVistaPrevia);
            StackPane marcoImagen = new StackPane(imagen);
            marcoImagen.setPrefSize(ladoVistaPrevia, ladoVistaPrevia);
            marcoImagen.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #d0d0d0;");
            Label etiquetaImagen = new Label("Imagen " + (indice + 1));
            VBox tarjetaImagen = new VBox(5, marcoImagen, etiquetaImagen);
            tarjetaImagen.setAlignment(Pos.CENTER);
            cuadrillaImagenes.add(tarjetaImagen, indice % disposicion.getColumnas(),
                    indice / disposicion.getColumnas());
        }
        cuerpoHoja.getChildren().add(cuadrillaImagenes);
        if (firma != null && firma.isFile()) {
            ImageView vistaFirma = new ImageView(new Image(firma.toURI().toString(), false));
            vistaFirma.setPreserveRatio(true);
            vistaFirma.setSmooth(true);
            vistaFirma.setFitWidth(anchoFirmaCm * 37.8);
            VBox pieFirma = new VBox(6, new Label("Firma"), vistaFirma);
            pieFirma.setAlignment(Pos.BOTTOM_RIGHT);
            cuerpoHoja.getChildren().add(pieFirma);
        }
        VBox hoja = new VBox(cuerpoHoja);
        hoja.setAlignment(Pos.TOP_LEFT);
        hoja.setPadding(new Insets(44));
        hoja.setPrefWidth(565);
        hoja.setMinHeight(710);
        hoja.setStyle("-fx-background-color: white; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 14, 0, 0, 3);");

        StackPane lienzo = new StackPane(hoja);
        lienzo.setPadding(new Insets(24));
        lienzo.setStyle("-fx-background-color: #e6e8eb;");
        ScrollPane desplazamiento = new ScrollPane(lienzo);
        desplazamiento.setFitToWidth(true);
        desplazamiento.setFitToHeight(true);
        desplazamiento.setPannable(true);
        desplazamiento.setStyle("-fx-background: #e6e8eb; -fx-background-color: #e6e8eb;");

        Label tituloFormatos = new Label("Exportar como");
        tituloFormatos.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label ayudaFormatos = new Label("Seleccioná uno o más formatos. Las imágenes usan la disposición configurada en Tabla y la firma se incluye al pie.");
        ayudaFormatos.setWrapText(true);
        VBox opciones = new VBox(14, tituloFormatos, ayudaFormatos, word, pdf, excel);
        opciones.setPadding(new Insets(22, 18, 18, 18));
        opciones.setPrefWidth(220);
        opciones.setStyle("-fx-background-color: #f7f7f7; -fx-border-color: #d5d5d5; -fx-border-width: 0 0 0 1;");

        BorderPane cuerpo = new BorderPane();
        cuerpo.setCenter(desplazamiento);
        cuerpo.setRight(opciones);
        cuerpo.setPrefSize(900, 690);

        ButtonType exportar = new ButtonType("Exportar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = ButtonType.CANCEL;
        Dialog<List<String>> dialogo = new Dialog<>();
        dialogo.setTitle("Exportar expediente");
        dialogo.setHeaderText(null);
        dialogo.initOwner(owner);
        dialogo.getDialogPane().setContent(cuerpo);
        dialogo.getDialogPane().setPrefSize(900, 690);
        dialogo.getDialogPane().getButtonTypes().addAll(exportar, cancelar);
        dialogo.setResultConverter(boton -> {
            if (boton != exportar) return null;
            List<String> formatos = new ArrayList<>();
            if (word.isSelected()) formatos.add("word");
            if (pdf.isSelected()) formatos.add("pdf");
            if (excel.isSelected()) formatos.add("excel");
            return formatos;
        });

        dialogo.showAndWait().ifPresent(formatos -> {
            if (formatos.isEmpty()) {
                mostrarError(owner, "Seleccioná al menos un formato para exportar.");
                return;
            }
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle("Elegir carpeta de destino");
            File carpeta = selector.showDialog(owner);
            if (carpeta == null) return;
            try {
                ExportadorExpediente.exportar(
                        nombreExpediente, contenido, carpeta.toPath(), formatos, imagenesOrdenadas,
                        disposicion, firma, anchoFirmaCm);
                Alert confirmacion = new Alert(Alert.AlertType.INFORMATION);
                confirmacion.setTitle("Exportación completa");
                confirmacion.setHeaderText(null);
                confirmacion.setContentText("Los archivos se guardaron en:\n" + carpeta.getAbsolutePath());
                confirmacion.initOwner(owner);
                confirmacion.showAndWait();
            } catch (IOException e) {
                mostrarError(owner, "No se pudo exportar el expediente: " + e.getMessage());
            }
        });
    }

    private static void mostrarError(Window owner, String mensaje) {
        Alert error = new Alert(Alert.AlertType.ERROR);
        error.setTitle("Error al exportar");
        error.setHeaderText(null);
        error.setContentText(mensaje);
        error.initOwner(owner);
        error.showAndWait();
    }
}
