package com.caa.app;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Diálogo para adjuntar un documento Word a un evento y fijar el plazo de la
 * notificación. El plazo se puede indicar en días (por defecto 30) o con una
 * fecha límite concreta.
 */
public final class DialogoNotificacion extends Dialog<DialogoNotificacion.Resultado> {

    /** Datos elegidos por el usuario. */
    public static final class Resultado {

        private final Path archivo;
        private final LocalDate fechaLimite;
        private final String descripcion;
        private final boolean eliminarArchivo;

        public Resultado(Path archivo, LocalDate fechaLimite, String descripcion,
                         boolean eliminarArchivo) {
            this.archivo = archivo;
            this.fechaLimite = fechaLimite;
            this.descripcion = descripcion;
            this.eliminarArchivo = eliminarArchivo;
        }

        /** Archivo nuevo elegido, o {@code null} si no se reemplaza el actual. */
        public Path getArchivo() {
            return archivo;
        }

        public boolean isEliminarArchivo() {
            return eliminarArchivo;
        }

        public LocalDate getFechaLimite() {
            return fechaLimite;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    private final ObjectProperty<Path> archivoSeleccionado = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> archivoParaDescargar = new SimpleObjectProperty<>();
    private final Label nombreArchivo = new Label("Ningún archivo seleccionado");
    private final Button subir = new Button("Subir");
    private final Button descargar = new Button("Descargar");
    private final Button eliminar = new Button("Eliminar");
    private final Spinner<Integer> dias = new Spinner<>(0, 3650, 30);
    private final DatePicker fechaLimite = new DatePicker(LocalDate.now().plusDays(30));
    private final TextField detalle = new TextField();
    private final boolean editando;

    private boolean sincronizando = false;
    private boolean archivoEliminado = false;

    public DialogoNotificacion(Window owner, Notificacion existente, Path archivoExistente) {
        this.editando = existente != null;

        setTitle(editando ? "Editar notificación" : "Agregar notificación");
        setHeaderText(null);
        if (owner != null) initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (editando) {
            if (existente.getFechaLimite() != null) {
                fechaLimite.setValue(existente.getFechaLimite());
                long transcurridos = ChronoUnit.DAYS.between(LocalDate.now(), existente.getFechaLimite());
                dias.getValueFactory().setValue((int) Math.max(0, Math.min(3650, transcurridos)));
            }
            if (existente.getDescripcion() != null) detalle.setText(existente.getDescripcion());
            if (existente.getArchivo() != null && !existente.getArchivo().isBlank()
                    && archivoExistente != null) {
                archivoParaDescargar.set(archivoExistente);
                nombreArchivo.setText(archivoExistente.getFileName().toString());
            }
        }

        subir.setOnAction(evento -> elegirArchivo());
        descargar.setOnAction(evento -> descargarArchivo());
        eliminar.setOnAction(evento -> eliminarArchivo());
        nombreArchivo.setMaxWidth(280);
        nombreArchivo.setWrapText(true);

        HBox filaArchivo = new HBox(8, subir, nombreArchivo, descargar, eliminar);
        filaArchivo.setAlignment(Pos.CENTER_LEFT);
        actualizarEstadoArchivo();

        dias.setEditable(true);
        dias.setPrefWidth(90);

        detalle.setPromptText("Detalle (opcional)");
        detalle.setPrefColumnCount(28);

        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(10);
        grilla.setPadding(new Insets(12));
        grilla.add(new Label("Documento:"), 0, 0);
        grilla.add(filaArchivo, 1, 0);
        grilla.add(new Label("Plazo (días):"), 0, 1);
        grilla.add(dias, 1, 1);
        grilla.add(new Label("Fecha límite:"), 0, 2);
        grilla.add(fechaLimite, 1, 2);
        grilla.add(new Label("Detalle:"), 0, 3);
        grilla.add(detalle, 1, 3);

        Label ayuda = new Label("La campana se pondrá roja cuando llegue la fecha límite.");
        ayuda.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        grilla.add(ayuda, 0, 4, 2, 1);

        getDialogPane().setContent(grilla);

        dias.valueProperty().addListener((observable, anterior, actual) -> {
            if (sincronizando || actual == null) return;
            sincronizando = true;
            fechaLimite.setValue(LocalDate.now().plusDays(actual));
            sincronizando = false;
        });

        fechaLimite.valueProperty().addListener((observable, anterior, actual) -> {
            if (sincronizando || actual == null) return;
            sincronizando = true;
            long transcurridos = ChronoUnit.DAYS.between(LocalDate.now(), actual);
            dias.getValueFactory().setValue((int) Math.max(0, Math.min(3650, transcurridos)));
            sincronizando = false;
        });

        Button aceptar = (Button) getDialogPane().lookupButton(ButtonType.OK);
        aceptar.setText(editando ? "Guardar" : "Agregar");
        aceptar.disableProperty().bind(Bindings.createBooleanBinding(
                () -> fechaLimite.getValue() == null
                        || fechaLimite.getValue().isBefore(LocalDate.now())
                        || (archivoSeleccionado.get() == null && !editando),
                fechaLimite.valueProperty(), archivoSeleccionado));

        setResultConverter(boton -> {
            if (boton != ButtonType.OK) return null;
            // Si el usuario escribió los días sin presionar Enter, los confirmamos.
            try {
                dias.increment(0);
            } catch (RuntimeException valorInvalido) {
                // Si el texto no es un número, se conserva la fecha elegida.
            }
            return new Resultado(archivoSeleccionado.get(), fechaLimite.getValue(),
                    detalle.getText() == null ? "" : detalle.getText().trim(), archivoEliminado);
        });

        setOnShown(evento -> {
            Window ventanaDialogo = getDialogPane().getScene().getWindow();
            if (ventanaDialogo instanceof Stage) {
                Stage stage = (Stage) ventanaDialogo;
                stage.setAlwaysOnTop(true);
                stage.toFront();
                stage.requestFocus();
            }
        });
    }

    private void elegirArchivo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir documento de la notificación");
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documentos Word", "*.doc", "*.docx"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File archivo = selector.showOpenDialog(getDialogPane().getScene().getWindow());
        if (archivo != null) usarArchivo(archivo.toPath());
    }

