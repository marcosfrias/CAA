package com.eia.app;

import com.dlsc.formsfx.model.structure.DataField;
import com.dlsc.formsfx.model.structure.Element;
import com.dlsc.formsfx.model.structure.Field;
import com.dlsc.formsfx.model.structure.Form;
import com.dlsc.formsfx.model.structure.Section;
import com.dlsc.formsfx.model.structure.SingleSelectionField;
import com.dlsc.formsfx.model.structure.StringField;
import com.dlsc.formsfx.view.controls.SimpleComboBoxControl;
import com.dlsc.formsfx.view.controls.SimpleTextControl;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Campos del informe, equivalentes a las columnas de la planilla de industrias.
 * Resoluciones, auditorías y notificaciones comparten una sola sección; las
 * filas se agregan con "+" y se quitan con "−". Solo la resolución lleva el año
 * editable (la auditoría ya tiene fecha), y todo se guarda por expediente.
 */
public final class FormularioModel {

    private static final int MAX_FILAS = Integer.MAX_VALUE;

    private static final List<String> CLAVES_TEXTO = List.of(
            "company", "fileNumber", "email",
            "notaNumero", "notaLetra", "notaFecha", "notaTema",
            "notaRespuesta", "notaTipo", "respuestaFecha", "observations",
            "diasDesdeUltimaAuditoria");

    private final Map<String, StringProperty> campos = new LinkedHashMap<>();
    private final Map<Integer, BooleanProperty> certificados = new LinkedHashMap<>();
    private final List<String> actividadesDisponibles;
    private final int anioEvento;
    private final int cantidadAuditorias;
    private final int cantidadResoluciones;
    private final int cantidadNotificaciones;
    private final Form form;
    private final SingleSelectionField<String> campoActividad;

    public FormularioModel(Map<String, String> guardados) {
        this(guardados, LocalDate.now().getYear(), List.of());
    }

    public FormularioModel(Map<String, String> guardados, int anioEvento) {
        this(guardados, anioEvento, List.of());
    }

    public FormularioModel(Map<String, String> guardados, int anioEvento,
                           List<String> actividadesDisponibles) {
        this(guardados, anioEvento, actividadesDisponibles,
                contarAuditorias(guardados, anioEvento), contarResoluciones(guardados, anioEvento),
                contarNotificaciones(guardados, anioEvento));
    }

