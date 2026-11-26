package com.caa.app;

import com.dlsc.formsfx.model.structure.DataField;
import com.dlsc.formsfx.model.structure.Field;
import com.dlsc.formsfx.view.renderer.FormRenderer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class FormularioPane extends BorderPane {

    private final ScrollPane scroll = new ScrollPane();
    private final Button agregarAuditoria = new Button("+");
    private final Button quitarAuditoria = new Button("−");
    private final Button agregarResolucion = new Button("+");
    private final Button quitarResolucion = new Button("−");
    private final Button agregarNotificacion = new Button("+");
    private final Button quitarNotificacion = new Button("−");
    private final HBox controlesAuditoria = new HBox(4, quitarAuditoria, agregarAuditoria);
    private final HBox controlesResolucion = new HBox(4, quitarResolucion, agregarResolucion);
    private final HBox controlesNotificacion = new HBox(4, quitarNotificacion, agregarNotificacion);
    private final Button restablecer = new Button("Restablecer");
    private final Button guardar = new Button("Guardar");
    private final Button aplicar = new Button("Aplicar");
    private final Consumer<Map<String, String>> alGuardar;
    private final Consumer<Map<String, String>> alAplicar;
    private Map<String, String> guardado = new LinkedHashMap<>();
    private FormularioModel modelo;

    public FormularioPane(FormularioModel modeloInicial, Consumer<Map<String, String>> alGuardar,
                          Consumer<Map<String, String>> alAplicar) {
        this.modelo = modeloInicial;
        this.alGuardar = alGuardar;
        this.alAplicar = alAplicar;
        this.guardado.putAll(modeloInicial.getValores());
        scroll.setFitToWidth(true);

        agregarAuditoria.setTooltip(new Tooltip("Agregar otra auditoría"));
        quitarAuditoria.setTooltip(new Tooltip("Quitar la última auditoría"));
        agregarResolucion.setTooltip(new Tooltip("Agregar otra resolución"));
        quitarResolucion.setTooltip(new Tooltip("Quitar la última resolución"));
        agregarNotificacion.setTooltip(new Tooltip("Agregar otra notificación"));
        quitarNotificacion.setTooltip(new Tooltip("Quitar la última notificación"));
        for (Button boton : new Button[]{agregarAuditoria, agregarResolucion, agregarNotificacion}) {
            boton.setStyle("-fx-font-weight: bold; -fx-text-fill: #16833b;");
        }
        for (Button boton : new Button[]{quitarAuditoria, quitarResolucion, quitarNotificacion}) {
            boton.setStyle("-fx-font-weight: bold; -fx-text-fill: #b00020;");
        }

        agregarAuditoria.setOnAction(e -> reconstruir(modelo.getCantidadAuditorias() + 1,
                modelo.getCantidadResoluciones(), modelo.getCantidadNotificaciones()));
        quitarAuditoria.setOnAction(e -> reconstruir(modelo.getCantidadAuditorias() - 1,
                modelo.getCantidadResoluciones(), modelo.getCantidadNotificaciones()));
        agregarResolucion.setOnAction(e -> reconstruir(modelo.getCantidadAuditorias(),
                modelo.getCantidadResoluciones() + 1, modelo.getCantidadNotificaciones()));
        quitarResolucion.setOnAction(e -> reconstruir(modelo.getCantidadAuditorias(),
                modelo.getCantidadResoluciones() - 1, modelo.getCantidadNotificaciones()));
        agregarNotificacion.setOnAction(e -> reconstruir(modelo.getCantidadAuditorias(),
                modelo.getCantidadResoluciones(), modelo.getCantidadNotificaciones() + 1));
        quitarNotificacion.setOnAction(e -> reconstruir(modelo.getCantidadAuditorias(),
                modelo.getCantidadResoluciones(), modelo.getCantidadNotificaciones() - 1));

        guardar.setOnAction(e -> {
            modelo.getForm().persist();
            Map<String, String> valores = modelo.getValores();
            alGuardar.accept(valores);
            guardado = new LinkedHashMap<>(valores);
        });
        aplicar.setOnAction(e -> {
            modelo.getForm().persist();
            alAplicar.accept(modelo.getValores());
        });
        restablecer.setOnAction(e -> restablecer());

        HBox barra = new HBox(10, restablecer, guardar, aplicar);
        barra.setPadding(new Insets(10));
        barra.setAlignment(Pos.CENTER_RIGHT);

        setCenter(scroll);
        setBottom(barra);
        renderizar();
    }

    /** Arma el renderer con el modelo actual; se vuelve a llamar al agregar o quitar filas. */
    private void renderizar() {
        agregarAuditoria.setDisable(!modelo.puedeAgregarAuditoria());
        quitarAuditoria.setDisable(!modelo.puedeQuitarAuditoria());
        agregarResolucion.setDisable(!modelo.puedeAgregarResolucion());
        quitarResolucion.setDisable(!modelo.puedeQuitarResolucion());
        agregarNotificacion.setDisable(!modelo.puedeAgregarNotificacion());
        quitarNotificacion.setDisable(!modelo.puedeQuitarNotificacion());

        for (Field<?> campo : modelo.getForm().getFields()) {
            if (modelo.getClaveUltimaAuditoria().equals(campo.getID())) campo.labelDescription(controlesAuditoria);
            if (modelo.getClaveUltimaResolucion().equals(campo.getID())) campo.labelDescription(controlesResolucion);
            if (modelo.getClaveUltimaNotificacion().equals(campo.getID())) campo.labelDescription(controlesNotificacion);
        }
        FormRenderer renderer = new FormRenderer(modelo.getForm());
        scroll.setContent(renderer);
        Platform.runLater(() -> {
            for (Node nodo : renderer.lookupAll(".fila-inicio")) {
                nodo.setStyle("-fx-border-color: #cccccc transparent transparent transparent;"
                        + " -fx-border-width: 1 0 0 0; -fx-padding: 10 0 0 0;");
            }
        });
        guardar.disableProperty().unbind();
        guardar.disableProperty().bind(modelo.getForm().persistableProperty().not());
        restablecer.disableProperty().unbind();
        boolean filasAgregadas = modelo.getCantidadAuditorias() > 1
                || modelo.getCantidadResoluciones() > 1
                || modelo.getCantidadNotificaciones() > 1;
        if (filasAgregadas) {
            restablecer.setDisable(false);
        } else {
            restablecer.disableProperty().bind(modelo.getForm().changedProperty().not());
        }
    }

    /** Vuelve a una fila por sección y a los últimos valores guardados. */
    private void restablecer() {
        modelo = new FormularioModel(guardado, modelo.getAnioEvento(),
                modelo.getActividadesDisponibles(), 1, 1, 1);
        renderizar();
    }

    /** Recrea el formulario con otra cantidad de filas, conservando lo ya escrito. */
    private void reconstruir(int auditorias, int resoluciones, int notificaciones) {
        double posicionScroll = scroll.getVvalue();
        Map<String, String> escrito = new LinkedHashMap<>();
        for (Field<?> campo : modelo.getForm().getFields()) {
            if (campo instanceof DataField && campo.getID() != null) {
                String texto = ((DataField<?, ?, ?>) campo).getUserInput();
                escrito.put(campo.getID(), texto == null ? "" : texto);
            }
        }
        FormularioModel nuevo = new FormularioModel(modelo.getValores(), modelo.getAnioEvento(),
                modelo.getActividadesDisponibles(), auditorias, resoluciones, notificaciones);
        for (Field<?> campo : nuevo.getForm().getFields()) {
            if (campo instanceof DataField && escrito.containsKey(campo.getID())) {
                ((DataField<?, ?, ?>) campo).userInputProperty().set(escrito.get(campo.getID()));
            }
        }
        modelo = nuevo;
        renderizar();
        Platform.runLater(() -> scroll.setVvalue(posicionScroll));
    }
}