    private void usarArchivo(Path archivo) {
        if (archivo == null) return;
        archivoSeleccionado.set(archivo);
        archivoParaDescargar.set(archivo);
        nombreArchivo.setText(archivo.getFileName().toString());
        archivoEliminado = false;
        actualizarEstadoArchivo();
    }

    private void actualizarEstadoArchivo() {
        boolean hayArchivo = archivoParaDescargar.get() != null;
        subir.setVisible(!hayArchivo);
        subir.setManaged(!hayArchivo);
        nombreArchivo.setVisible(hayArchivo);
        nombreArchivo.setManaged(hayArchivo);
        descargar.setVisible(hayArchivo);
        descargar.setManaged(hayArchivo);
        eliminar.setVisible(hayArchivo);
        eliminar.setManaged(hayArchivo);
    }

    private void descargarArchivo() {
        Path origen = archivoParaDescargar.get();
        if (origen == null) return;
        if (!Files.isRegularFile(origen)) {
            mostrarError("No se pudo descargar", "El archivo adjunto ya no está disponible.");
            return;
        }

        FileChooser selector = new FileChooser();
        selector.setTitle("Descargar documento de la notificación");
        selector.setInitialFileName(origen.getFileName().toString());
        File destino = selector.showSaveDialog(getDialogPane().getScene().getWindow());
        if (destino == null) return;
        descargarArchivo(origen, destino.toPath());
    }

    private void descargarArchivo(Path origen, Path destino) {
        try {
            Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            mostrarError("No se pudo descargar", "No se pudo copiar el documento:\n" + ex.getMessage());
        }
    }

    private void eliminarArchivo() {
        archivoSeleccionado.set(null);
        archivoParaDescargar.set(null);
        nombreArchivo.setText("Ningún archivo seleccionado");
        archivoEliminado = true;
        actualizarEstadoArchivo();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.initOwner(getDialogPane().getScene().getWindow());
        alerta.showAndWait();
    }
}