    public FormularioModel(Map<String, String> guardados, int anioEvento,
                           List<String> actividadesDisponibles,
                           int cantidadAuditorias, int cantidadResoluciones, int cantidadNotificaciones) {
        this.actividadesDisponibles = new ArrayList<>(actividadesDisponibles == null
                ? List.of() : actividadesDisponibles);
        this.anioEvento = anioEvento;
        this.cantidadAuditorias = Math.max(1, Math.min(cantidadAuditorias, MAX_FILAS));
        this.cantidadResoluciones = Math.max(1, Math.min(cantidadResoluciones, MAX_FILAS));
        this.cantidadNotificaciones = Math.max(1, Math.min(cantidadNotificaciones, MAX_FILAS));

        for (String clave : CLAVES_TEXTO) {
            campos.put(clave, new SimpleStringProperty(guardados.getOrDefault(clave, "")));
        }

        List<Element> auditorias = new ArrayList<>();
        for (int fila = 1; fila <= this.cantidadAuditorias; fila++) {
            int atras = anioEvento - (fila - 1);
            int adelante = anioEvento + (fila - 1);

            String claveFecha = "auditoriaFecha" + fila;
            String claveConsultor = "auditoriaConsultor" + fila;
            String claveAuditor = "auditoriaAuditor" + fila;
            campos.put(claveFecha, new SimpleStringProperty(fila == 1
                    ? tomar(guardados, claveFecha, "auditFecha" + adelante, "auditFecha" + atras, "audit2025Date")
                    : tomar(guardados, claveFecha, "auditFecha" + adelante, "auditFecha" + atras)));
            campos.put(claveConsultor, new SimpleStringProperty(fila == 1
                    ? tomar(guardados, claveConsultor, "consultor" + adelante, "consultor" + atras, "consultant")
                    : tomar(guardados, claveConsultor, "consultor" + adelante, "consultor" + atras)));
            campos.put(claveAuditor, new SimpleStringProperty(fila == 1
                    ? tomar(guardados, claveAuditor, "auditor" + adelante, "auditor" + atras, "auditor1")
                    : tomar(guardados, claveAuditor, "auditor" + adelante, "auditor" + atras)));

            auditorias.add(campo(claveFecha, "Fecha auditoría").placeholder("DD/MM/AAAA")
                    .styleClass("fila-inicio"));
            auditorias.add(campo(claveConsultor, "Consultor"));
            auditorias.add(campo(claveAuditor, "Auditor"));
        }

        List<Element> resoluciones = new ArrayList<>();
        int anioSugerido = anioEvento;
        for (int fila = 1; fila <= this.cantidadResoluciones; fila++) {
            int atras = anioEvento - (fila - 1);

            String claveAnio = "resolucionAnio" + fila;
            String textoAnio = guardados.get(claveAnio);
            if (esVacio(textoAnio)) {
                boolean legacy = guardados.containsKey("resolucion" + atras)
                        || guardados.containsKey("certifico" + atras);
                textoAnio = String.valueOf(legacy ? atras : anioSugerido);
            }
            campos.put(claveAnio, new SimpleStringProperty(textoAnio));
            anioSugerido = numero(textoAnio, anioSugerido) + 1;

            int anio = numero(textoAnio, anioEvento);
            String clave = "resolucion" + fila;
            campos.put(clave, new SimpleStringProperty(tomar(guardados, clave,
                    "resolucion" + anio, "resolucion" + atras)));

            String valor = guardados.get("certifico" + fila);
            if (valor == null) valor = guardados.get("certifico" + anio);
            if (valor == null) valor = guardados.get("certifico" + atras);
            if (valor == null && fila == 1) valor = guardados.get("certifico");
            certificados.put(fila, new SimpleBooleanProperty(Boolean.parseBoolean(valor)));

            StringProperty anioPropiedad = campos.get(claveAnio);
            BooleanProperty certificado = certificados.get(fila);
            StringField resolucion = campo(clave, "Resolución periodo")
                    .render(() -> new ResolucionControl(anioPropiedad, certificado));
            if (fila > 1) resolucion.styleClass("fila-inicio");
            resoluciones.add(resolucion);
        }

        List<Element> notificaciones = new ArrayList<>();
        for (int fila = 1; fila <= this.cantidadNotificaciones; fila++) {
            int atras = anioEvento - (fila - 1);

            String clave = "notificacion" + fila;
            campos.put(clave, new SimpleStringProperty(tomar(guardados, clave,
                    "notificacion" + atras, "notificaciones" + atras)));

            notificaciones.add(campo(clave, "Notificaciones").multiline(true)
                    .styleClass("fila-inicio"));
        }

        List<Element> registros = new ArrayList<>();
        registros.addAll(resoluciones);
        registros.addAll(auditorias);
        registros.addAll(notificaciones);

        List<String> opcionesActividad = new ArrayList<>();
        for (String nombre : actividadesDisponibles) {
            if (nombre != null && !nombre.isBlank() && !opcionesActividad.contains(nombre)) {
                opcionesActividad.add(nombre);
            }
        }
        String actividadGuardada = guardados.getOrDefault("actividad", "").trim();
        if (!actividadGuardada.isEmpty() && !opcionesActividad.contains(actividadGuardada)) {
            opcionesActividad.add(actividadGuardada);
        }
        int indiceActividad = actividadGuardada.isEmpty()
                ? -1 : opcionesActividad.indexOf(actividadGuardada);
        campoActividad = Field.ofSingleSelectionType(opcionesActividad, indiceActividad)
                .label("Actividad").id("actividad").render(ActividadControl::new);

        form = Form.of(
                Section.of(
                        campoActividad,
                        campo("company", "Empresa").required("Obligatorio"),
                        campo("fileNumber", "Nº de expediente"),
                        campo("email", "Correo electrónico")
                ).title("Identificación"),
                Section.of(registros.toArray(new Element[0]))
                        .title("Resoluciones, auditorías y notificaciones"),
                Section.of(
                        campo("notaNumero", "Nota Nº"),
                        campo("notaLetra", "Letra"),
                        campo("notaFecha", "Fecha"),
                        campo("notaTema", "Tema"),
                        campo("notaRespuesta", "Respuesta"),
                        campo("notaTipo", "Tipo"),
                        campo("respuestaFecha", "Fecha de respuesta"),
                        campo("observations", "Observaciones").multiline(true),
                        campo("diasDesdeUltimaAuditoria", "Días desde última auditoría").editable(false)
                ).title("Nota de seguimiento")
        ).title("Formulario de auditoría");
    }

