/*
 *  Copyright (C) 2026 marcos frias Software & Consulting
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.eia.app;

import com.calendarfx.model.Calendar;
import com.calendarfx.model.Calendar.Style;
import com.calendarfx.model.CalendarEvent;
import com.calendarfx.model.CalendarSource;
import com.calendarfx.model.Entry;
import com.calendarfx.view.CalendarView;
import com.calendarfx.view.CalendarSelector;
import com.calendarfx.view.YearMonthView;
import com.calendarfx.view.DateControl;
import com.calendarfx.view.Messages;
import com.calendarfx.model.Interval;
import com.eia.app.FormularioModel;
import com.eia.app.FormularioPane;
import fr.brouillard.oss.cssfx.CSSFX;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.Parent;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Properties;

public class CalendarApp extends Application {

    static final String PROPIEDAD_ACTIVIDAD = "actividad";
    private static final String PROPIEDAD_EXPEDIENTE = "expedienteId";
    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Cordoba");
    private static final Path CARPETA = Almacenamiento.carpetaDatos();
    private static final Path ARCHIVO_INDUSTRIAS = CARPETA.resolve("industrias.txt");
    private static final Path ARCHIVO_ACTIVIDADES = CARPETA.resolve("actividades.txt");
    private static final Path ARCHIVO_EVENTOS = CARPETA.resolve("eventos.txt");

    private final CalendarSource industrias = new CalendarSource("Industrias");
    private final CalendarSource actividades = new CalendarSource("Actividades");
    private final List<CalendarSource> grupos = List.of(industrias, actividades);
    private final Map<Calendar, Style> estilos = new HashMap<>();
    private final Set<Entry<?>> entradas = new LinkedHashSet<>();
    private final PauseTransition guardadoPendiente = new PauseTransition(Duration.millis(500));
    private Datos datos;
    private boolean cargando = false;
    private Stage ventana;
    private Stage ventanaFormsFx;
    private String formularioFormsFxId;
    private final Map<String, Stage> ventanasExpediente = new HashMap<>();

    @Override
    public void start(Stage stage) {
        ventana = stage;
        try {
            Almacenamiento.inicializar();
        } catch (IOException e) {
            Alert alerta = new Alert(Alert.AlertType.ERROR);
            alerta.setTitle("No se pudo abrir la carpeta de datos");
            alerta.setHeaderText(null);
            alerta.setContentText("La aplicación no puede guardar información en:\n" + CARPETA
                    + "\n\nVerificá los permisos de esa carpeta.");
            alerta.initOwner(stage);
            alerta.showAndWait();
            Platform.exit();
            return;
        }

        CalendarView calendarView = new CalendarView();
        calendarView.setZoneId(ZONA_ARGENTINA);
        // La zona horaria de la aplicación se mantiene en Argentina; Datos ocupa
        // el espacio del selector de zona con los filtros de visitas.
        calendarView.setEnableTimeZoneSupport(true);
        calendarView.setShowAddCalendarButton(false); // se reemplaza por nuestro botón
        calendarView.setShowPrintButton(false);

        guardadoPendiente.setOnFinished(e -> guardarEventos());
        cargarIndustrias();
        cargarActividades();
        cargarEventos();
        // La fuente vacía mantiene espacio entre los grupos en el desplegable.
        calendarView.getCalendarSources().setAll(industrias, new CalendarSource(" "), actividades);
        instalarSelectorSoloIndustrias(calendarView);
        configurarMenuContextualEvento(calendarView);

        iniciarReloj(calendarView);

        BorderPane root = new BorderPane(calendarView);
        Scene scene = new Scene(root);
        CSSFX.start(scene);

        stage.setTitle("EIA");
        aplicarIcono(stage);
        stage.setScene(scene);
        dimensionarYCentrar(stage, null, 1280, 720);
        stage.show();

        datos = new Datos(calendarView, grupos, actividades, entradas);
        datos.instalar(root);

        calendarView.selectedPageProperty().addListener((obs, anterior, pagina) -> {
            if (pagina == CalendarView.Page.YEAR) {
                Platform.runLater(() -> configurarColoresAnuales(calendarView));
            }
        });
        if (calendarView.getSelectedPage() == CalendarView.Page.YEAR) {
            Platform.runLater(() -> configurarColoresAnuales(calendarView));
        }

        Button[] botones = {crearBotonAgregar(), crearBotonEliminar()};
        calendarView.selectedPageProperty().addListener((obs, anterior, actual) ->
                Platform.runLater(() -> insertarBotonesEnBarra(root, botones)));
        ubicarBotones(root, botones);
    }

    private static void aplicarIcono(Stage stage) {
        stage.getIcons().add(new Image(
                CalendarApp.class.getResource("/com/eia/app/icon.png").toExternalForm()));
    }

    private static void dimensionarYCentrar(Stage stage, Window owner,
                                            double anchoPreferido, double altoPreferido) {
        Rectangle2D area = pantallaDisponible(owner);
        double ancho = Math.min(anchoPreferido, area.getWidth() * 0.92);
        double alto = Math.min(altoPreferido, area.getHeight() * 0.86);
        stage.setWidth(ancho);
        stage.setHeight(alto);
        stage.setX(area.getMinX() + (area.getWidth() - ancho) / 2);
        stage.setY(area.getMinY() + (area.getHeight() - alto) / 2);
    }

    private static Rectangle2D pantallaDisponible(Window owner) {
        if (owner != null && owner.isShowing()) {
            double centroX = owner.getX() + owner.getWidth() / 2;
            double centroY = owner.getY() + owner.getHeight() / 2;
            var pantallas = Screen.getScreensForRectangle(centroX, centroY, 1, 1);
            if (!pantallas.isEmpty()) {
                return pantallas.get(0).getVisualBounds();
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    @Override
    public void stop() {
        guardarIndustrias();
        guardarGrupo(actividades);
        guardarEventos();
    }

    // ---------- Industrias ----------

    private void agregarCalendario(CalendarSource grupo, String nombre) {
        Calendar calendario = new Calendar(nombre);
        calendario.setShortName(nombre.substring(0, 1).toUpperCase());
        Style estilo = estiloLibre();
        calendario.setStyle(estilo);
        estilos.put(calendario, estilo);
        calendario.addEventHandler(ev -> {
            if (ev instanceof CalendarEvent) alCambiar((CalendarEvent) ev);
        });
        grupo.getCalendars().add(calendario);
    }

    /** Primer estilo (color) que no esté en uso; si se agotan, vuelve a empezar. */
    private Style estiloLibre() {
        for (Style estilo : Style.values()) {
            if (!estilos.containsValue(estilo)) return estilo;
        }
        return Style.values()[estilos.size() % Style.values().length];
    }

    private void eliminarCalendario(Calendar calendario) {
        entradas.removeIf(en -> en.getCalendar() == calendario); // sus eventos dejan de guardarse
        estilos.remove(calendario);                              // el color queda libre
        for (CalendarSource grupo : grupos) {
            if (grupo.getCalendars().remove(calendario)) guardarGrupo(grupo);
        }
        guardarEventos();
        if (datos != null) datos.refrescar();
    }

    private Button crearBotonAgregar() {
        Button boton = new Button("+");
        boton.setOnAction(e -> {
            List<String> nombresGrupos = List.of("Industrias", "Actividades");
            ChoiceDialog<String> elegirGrupo = new ChoiceDialog<>(nombresGrupos.get(0), nombresGrupos);
            elegirGrupo.setTitle("Nueva categoría");
            elegirGrupo.setHeaderText("¿En qué grupo querés agregarla?");
            elegirGrupo.setContentText("Grupo:");
            elegirGrupo.initOwner(ventana);
            elegirGrupo.showAndWait().ifPresent(nombreGrupo -> {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Nueva categoría");
                dialog.setHeaderText(null);
                dialog.setContentText("Nombre de la categoría:");
                dialog.initOwner(ventana);

                dialog.showAndWait()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .ifPresent(nombre -> {
                            CalendarSource grupo = "Actividades".equals(nombreGrupo) ? actividades : industrias;
                            agregarCalendario(grupo, nombre);
                            guardarGrupo(grupo);
                        });
            });
        });
        return boton;
    }

    private Button crearBotonEliminar() {
        Button boton = new Button("−");
        boton.setOnAction(e -> {
            List<String> nombres = new ArrayList<>();
            Map<String, Calendar> porNombre = new LinkedHashMap<>();
            for (CalendarSource grupo : grupos) {
                for (Calendar c : grupo.getCalendars()) porNombre.put(grupo.getName() + " — " + c.getName(), c);
            }
            nombres.addAll(porNombre.keySet());
            if (nombres.isEmpty()) return;

            ChoiceDialog<String> elegir = new ChoiceDialog<>(nombres.get(0), nombres);
            elegir.setTitle("Eliminar categoría");
            elegir.setHeaderText(null);
            elegir.setContentText("Categoría:");
            elegir.initOwner(ventana);

            elegir.showAndWait().ifPresent(nombre -> {
                Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                        "Se eliminará \"" + nombre + "\" y todos sus eventos. No se puede deshacer.",
                        ButtonType.OK, ButtonType.CANCEL);
                confirmar.setHeaderText(null);
                confirmar.initOwner(ventana);
                confirmar.showAndWait()
                        .filter(b -> b == ButtonType.OK)
                        .ifPresent(b -> eliminarCalendario(porNombre.get(nombre)));
            });
        });
        return boton;
    }

    private void abrirFormsFxDemo(Entry<?> entrada) {
        Object idActual = entrada.getProperties().get(PROPIEDAD_EXPEDIENTE);
        String idFormulario;
        if (idActual instanceof String && !((String) idActual).isBlank()) {
            idFormulario = (String) idActual;
        } else {
            idFormulario = UUID.randomUUID().toString();
            entrada.getProperties().put(PROPIEDAD_EXPEDIENTE, idFormulario);
            guardarEventos();
        }

        if (ventanaFormsFx != null && idFormulario.equals(formularioFormsFxId)) {
            ventanaFormsFx.show();
            ventanaFormsFx.toFront();
            ventanaFormsFx.requestFocus();
            return;
        }
        if (ventanaFormsFx != null) ventanaFormsFx.close();

        int anioEvento = entrada.getInterval().getStartDateTime().toLocalDate().getYear();
        Map<String, String> guardados = cargarFormulario(idFormulario);
        completarConEvento(guardados, entrada);
        guardados.put("diasDesdeUltimaAuditoria", diasDesdeUltimaAuditoria(entrada, entradas));
        FormularioModel modelo = new FormularioModel(guardados, anioEvento, nombresActividades());
        FormularioPane formulario = new FormularioPane(modelo, valores -> {
            guardarFormulario(idFormulario, valores);
            actualizarActividadEvento(entrada, valores.get("actividad"));
        });

        Stage demo = new Stage();
        demo.initOwner(ventana);
        aplicarIcono(demo);
        demo.setTitle("Formulario de auditoría");
        demo.setScene(new Scene(formulario));
        dimensionarYCentrar(demo, ventana, 1120, 660);
        demo.setOnHidden(evento -> {
            if (idFormulario.equals(formularioFormsFxId)) {
                ventanaFormsFx = null;
                formularioFormsFxId = null;
            }
        });

        ventanaFormsFx = demo;
        formularioFormsFxId = idFormulario;
        demo.show();

    }

    private void actualizarActividadEvento(Entry<?> entrada, String actividad) {
        if (actividad == null || actividad.isBlank()) {
            entrada.getProperties().remove(PROPIEDAD_ACTIVIDAD);
        } else {
            entrada.getProperties().put(PROPIEDAD_ACTIVIDAD, actividad.trim());
        }
        guardarEventos();
        if (datos != null) datos.refrescar();
    }

    /**
     * Precarga con lo que ya tiene el evento: la industria (empresa), la actividad
     * y la fecha. Nunca pisa valores que el usuario ya haya guardado.
     */
    private static void completarConEvento(Map<String, String> valores, Entry<?> entrada) {
        String empresa = entrada.getCalendar() == null ? "" : entrada.getCalendar().getName();
        if (empresa == null || empresa.isBlank()) empresa = entrada.getTitle();
        ponerSiVacio(valores, "company", empresa);

        Object actividad = entrada.getProperties().get(PROPIEDAD_ACTIVIDAD);
        if (actividad instanceof String) ponerSiVacio(valores, "actividad", (String) actividad);

        LocalDate fecha = entrada.getInterval().getStartDateTime().toLocalDate();
        ponerSiVacio(valores, "auditoriaFecha1", String.format("%02d/%02d/%04d",
                fecha.getDayOfMonth(), fecha.getMonthValue(), fecha.getYear()));
    }

    private static void ponerSiVacio(Map<String, String> valores, String clave, String dato) {
        if (dato == null || dato.isBlank()) return;
        String actual = valores.get(clave);
        if (actual == null || actual.isBlank()) valores.put(clave, dato.trim());
    }

    /** Días entre la auditoría anterior de la misma empresa y la fecha de este evento. */
    private static String diasDesdeUltimaAuditoria(Entry<?> entrada, Set<Entry<?>> entradas) {
        LocalDate actual = entrada.getInterval().getStartDateTime().toLocalDate();
        LocalDate anterior = null;
        for (Entry<?> otra : entradas) {
            if (otra == entrada || otra.isRecurrence() || otra.getCalendar() == null) continue;
            if (entrada.getCalendar() == null || otra.getCalendar() != entrada.getCalendar()) continue;
            LocalDate fecha = otra.getInterval().getStartDateTime().toLocalDate();
            if (!fecha.isBefore(actual)) continue;
            if (anterior == null || fecha.isAfter(anterior)) anterior = fecha;
        }
        if (anterior == null) return "Sin auditoría anterior";
        return String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(anterior, actual));
    }


    /** El selector de color del evento ofrece únicamente calendarios de Industrias. */
    private void instalarSelectorSoloIndustrias(CalendarView calendarView) {
        javafx.util.Callback<DateControl.EntryDetailsPopOverContentParameter, Node> contenidoOriginal =
                calendarView.getEntryDetailsPopOverContentCallback();
        calendarView.setEntryDetailsPopOverContentCallback(param -> {
            Node contenido = contenidoOriginal.call(param);
            Platform.runLater(() -> {
                filtrarSelectorCalendarios(contenido);
                if (contenido instanceof com.calendarfx.view.popover.EntryPopOverContentPane) {
                    com.calendarfx.view.popover.EntryPopOverContentPane panel =
                            (com.calendarfx.view.popover.EntryPopOverContentPane) contenido;
                    agregarBotonesJuntoARepetir(panel);
                }
            });
            return contenido;
        });
    }

    /** Coloca los accesos al formulario y al expediente junto a la opción Repetir. */
    private void agregarBotonesJuntoARepetir(
            com.calendarfx.view.popover.EntryPopOverContentPane panel) {
        Button botonExpediente = new Button("+");
        botonExpediente.setAccessibleText("Expediente");
        botonExpediente.setTooltip(new javafx.scene.control.Tooltip("Expediente"));
        botonExpediente.setStyle("-fx-text-fill: #16833b; -fx-font-weight: bold;"
                + " -fx-font-size: 16px; -fx-padding: 0 7 0 7;");
        botonExpediente.setOnAction(evento -> abrirDemo(panel.getEntry()));

        Button botonFormulario = new Button();
        FontIcon iconoDocumento = new FontIcon(FontAwesomeSolid.FILE_ALT);
        iconoDocumento.setIconSize(13);
        iconoDocumento.setIconColor(javafx.scene.paint.Color.web("#4a5568"));
        botonFormulario.setGraphic(iconoDocumento);
        botonFormulario.setStyle("-fx-font-size: 16px; -fx-padding: 0 7 0 7;");
        botonFormulario.setAccessibleText("Formulario");
        botonFormulario.setTooltip(new javafx.scene.control.Tooltip("Formulario"));
        botonFormulario.setOnAction(evento -> abrirFormsFxDemo(panel.getEntry()));

        Button botonExportar = new Button();
        FontIcon iconoExportar = new FontIcon(FontAwesomeSolid.FILE_EXPORT);
        iconoExportar.setIconSize(13);
        iconoExportar.setIconColor(javafx.scene.paint.Color.web("#2a7de1"));
        botonExportar.setGraphic(iconoExportar);
        botonExportar.setStyle("-fx-font-size: 16px; -fx-padding: 0 7 0 7;");
        botonExportar.setAccessibleText("Exportar");
        Object idActual = panel.getEntry().getProperties().get(PROPIEDAD_EXPEDIENTE);
        boolean expedienteGuardado = idActual instanceof String
                && com.eia.documentos.Data.estaGuardado((String) idActual);
        botonExportar.setDisable(!expedienteGuardado);
        botonExportar.setTooltip(new javafx.scene.control.Tooltip(
                expedienteGuardado ? "Exportar" : "Guardá el expediente primero"));
        botonExportar.setOnAction(evento -> exportarExpediente(panel.getEntry()));

        HBox botones = new HBox(4, botonFormulario, botonExpediente, botonExportar);
        botones.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        for (javafx.scene.control.TitledPane seccion : panel.getPanes()) {
            GridPane grilla = buscarGrillaDetalles(seccion.getContent());
            if (grilla == null) continue;
            for (Node nodo : new ArrayList<>(grilla.getChildren())) {
                Integer fila = GridPane.getRowIndex(nodo);
                Integer columna = GridPane.getColumnIndex(nodo);
                if (nodo instanceof javafx.scene.control.MenuButton
                        && Integer.valueOf(4).equals(fila)
                        && Integer.valueOf(1).equals(columna)) {
                    javafx.scene.layout.Region espacio = new javafx.scene.layout.Region();
                    HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);
                    HBox filaRepetir = new HBox(14, nodo, espacio, botones);
                    filaRepetir.setMaxWidth(Double.MAX_VALUE);
                    filaRepetir.setStyle("-fx-alignment: center-left;");
                    grilla.getChildren().remove(nodo);
                    grilla.add(filaRepetir, 1, 4);
                    return;
                }
            }
        }
    }

    private void exportarExpediente(Entry<?> entrada) {
        Object idActual = entrada.getProperties().get(PROPIEDAD_EXPEDIENTE);
        if (!(idActual instanceof String) || ((String) idActual).isBlank()
                || !com.eia.documentos.Data.estaGuardado((String) idActual)) {
            Alert alerta = new Alert(Alert.AlertType.WARNING);
            alerta.setTitle("No se puede exportar");
            alerta.setHeaderText(null);
            alerta.setContentText("Abrí el expediente y pulsá Guardar antes de exportar.");
            alerta.initOwner(ventana);
            alerta.showAndWait();
            return;
        }
        String idExpediente = (String) idActual;

        Stage ventanaExpediente = ventanasExpediente.get(idExpediente);
        if (ventanaExpediente != null) {
            Object controlador = ventanaExpediente.getProperties().get("tempController");
            if (controlador instanceof com.eia.documentos.TempController) {
                ((com.eia.documentos.TempController) controlador).exportar(ventana);
                return;
            }
        }

        try {
            com.eia.documentos.TempController controlador = cargarControladorExpediente(idExpediente);
            controlador.exportar(ventana);
        } catch (IOException ex) {
            Alert alerta = new Alert(Alert.AlertType.ERROR);
            alerta.setTitle("No se pudo exportar");
            alerta.setHeaderText(null);
            alerta.setContentText("No se pudo cargar el expediente: " + ex.getMessage());
            alerta.initOwner(ventana);
            alerta.showAndWait();
        }
    }

    private com.eia.documentos.TempController cargarControladorExpediente(String idExpediente)
            throws IOException {
        FXMLLoader cargador = new FXMLLoader(
                CalendarApp.class.getResource("/com/eia/documentos/temp-view.fxml"));
        cargador.load();
        com.eia.documentos.TempController controlador = cargador.getController();
        controlador.cargarExpediente(idExpediente);
        return controlador;
    }

    /** Abre la aplicación Demo como ventana interactiva sobre el calendario. */
    private void abrirDemo(Entry<?> entrada) {
        Object idActual = entrada.getProperties().get(PROPIEDAD_EXPEDIENTE);
        String idExpediente;
        if (idActual instanceof String && !((String) idActual).isBlank()) {
            idExpediente = (String) idActual;
        } else {
            idExpediente = UUID.randomUUID().toString();
            entrada.getProperties().put(PROPIEDAD_EXPEDIENTE, idExpediente);
            guardarEventos();
        }

        Stage ventanaExistente = ventanasExpediente.get(idExpediente);
        if (ventanaExistente != null) {
            ventanaExistente.show();
            ventanaExistente.toFront();
            ventanaExistente.requestFocus();
            return;
        }

        try {
            FXMLLoader cargador = new FXMLLoader(
                    CalendarApp.class.getResource("/com/eia/documentos/temp-view.fxml"));
            Parent contenido = cargador.load();
            com.eia.documentos.TempController controlador = cargador.getController();
            controlador.cargarExpediente(idExpediente);
            Stage demo = new Stage();
            demo.initOwner(ventana);
            aplicarIcono(demo);
            demo.initModality(Modality.NONE);
            demo.setAlwaysOnTop(true);
            controlador.vincularTituloVentana(demo);
            demo.getProperties().put("tempController", controlador);
            demo.setScene(new Scene(contenido));
            dimensionarYCentrar(demo, ventana, 920, 520);
            demo.setOnHidden(evento -> {
                ventanasExpediente.remove(idExpediente, demo);
            });
            ventanasExpediente.put(idExpediente, demo);
            demo.show();
        } catch (IOException ex) {
            Alert alerta = new Alert(Alert.AlertType.ERROR);
            alerta.setTitle("No se pudo abrir Demo");
            alerta.setHeaderText(null);
            alerta.setContentText("No se pudo cargar la interfaz Demo: " + ex.getMessage());
            alerta.showAndWait();
        }
    }

    /**
     * Configura el menú contextual del evento:
     * - Quita la opción que permite mover el evento de industria ("Calendario").
     * - Cambia el nombre de la opción "Información" a "Auditoria".
     * - Muestra un cuadro de confirmación antes de eliminar el evento.
     */
    private void configurarMenuContextualEvento(CalendarView calendarView) {
        javafx.util.Callback<DateControl.EntryContextMenuParameter, ContextMenu> callbackOriginal =
                calendarView.getEntryContextMenuCallback();
        calendarView.setEntryContextMenuCallback(param -> {
            ContextMenu menu = callbackOriginal.call(param);
            if (menu != null) {
                menu.getItems().removeIf(item -> item instanceof Menu
                        && "Calendario".equalsIgnoreCase(item.getText()));
                for (MenuItem item : menu.getItems()) {
                    if (item != null && item.getText() != null) {
                        if (item.getText().equalsIgnoreCase("Información")
                                || item.getText().equalsIgnoreCase("Informacion")
                                || item.getText().equalsIgnoreCase("Information")
                                || item.getText().equalsIgnoreCase(Messages.getString("DateControl.MENU_ITEM_INFORMATION"))) {
                            item.setText("Auditoria");
                        } else if (item.getText().equalsIgnoreCase("Eliminar")
                                || item.getText().equalsIgnoreCase("Delete")
                                || item.getText().equalsIgnoreCase(Messages.getString("DateControl.MENU_ITEM_DELETE"))) {
                            javafx.event.EventHandler<javafx.event.ActionEvent> accionOriginal = item.getOnAction();
                            item.setOnAction(evt -> {
                                Entry<?> entrada = param.getEntryView() != null ? param.getEntryView().getEntry() : null;
                                String titulo = entrada != null ? entrada.getTitle() : null;
                                String mensaje = (titulo != null && !titulo.isBlank())
                                        ? "¿Está seguro de que desea eliminar el evento \"" + titulo + "\"?"
                                        : "¿Está seguro de que desea eliminar este evento?";

                                Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.OK, ButtonType.CANCEL);
                                confirmar.setTitle("Eliminar evento");
                                confirmar.setHeaderText(null);
                                if (ventana != null) {
                                    confirmar.initOwner(ventana);
                                }
                                confirmar.showAndWait()
                                        .filter(b -> b == ButtonType.OK)
                                        .ifPresent(b -> {
                                            if (accionOriginal != null) {
                                                accionOriginal.handle(evt);
                                            } else if (entrada != null) {
                                                Calendar cal = entrada.getCalendar();
                                                if (cal != null && !cal.isReadOnly()) {
                                                    if (entrada.isRecurrence()) {
                                                        Entry<?> rec = entrada.getRecurrenceSourceEntry();
                                                        if (rec != null) rec.removeFromCalendar();
                                                    } else {
                                                        entrada.removeFromCalendar();
                                                    }
                                                }
                                            }
                                        });
                            });
                        }
                    }
                }
            }
            return menu;
        });
    }

    private List<String> nombresActividades() {
        List<String> nombres = new ArrayList<>();
        for (Calendar actividad : actividades.getCalendars()) nombres.add(actividad.getName());
        return nombres;
    }

    private GridPane buscarGrillaDetalles(Node nodo) {
        if (nodo instanceof GridPane && nodo.getStyleClass().contains("content")) return (GridPane) nodo;
        if (nodo instanceof Parent) {
            for (Node hijo : ((Parent) nodo).getChildrenUnmodifiable()) {
                GridPane resultado = buscarGrillaDetalles(hijo);
                if (resultado != null) return resultado;
            }
        }
        return null;
    }

    private void filtrarSelectorCalendarios(Node nodo) {
        if (nodo instanceof CalendarSelector) {
            ((CalendarSelector) nodo).getCalendars().setAll(industrias.getCalendars());
            return;
        }
        if (nodo instanceof Parent) {
            for (Node hijo : ((Parent) nodo).getChildrenUnmodifiable()) filtrarSelectorCalendarios(hijo);
        }
    }

    /** En la vista anual usa el color de la primera industria visible con eventos ese día. */
    private void configurarColoresAnuales(CalendarView calendarView) {
        for (Month mes : Month.values()) {
            YearMonthView vistaMes = calendarView.getYearPage().getYearView().getMonthView(mes);
            vistaMes.setShowUsageColors(false);
            vistaMes.setCellFactory(view -> new YearMonthView.DateCell() {
                @Override
                protected void update(LocalDate fecha) {
                    super.update(fecha);
                    setStyle("");

                    if (view.isShowToday() && fecha.equals(view.getToday())) return;

                    for (Calendar calendario : view.getCalendars()) {
                        if (view.isCalendarVisible(calendario)
                                && calendario.findEntries(fecha, fecha, view.getZoneId()).containsKey(fecha)) {
                            setStyle("-fx-background-color: derive(-" + calendario.getStyle() + "-color, 70%);");
                            return;
                        }
                    }
                }
            });
        }
    }

    // ---------- Persistencia ----------

    private void cargarIndustrias() {
        for (String linea : leer(ARCHIVO_INDUSTRIAS)) {
            if (!linea.isBlank()) agregarCalendario(industrias, linea.trim());
        }
    }

    private void cargarActividades() {
        for (String linea : leer(ARCHIVO_ACTIVIDADES)) {
            if (!linea.isBlank()) agregarCalendario(actividades, linea.trim());
        }
    }

    private void guardarIndustrias() {
        guardarGrupo(industrias);
    }

    private void guardarGrupo(CalendarSource grupo) {
        List<String> nombres = new ArrayList<>();
        for (Calendar c : grupo.getCalendars()) nombres.add(c.getName());
        escribir(grupo == actividades ? ARCHIVO_ACTIVIDADES : ARCHIVO_INDUSTRIAS, nombres);
    }

    /** Se llama ante cualquier cambio en los eventos de una industria. */
    private void alCambiar(CalendarEvent e) {
        Entry<?> entrada = e.getEntry();
        if (entrada != null && !entrada.isRecurrence()) {
            if (entrada.getCalendar() != null) entradas.add(entrada);
            else entradas.remove(entrada);
        }
        if (!cargando && datos != null) datos.refrescar();
        if (!cargando) guardadoPendiente.playFromStart();
    }

    private void guardarEventos() {
        entradas.removeIf(en -> en.getCalendar() == null);
        List<String> lineas = new ArrayList<>();
        for (Entry<?> en : entradas) {
            if (en.isRecurrence()) continue;
            Interval intervalo = en.getInterval();
            lineas.add(String.join("\t",
                    cod(claveCalendario(en.getCalendar())),
                    cod(en.getTitle()),
                    cod(en.getLocation()),
                    String.valueOf(en.isFullDay()),
                    intervalo.getStartDateTime().toString(),
                    intervalo.getEndDateTime().toString(),
                    intervalo.getZoneId().getId(),
                    cod(en.getRecurrenceRule()),
                    cod(String.valueOf(en.getProperties().getOrDefault(PROPIEDAD_ACTIVIDAD, ""))),
                    cod(String.valueOf(en.getProperties().getOrDefault(PROPIEDAD_EXPEDIENTE, "")))));
        }
        escribir(ARCHIVO_EVENTOS, lineas);
    }

    private void cargarEventos() {
        cargando = true;
        for (String linea : leer(ARCHIVO_EVENTOS)) {
            String[] f = linea.split("\t", -1);
            if (f.length < 8) continue;

            Calendar calendario = buscarCalendario(dec(f[0]));
            if (calendario == null) continue;

            try {
                Entry<Object> entrada = new Entry<>(dec(f[1]));
                entrada.setInterval(new Interval(
                        LocalDateTime.parse(f[4]), LocalDateTime.parse(f[5]), ZoneId.of(f[6])));
                entrada.setFullDay(Boolean.parseBoolean(f[3]));
                entrada.setLocation(dec(f[2]));
                String regla = dec(f[7]);
                if (!regla.isBlank()) entrada.setRecurrenceRule(regla);
                if (f.length >= 9 && !dec(f[8]).isBlank()) {
                    entrada.getProperties().put(PROPIEDAD_ACTIVIDAD, dec(f[8]));
                }
                if (f.length >= 10 && !dec(f[9]).isBlank()) {
                    entrada.getProperties().put(PROPIEDAD_EXPEDIENTE, dec(f[9]));
                }

                calendario.addEntry(entrada);
                entradas.add(entrada);
            } catch (RuntimeException ex) {
                ex.printStackTrace(); // línea dañada: se ignora y se sigue con las demás
            }
        }
        cargando = false;
    }

    // ---------- Formulario FormsFX ----------

    /** Archivo donde se guardan los valores del formulario de un expediente. */
    private static Path archivoFormulario(String idFormulario) {
        return CARPETA.resolve("expedientes").resolve(idFormulario).resolve("formulario.tsv");
    }

    /** Lee los valores guardados del formulario; si no existe, devuelve un mapa vacío. */
    private Map<String, String> cargarFormulario(String idFormulario) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (String linea : leer(archivoFormulario(idFormulario))) {
            String[] campos = linea.split("\t", -1);
            if (campos.length == 2) valores.put(dec(campos[0]), dec(campos[1]));
        }
        return valores;
    }

    /** Guarda los valores del formulario al pulsar Guardar (tras Form.persist()). */
    private void guardarFormulario(String idFormulario, Map<String, String> valores) {
        List<String> lineas = new ArrayList<>();
        valores.forEach((clave, valor) -> lineas.add(cod(clave) + "\t" + cod(valor)));
        escribir(archivoFormulario(idFormulario), lineas);
    }

    private String claveCalendario(Calendar calendario) {
        for (CalendarSource grupo : grupos) {
            if (grupo.getCalendars().contains(calendario)) return grupo.getName() + "::" + calendario.getName();
        }
        return calendario.getName();
    }

    private Calendar buscarCalendario(String clave) {
        int separador = clave.indexOf("::");
        if (separador >= 0) {
            String nombreGrupo = clave.substring(0, separador);
            String nombreCalendario = clave.substring(separador + 2);
            for (CalendarSource grupo : grupos) {
                if (!grupo.getName().equals(nombreGrupo)) continue;
                for (Calendar c : grupo.getCalendars()) if (c.getName().equals(nombreCalendario)) return c;
            }
            return null;
        }
        // Compatibilidad con eventos guardados antes de crear el grupo Actividades.
        for (Calendar c : industrias.getCalendars()) {
            if (c.getName().equals(clave)) return c;
        }
        return null;
    }

    private static String cod(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static List<String> leer(Path archivo) {
        try {
            return Files.exists(archivo) ? Files.readAllLines(archivo, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private static void escribir(Path archivo, List<String> lineas) {
        try {
            Almacenamiento.escribir(archivo, lineas);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------- Interfaz ----------

    /** Agrega los botones junto al botón de Categorías en la barra de CalendarFX. */
    private void ubicarBotones(BorderPane root, Button... botones) {
        Timeline buscador = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            insertarBotonesEnBarra(root, botones);
        }));
        buscador.setCycleCount(40);
        buscador.setOnFinished(evento -> {
            if (botones[0].getParent() == null && botones[1].getParent() == null) {
                HBox barraAlternativa = new HBox(8, botones);
                barraAlternativa.setStyle("-fx-padding: 8; -fx-alignment: center-left;");
                root.setTop(barraAlternativa);
            }
        });
        buscador.play();
    }

    private void insertarBotonesEnBarra(BorderPane root, Button... botones) {
        Node botonCategorias = root.lookup("#show-tray-button");
        if (botonCategorias == null || !(botonCategorias.getParent() instanceof HBox)) return;

        if (botonCategorias instanceof ToggleButton) {
            ((ToggleButton) botonCategorias).setText("Categorías");
        }

        HBox barra = (HBox) botonCategorias.getParent();
        for (int i = 0; i < botones.length; i++) {
            if (!barra.getChildren().contains(botones[i])) {
                barra.getChildren().add(Math.min(3 + i, barra.getChildren().size()), botones[i]);
            }
        }
    }

    private void iniciarReloj(CalendarView calendarView) {
        Runnable actualizar = () -> {
            calendarView.setToday(LocalDate.now(ZONA_ARGENTINA));
            calendarView.setTime(LocalTime.now(ZONA_ARGENTINA));
        };
        actualizar.run();
        calendarView.setRequestedTime(LocalTime.now(ZONA_ARGENTINA));

        Timeline reloj = new Timeline(new KeyFrame(Duration.seconds(30), e -> actualizar.run()));
        reloj.setCycleCount(Timeline.INDEFINITE);
        reloj.play();
    }

    public static void main(String[] args) {
        Locale argentina = Locale.forLanguageTag("es-AR");
        Locale.setDefault(argentina);
        Locale.setDefault(Locale.Category.DISPLAY, argentina);
        Locale.setDefault(Locale.Category.FORMAT, argentina);
        launch(args);
    }
}