    /** Devuelve un StringField enlazado al valor guardado bajo la clave indicada. */
    private StringField campo(String clave, String etiqueta) {
        return (StringField) Field.ofStringType(campos.get(clave)).label(etiqueta).id(clave);
    }

    /** Valor de la clave nueva; si no existe, busca en las claves del formato anterior. */
    private static String tomar(Map<String, String> guardados, String clave, String... alternativas) {
        String valor = guardados.get(clave);
        if (valor != null) return valor;
        for (String alternativa : alternativas) {
            valor = guardados.get(alternativa);
            if (valor != null) return valor;
        }
        return "";
    }

    private static boolean tieneAuditoria(Map<String, String> guardados, int anio) {
        return guardados.containsKey("auditFecha" + anio)
                || guardados.containsKey("consultor" + anio)
                || guardados.containsKey("auditor" + anio);
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static int numero(String texto, int porDefecto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (RuntimeException e) {
            return porDefecto;
        }
    }

    /** Cuenta filas guardadas con el formato por fila; si no hay, usa el formato por año. */
    private static int contarAuditorias(Map<String, String> guardados, int anioEvento) {
        int nuevas = 0;
        while (nuevas < MAX_FILAS
                && (guardados.containsKey("auditoriaAnio" + (nuevas + 1))
                || guardados.containsKey("auditoriaFecha" + (nuevas + 1))
                || guardados.containsKey("auditoriaConsultor" + (nuevas + 1))
                || guardados.containsKey("auditoriaAuditor" + (nuevas + 1)))) nuevas++;
        if (nuevas > 0) return nuevas;

        int cantidad = 1;
        while (cantidad < MAX_FILAS
                && (tieneAuditoria(guardados, anioEvento - cantidad)
                || tieneAuditoria(guardados, anioEvento + cantidad))) cantidad++;
        return cantidad;
    }

    private static int contarResoluciones(Map<String, String> guardados, int anioEvento) {
        int nuevas = 0;
        while (nuevas < MAX_FILAS
                && (guardados.containsKey("resolucionAnio" + (nuevas + 1))
                || guardados.containsKey("resolucion" + (nuevas + 1))
                || guardados.containsKey("certifico" + (nuevas + 1)))) nuevas++;
        if (nuevas > 0) return nuevas;

        int cantidad = 1;
        while (cantidad < MAX_FILAS
                && (guardados.containsKey("resolucion" + (anioEvento - cantidad))
                || guardados.containsKey("certifico" + (anioEvento - cantidad)))) cantidad++;
        return cantidad;
    }

    private static int contarNotificaciones(Map<String, String> guardados, int anioEvento) {
        int nuevas = 0;
        while (nuevas < MAX_FILAS
                && (guardados.containsKey("notificacionAnio" + (nuevas + 1))
                || guardados.containsKey("notificacion" + (nuevas + 1)))) nuevas++;
        if (nuevas > 0) return nuevas;

        int cantidad = 1;
        while (cantidad < MAX_FILAS
                && (guardados.containsKey("notificacion" + (anioEvento - cantidad))
                || guardados.containsKey("notificaciones" + (anioEvento - cantidad)))) cantidad++;
        return cantidad;
    }

    public Form getForm() { return form; }

    public List<String> getActividadesDisponibles() {
        return List.copyOf(actividadesDisponibles);
    }

    public int getAnioEvento() { return anioEvento; }

    public int getCantidadAuditorias() { return cantidadAuditorias; }

    public int getCantidadResoluciones() { return cantidadResoluciones; }

    public int getCantidadNotificaciones() { return cantidadNotificaciones; }

    public boolean puedeAgregarAuditoria() { return cantidadAuditorias < MAX_FILAS; }

    public boolean puedeQuitarAuditoria() { return cantidadAuditorias > 1; }

    public boolean puedeAgregarResolucion() { return cantidadResoluciones < MAX_FILAS; }

    public boolean puedeQuitarResolucion() { return cantidadResoluciones > 1; }

    public boolean puedeAgregarNotificacion() { return cantidadNotificaciones < MAX_FILAS; }

    public boolean puedeQuitarNotificacion() { return cantidadNotificaciones > 1; }

    /** Clave del último auditor: el "+" y el "−" se ubican junto a él. */
    public String getClaveUltimaAuditoria() { return "auditoriaAuditor" + cantidadAuditorias; }

    /** Clave de la última resolución: el "+" y el "−" se ubican junto a ella. */
    public String getClaveUltimaResolucion() { return "resolucion" + cantidadResoluciones; }

    /** Clave de la última notificación: el "+" y el "−" se ubican junto a ella. */
    public String getClaveUltimaNotificacion() { return "notificacion" + cantidadNotificaciones; }

    public Map<String, String> getValores() {
        Map<String, String> r = new LinkedHashMap<>();
        campos.forEach((k, p) -> r.put(k, p.get() == null ? "" : p.get()));
        for (Field<?> campo : form.getFields()) {
            if (campo instanceof DataField && campo.getID() != null) {
                String escrito = ((DataField<?, ?, ?>) campo).getUserInput();
                if (escrito != null) r.put(campo.getID(), escrito);
            }
        }
        r.put("actividad", campoActividad.getSelection() == null ? "" : campoActividad.getSelection());
        certificados.forEach((fila, p) -> r.put("certifico" + fila, String.valueOf(p.get())));
        if (certificados.containsKey(1)) {
            r.put("certifico", String.valueOf(certificados.get(1).get()));
        }
        return r;
    }

    /** Selector compacto de año: se puede escribir o subir/bajar con las flechas. */
    private static Spinner<Integer> spinnerAnio(StringProperty propiedad) {
        Spinner<Integer> spinner = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory fabrica =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1900, 2200,
                        numero(propiedad.get(), LocalDate.now().getYear()));
        spinner.setValueFactory(fabrica);
        spinner.setEditable(true);
        spinner.setPrefWidth(105);
        spinner.getStyleClass().add("anio-spinner");
        fabrica.valueProperty().addListener((obs, viejo, nuevo) -> {
            if (nuevo != null) propiedad.set(String.valueOf(nuevo));
        });
        spinner.getEditor().textProperty().addListener((obs, viejo, nuevo) -> {
            if (nuevo == null || !nuevo.matches("\\d{4}")) return;
            int valor = Integer.parseInt(nuevo);
            if (fabrica.getValue() == null || fabrica.getValue() != valor) fabrica.setValue(valor);
        });
        return spinner;
    }

    /** Selector de actividad con el mismo texto inicial que el selector del evento. */
    private static final class ActividadControl extends SimpleComboBoxControl<String> {
        @Override
        public void initializeParts() {
            super.initializeParts();
            comboBox.setPromptText("Sin actividad");
        }
    }

    /** Campo de resolución con año compacto y la casilla "Certificó" en el mismo renglón. */
    private static final class ResolucionControl extends SimpleTextControl {
        private final Spinner<Integer> anio;
        private final CheckBox certificado;

        private ResolucionControl(StringProperty anioPropiedad, BooleanProperty propiedad) {
            anio = spinnerAnio(anioPropiedad);
            certificado = new CheckBox("Certificó");
            certificado.selectedProperty().bindBidirectional(propiedad);
        }

        @Override
        public void layoutParts() {
            super.layoutParts();
            HBox fila = new HBox(8, anio, stack, certificado);
            HBox.setHgrow(stack, Priority.ALWAYS);
            fila.setAlignment(Pos.CENTER_LEFT);
            getChildren().remove(stack);
            add(fila, 2, 0, Math.max(1, field.getSpan() - 2), 1);
        }
    }
}
