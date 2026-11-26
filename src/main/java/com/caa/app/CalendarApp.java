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
package com.caa.app;

import com.calendarfx.model.Calendar;
import com.calendarfx.model.Calendar.Style;
import com.calendarfx.model.CalendarEvent;
import com.calendarfx.model.CalendarSource;
import com.calendarfx.model.Entry;
import com.calendarfx.view.CalendarView;
import com.calendarfx.view.CalendarSelector;
import com.calendarfx.view.YearMonthView;
import com.calendarfx.view.DateControl;
import com.calendarfx.view.VirtualGrid;
import com.calendarfx.view.Messages;
import com.calendarfx.model.Interval;
import com.caa.app.FormularioModel;
import com.caa.app.FormularioPane;
import fr.brouillard.oss.cssfx.CSSFX;
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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.Parent;
import javafx.stage.FileChooser;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Properties;
import java.io.File;
import java.time.format.DateTimeFormatter;

public class CalendarApp extends Application {

    static final String PROPIEDAD_ACTIVIDAD = "actividad";
    private static final String PROPIEDAD_EXPEDIENTE = "expedienteId";
    private static final String PROPIEDAD_NOTIFICACION = "notificacionId";
    private static final String PROPIEDAD_LISTENER_TITULO_AUDITORIA = "listenerTituloAuditoria";
    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Cordoba");
    private static final Path CARPETA = Almacenamiento.carpetaDatos();
    private static final Path ARCHIVO_INDUSTRIAS = CARPETA.resolve("industrias.txt");
    private static final Path ARCHIVO_ACTIVIDADES = CARPETA.resolve("actividades.txt");
    private static final Path ARCHIVO_EVENTOS = CARPETA.resolve("eventos.txt");
    private static final Path ARCHIVO_NOTIFICACIONES = CARPETA.resolve("notificaciones.txt");

    private final CalendarSource industrias = new CalendarSource("Industrias");
    private final CalendarSource actividades = new CalendarSource("Actividad");
    private final List<CalendarSource> grupos = List.of(industrias, actividades);
    private final Map<Calendar, Style> estilos = new HashMap<>();
    private final Set<Entry<?>> entradas = new LinkedHashSet<>();
    private final List<Notificacion> notificaciones = new ArrayList<>();
    // Calendario que nunca se muestra: evita que CalendarFX abra su propio aviso
    // ("no se puede crear el evento") cuando todavía no hay industrias.
    private final Calendar calendarioAuxiliar = new Calendar("Sin industria");
    private final PauseTransition guardadoPendiente = new PauseTransition(Duration.millis(500));
    private Datos datos;
    private boolean cargando = false;
    private Stage ventana;
    private Stage ventanaFormsFx;
    private String formularioFormsFxId;
    private final Map<String, Stage> ventanasExpediente = new HashMap<>();
    private BotonNotificacion botonNotificaciones;
    private boolean hayNotificaciones = false;
    private Timeline controlNotificaciones;
    private Entry<?> eventoCopiado;
    private Calendar calendarioEventoCopiado;

    @Override
    public void start(Stage stage) {
        // CalendarFX resuelve textos y nombres de días al crear sus controles.
        // Fijar el locale aquí también cubre los lanzamientos directos de esta clase.
        fijarLocaleArgentina();
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
        cargarNotificaciones();
        // La fuente vacía mantiene espacio entre los grupos en el desplegable.
        calendarView.getCalendarSources().setAll(industrias, new CalendarSource(" "), actividades);
        instalarSelectorSoloIndustrias(calendarView);
        instalarCreacionDeEventos(calendarView);
        configurarMenuContextualEvento(calendarView);

        iniciarReloj(calendarView);

        BorderPane root = new BorderPane(calendarView);
        Scene scene = new Scene(root);
        CSSFX.start(scene);

        stage.setTitle("CAA");
        aplicarIcono(stage);
        stage.setScene(scene);
        dimensionarYCentrar(stage, null, 1280, 720);
        stage.show();

        botonNotificaciones = new BotonNotificacion();
        botonNotificaciones.setOnAction(evento -> abrirNotificacionesPendientes());
        datos = new Datos(calendarView, grupos, actividades, entradas);
        datos.setAccesorioFinal(botonNotificaciones);
        datos.instalar(root);
        actualizarEstadoNotificaciones();
        iniciarControlNotificaciones();

        calendarView.selectedPageProperty().addListener((obs, anterior, pagina) -> {
            if (pagina == CalendarView.Page.YEAR) {
                Platform.runLater(() -> configurarColoresAnuales(calendarView));
            }
        });
        if (calendarView.getSelectedPage() == CalendarView.Page.YEAR) {
            Platform.runLater(() -> configurarColoresAnuales(calendarView));
        }

        configurarBotonImportar(calendarView.getDayPage().getToolBarControls());
        configurarBotonImportar(calendarView.getWeekPage().getToolBarControls());
        Button[] botones = {crearBotonAgregar(), crearBotonEliminar()};
        calendarView.selectedPageProperty().addListener((obs, anterior, actual) ->
                Platform.runLater(() -> insertarBotonesEnBarra(root, botones)));
        ubicarBotones(root, botones);
    }

    private static void aplicarIcono(Stage stage) {
        stage.getIcons().add(new Image(
                CalendarApp.class.getResource("/com/caa/app/icon.png").toExternalForm()));
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
        if (controlNotificaciones != null) controlNotificaciones.stop();
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
        for (Entry<?> entrada : new ArrayList<>(entradas)) {
            if (entrada.getCalendar() == calendario) eliminarNotificacionDe(entrada);
        }
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
            List<String> nombresGrupos = List.of("Industria", "Actividad");
            ChoiceDialog<String> elegirGrupo = new ChoiceDialog<>(nombresGrupos.get(0), nombresGrupos);
            elegirGrupo.setTitle("Agregar");
            elegirGrupo.setHeaderText("¿Querés agregar una industria o una actividad?");
            elegirGrupo.setContentText("Tipo:");
            elegirGrupo.initOwner(ventana);
            elegirGrupo.showAndWait().ifPresent(nombreGrupo -> {
                boolean esActividad = "Actividad".equals(nombreGrupo);
                String tipo = esActividad ? "actividad" : "industria";

                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Nueva " + tipo);
                dialog.setHeaderText(null);
                dialog.setContentText("Nombre de la " + tipo + ":");
                dialog.initOwner(ventana);

                dialog.showAndWait()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .ifPresent(nombre -> {
                            CalendarSource grupo = esActividad ? actividades : industrias;
                            agregarCalendario(grupo, nombre);
                            guardarGrupo(grupo);
                        });
            });
        });
        return boton;
    }

    private void configurarBotonImportar(Node controles) {
        ToggleButton boton = buscarBotonAgrupar(controles);
        if (boton == null) return;
        boolean seleccionOriginal = boton.isSelected();
        boton.setTooltip(new Tooltip("Cargar datos o restablecer la aplicación"));
        boton.setAccessibleText("Cargar datos o restablecer la aplicación");
        boton.setOnAction(evento -> {
            boton.setSelected(seleccionOriginal);
            javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem cargar = new javafx.scene.control.MenuItem("Cargar datos desde Excel");
            cargar.setOnAction(accion -> importarExcel());
            javafx.scene.control.MenuItem reiniciar = new javafx.scene.control.MenuItem("Eliminar todos los datos");
            reiniciar.setOnAction(accion -> confirmarRestablecimiento());
            menu.getItems().setAll(cargar, reiniciar);
            menu.show(boton, javafx.geometry.Side.BOTTOM, 0, 0);
        });
    }

    private static ToggleButton buscarBotonAgrupar(Node nodo) {
        if (nodo == null) return null;
        if ("layout-button".equals(nodo.getId()) && nodo instanceof ToggleButton) {
            return (ToggleButton) nodo;
        }
        if (nodo instanceof Parent) {
            for (Node hijo : ((Parent) nodo).getChildrenUnmodifiable()) {
                ToggleButton encontrado = buscarBotonAgrupar(hijo);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private void importarExcel() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Cargar datos desde Excel");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Libro de Excel (.xlsx)", "*.xlsx"));
        File archivo = selector.showOpenDialog(ventana);
        if (archivo == null) return;

        ImportadorExcel.Resultado resultado;
        try {
            resultado = ImportadorExcel.leer(archivo.toPath());
        } catch (IOException | RuntimeException error) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo leer el Excel", error.getMessage());
            return;
        }
        if (resultado.registros.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Excel sin registros",
                    "No encontré filas con una empresa para importar.");
            return;
        }

        Set<String> industriasExistentes = nombresNormalizados(industrias);
        Set<String> actividadesExistentes = nombresNormalizados(actividades);
        Set<String> industriasNuevas = new HashSet<>();
        Set<String> actividadesNuevas = new HashSet<>();
        Set<String> eventosExistentes = clavesEventos();
        int eventosNuevos = 0;
        int fechasValidas = 0;
        for (ImportadorExcel.Registro registro : resultado.registros) {
            String empresa = ImportadorExcel.normalizar(registro.empresa);
            if (!empresa.isEmpty() && !industriasExistentes.contains(empresa)) industriasNuevas.add(empresa);
            String actividad = ImportadorExcel.normalizar(registro.actividad);
            if (!actividad.isEmpty() && !actividadesExistentes.contains(actividad)) actividadesNuevas.add(actividad);
            for (ImportadorExcel.Fecha fecha : registro.fechas) {
                fechasValidas++;
                String titulo = tituloEventoImportado(registro, fecha);
                String idExpediente = idExpedienteImportado(registro, archivo.toPath(), resultado.hoja);
                String clave = claveEvento(empresa, titulo, fecha.fecha, idExpediente);
                if (eventosExistentes.add(clave)) eventosNuevos++;
            }
        }

        String resumen = "Hoja: " + resultado.hoja
                + "\nFilas de empresas: " + resultado.registros.size()
                + "\nEmpresas nuevas: " + industriasNuevas.size()
                + "\nActividades nuevas: " + actividadesNuevas.size()
                + "\nEventos nuevos: " + eventosNuevos + " (" + fechasValidas + " fechas válidas)"
                + (resultado.fechasInvalidas == 0 ? "" : "\nFechas que se omitirán por formato inválido: " + resultado.fechasInvalidas)
                + "\n\nFormato de fechas aceptado: dd/MM/aaaa (ej. 25/11/2026), aaaa-MM-dd o dd/MM/aa."
                + "\nTambién se aceptan fechas reconocidas por Excel. El encabezado de la columna debe incluir la palabra FECHA."
                + "\n\nLos eventos se agendarán a las 10:00 y durarán una hora. ¿Continuar?";
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, resumen, ButtonType.OK, ButtonType.CANCEL);
        confirmar.setTitle("Confirmar importación");
        confirmar.setHeaderText(null);
        confirmar.initOwner(ventana);
        if (confirmar.showAndWait().filter(tipo -> tipo == ButtonType.OK).isEmpty()) return;

        aplicarImportacion(resultado, archivo.toPath());
    }

    private void confirmarRestablecimiento() {
        ButtonType borrarTodo = new ButtonType("Eliminar todo", ButtonBar.ButtonData.OK_DONE);
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se eliminarán todas las empresas, actividades, eventos, expedientes, archivos adjuntos "
                        + "y notificaciones guardados en la aplicación. Esta acción no se puede deshacer.",
                borrarTodo, ButtonType.CANCEL);
        confirmar.setTitle("Restablecer datos");
        confirmar.setHeaderText("¿Volver la aplicación a cero?");
        confirmar.initOwner(ventana);
        if (confirmar.showAndWait().filter(tipo -> tipo == borrarTodo).isPresent()) {
            restablecerDatos();
        }
    }

    private void restablecerDatos() {
        guardadoPendiente.stop();
        if (ventanaFormsFx != null) {
            ventanaFormsFx.close();
            ventanaFormsFx = null;
            formularioFormsFxId = null;
        }
        for (Stage etapa : new ArrayList<>(ventanasExpediente.values())) etapa.close();
        ventanasExpediente.clear();

        try {
            eliminarArbol(CARPETA.resolve("expedientes"));
            eliminarArbol(CARPETA.resolve("notificaciones"));
            Files.deleteIfExists(ARCHIVO_INDUSTRIAS);
            Files.deleteIfExists(ARCHIVO_ACTIVIDADES);
            Files.deleteIfExists(ARCHIVO_EVENTOS);
            Files.deleteIfExists(ARCHIVO_NOTIFICACIONES);
        } catch (IOException error) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudieron eliminar todos los datos",
                    "La aplicación pudo quedar parcialmente restablecida.\n" + error.getMessage());
            return;
        }

        cargando = true;
        for (CalendarSource grupo : grupos) grupo.getCalendars().clear();
        entradas.clear();
        notificaciones.clear();
        estilos.clear();
        eventoCopiado = null;
        calendarioEventoCopiado = null;
        cargando = false;
        if (datos != null) datos.refrescar();
        actualizarEstadoNotificaciones();
        mostrarAlerta(Alert.AlertType.INFORMATION, "Aplicación restablecida",
                "Se eliminaron las empresas, actividades, eventos, expedientes, adjuntos y notificaciones.");
    }

    private static void eliminarArbol(Path carpeta) throws IOException {
        if (Files.notExists(carpeta)) return;
        Path[] rutasOrdenadas;
        try (var rutas = Files.walk(carpeta)) {
            rutasOrdenadas = rutas.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
        }
        for (Path ruta : rutasOrdenadas) Files.deleteIfExists(ruta);
    }

    private void aplicarImportacion(ImportadorExcel.Resultado resultado, Path archivo) {
        Map<String, Calendar> calendariosIndustrias = indiceCalendarios(industrias);
        Map<String, Calendar> calendariosActividades = indiceCalendarios(actividades);
        Set<String> clavesEventos = clavesEventos();
        int empresasNuevas = 0;
        int actividadesNuevas = 0;
        int eventosNuevos = 0;
        boolean estabaCargando = cargando;
        cargando = true;
        try {
            for (ImportadorExcel.Registro registro : resultado.registros) {
                String claveEmpresa = ImportadorExcel.normalizar(registro.empresa);
                Calendar calendarioEmpresa = calendariosIndustrias.get(claveEmpresa);
                if (calendarioEmpresa == null) {
                    agregarCalendario(industrias, registro.empresa.trim());
                    calendarioEmpresa = industrias.getCalendars().get(industrias.getCalendars().size() - 1);
                    calendariosIndustrias.put(claveEmpresa, calendarioEmpresa);
                    empresasNuevas++;
                }

                String actividad = registro.actividad.trim();
                String claveActividad = ImportadorExcel.normalizar(actividad);
                if (!actividad.isEmpty() && !calendariosActividades.containsKey(claveActividad)) {
                    agregarCalendario(actividades, actividad);
                    Calendar nuevo = actividades.getCalendars().get(actividades.getCalendars().size() - 1);
                    calendariosActividades.put(claveActividad, nuevo);
                    actividadesNuevas++;
                }

                if (registro.fechas.isEmpty()) continue;
                String idExpediente = idExpedienteImportado(registro, archivo, resultado.hoja);
                Map<String, String> formulario = cargarFormulario(idExpediente);
                boolean cambioFormulario = false;
                for (Map.Entry<String, String> valor : formularioImportado(registro).entrySet()) {
                    String anterior = formulario.get(valor.getKey());
                    if ((anterior == null || anterior.isBlank()) && !valor.getValue().isBlank()) {
                        formulario.put(valor.getKey(), valor.getValue());
                        cambioFormulario = true;
                    }
                }
                if (cambioFormulario) guardarFormulario(idExpediente, formulario);

                for (ImportadorExcel.Fecha fecha : registro.fechas) {
                    String titulo = tituloEventoImportado(registro, fecha);
                    String clave = claveEvento(claveEmpresa, titulo, fecha.fecha, idExpediente);
                    if (!clavesEventos.add(clave)) continue;
                    Entry<Object> entrada = new Entry<>(titulo);
                    LocalDateTime inicio = LocalDateTime.of(fecha.fecha, LocalTime.of(10, 0));
                    entrada.setInterval(new Interval(inicio, inicio.plusHours(1), ZONA_ARGENTINA));
                    entrada.setLocation(registro.domicilio);
                    entrada.getProperties().put(PROPIEDAD_EXPEDIENTE, idExpediente);
                    if (!actividad.isEmpty()) entrada.getProperties().put(PROPIEDAD_ACTIVIDAD, actividad);
                    calendarioEmpresa.addEntry(entrada);
                    entradas.add(entrada);
                    eventosNuevos++;
                }
            }
        } finally {
            cargando = estabaCargando;
        }
        guardarIndustrias();
        guardarGrupo(actividades);
        guardarEventos();
        if (datos != null) datos.refrescar();
        actualizarEstadoNotificaciones();
        mostrarAlerta(Alert.AlertType.INFORMATION, "Importación completada",
                "Empresas agregadas: " + empresasNuevas
                        + "\nActividades agregadas: " + actividadesNuevas
                        + "\nEventos agendados: " + eventosNuevos
                        + "\n\nLos expedientes asociados a eventos se pueden abrir desde el evento del calendario.");
    }

    private Map<String, String> formularioImportado(ImportadorExcel.Registro registro) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("company", registro.empresa.trim());
        valores.put("fileNumber", registro.expediente.trim());
        valores.put("email", registro.email.trim());
        valores.put("actividad", registro.actividad.trim());
        if (!registro.consultor.isBlank()) valores.put("auditoriaConsultor1", registro.consultor);
        if (!registro.descripcion.isBlank() || !registro.domicilio.isBlank()) {
            String observaciones = registro.descripcion;
            if (!registro.domicilio.isBlank()) {
                if (!observaciones.isBlank()) observaciones += "\n";
                observaciones += registro.domicilio;
            }
            valores.put("observations", observaciones);
        }
        int auditoria = 0;
        int notificacion = 0;
        for (ImportadorExcel.Fecha fecha : registro.fechas) {
            String tipo = ImportadorExcel.normalizar(fecha.etiqueta);
            String mostrado = fecha.fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (tipo.contains("NOTIFIC")) {
                notificacion++;
                valores.put("notificacion" + notificacion, fecha.etiqueta + ": " + mostrado);
            } else {
                auditoria++;
                valores.put("auditoriaFecha" + auditoria, mostrado);
            }
        }
        int resolucion = 0;
        for (ImportadorExcel.Resolucion item : registro.resoluciones) {
            resolucion++;
            valores.put("resolucionAnio" + resolucion, String.valueOf(item.anio));
            valores.put("resolucion" + resolucion, item.texto);
        }
        return valores;
    }

    private static String idExpedienteImportado(ImportadorExcel.Registro registro, Path archivo, String hoja) {
        String numero = registro.expediente.isBlank()
                ? archivo.getFileName() + "|" + hoja + "|" + registro.fila
                : ImportadorExcel.normalizar(registro.expediente);
        String llave = ImportadorExcel.normalizar(registro.empresa) + "|" + numero;
        return UUID.nameUUIDFromBytes(llave.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String tituloEventoImportado(ImportadorExcel.Registro registro, ImportadorExcel.Fecha fecha) {
        String tipo = ImportadorExcel.normalizar(fecha.etiqueta);
        String nombre = tipo.contains("NOTIFIC") ? "Notificación CAA"
                : tipo.contains("AUDITOR") ? "Auditoría" : fecha.etiqueta.trim();
        return nombre + " — " + registro.empresa.trim();
    }

    private Set<String> nombresNormalizados(CalendarSource grupo) {
        Set<String> resultado = new HashSet<>();
        for (Calendar calendario : grupo.getCalendars()) {
            resultado.add(ImportadorExcel.normalizar(calendario.getName()));
        }
        return resultado;
    }

    private Map<String, Calendar> indiceCalendarios(CalendarSource grupo) {
        Map<String, Calendar> resultado = new LinkedHashMap<>();
        for (Calendar calendario : grupo.getCalendars()) {
            resultado.putIfAbsent(ImportadorExcel.normalizar(calendario.getName()), calendario);
        }
        return resultado;
    }

    private Set<String> clavesEventos() {
        Set<String> resultado = new HashSet<>();
        for (Entry<?> entrada : entradas) {
            if (entrada.getCalendar() == null || entrada.isRecurrence()) continue;
            resultado.add(claveEvento(ImportadorExcel.normalizar(entrada.getCalendar().getName()),
                    entrada.getTitle(), entrada.getInterval().getStartDateTime().toLocalDate(),
                    String.valueOf(entrada.getProperties().getOrDefault(PROPIEDAD_EXPEDIENTE, ""))));
        }
        return resultado;
    }

    private static String claveEvento(String empresa, String titulo, LocalDate fecha, String idExpediente) {
        return empresa + "|" + ImportadorExcel.normalizar(titulo) + "|" + fecha + "|" + idExpediente;
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
            elegir.setTitle("Eliminar industria o actividad");
            elegir.setHeaderText(null);
            elegir.setContentText("Industria o actividad:");
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

    private void abrirFormsFxDemo(Entry<?> entrada, Window owner,
                                  com.caa.documentos.TempController controladorExpediente) {
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
            controladorExpediente.generarInformeFormulario(valores);
        }, controladorExpediente::generarInformeFormulario);

        Stage demo = new Stage();
        demo.initOwner(owner);
        aplicarIcono(demo);
        demo.setTitle("Formulario de auditoría");
        demo.setScene(new Scene(formulario));
        demo.setAlwaysOnTop(true);
        dimensionarYCentrar(demo, owner, 1120, 660);
        demo.setOnHidden(evento -> {
            if (idFormulario.equals(formularioFormsFxId)) {
                ventanaFormsFx = null;
                formularioFormsFxId = null;
            }
        });

        ventanaFormsFx = demo;
        formularioFormsFxId = idFormulario;
        demo.show();
        demo.toFront();
        demo.requestFocus();

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

        if (!entrada.getTitle().startsWith("Notificación CAA")) {
            LocalDate fecha = entrada.getInterval().getStartDateTime().toLocalDate();
            ponerSiVacio(valores, "auditoriaFecha1", String.format("%02d/%02d/%04d",
                    fecha.getDayOfMonth(), fecha.getMonthValue(), fecha.getYear()));
        }
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
                    prepararTituloAuditoria(panel.getEntry());
                    reemplazarControlTodoElDia(panel);
                    agregarBotonExpedienteEnDetalles(panel);
                    reemplazarUbicacionPorNotificacion(panel);
                }
            });
            return contenido;
        });
    }

    /**
     * Evita el aviso propio de CalendarFX (que se muestra con {@code show()}, no modal,
     * y puede quedar detrás o apilarse) cuando se intenta crear un evento sin industrias.
     * Si hay industrias, replica la creación de evento por defecto.
     */
    private void instalarCreacionDeEventos(CalendarView calendarView) {
        Callback<DateControl, Calendar> proveedor = control ->
                industrias.getCalendars().isEmpty()
                        ? calendarioAuxiliar
                        : industrias.getCalendars().get(0);

        Callback<DateControl.CreateEntryParameter, Entry<?>> fabrica = param -> {
            if (industrias.getCalendars().isEmpty()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Sin industrias",
                        "Primero creá una industria para poder crear eventos.");
                return null;
            }
            return crearEventoPorDefecto(param);
        };

        for (DateControl pagina : List.of(calendarView.getDayPage(), calendarView.getWeekPage(),
                calendarView.getMonthPage(), calendarView.getYearPage())) {
            pagina.setDefaultCalendarProvider(proveedor);
            pagina.setEntryFactory(fabrica);
        }
    }

    /** Misma lógica que el entry factory por defecto de CalendarFX. */
    private static Entry<?> crearEventoPorDefecto(DateControl.CreateEntryParameter param) {
        DateControl control = param.getDateControl();
        ZonedDateTime tiempo = param.getZonedDateTime();
        VirtualGrid grid = control.getVirtualGrid();
        if (grid != null) {
            java.time.DayOfWeek primerDia = control.getFirstDayOfWeek();
            ZonedDateTime inferior = grid.adjustTime(tiempo, false, primerDia);
            ZonedDateTime superior = grid.adjustTime(tiempo, true, primerDia);
            if (java.time.Duration.between(tiempo, inferior).abs()
                    .minus(java.time.Duration.between(tiempo, superior).abs()).isNegative()) {
                tiempo = inferior;
            } else {
                tiempo = superior;
            }
        }

        Calendar calendario = param.getCalendar();
        String titulo = calendario == null
                ? Messages.getString("DateControl.DEFAULT_ENTRY_TITLE")
                : tituloAuditoria(calendario);
        Entry<Object> entrada = new Entry<>(titulo);
        entrada.setInterval(new Interval(tiempo.toLocalDateTime(),
                tiempo.toLocalDateTime().plusHours(1), tiempo.getZone()));
        return entrada;
    }

    /** Reemplaza la fila Todo el día por el intervalo desde la auditoría anterior. */
    private void reemplazarControlTodoElDia(
            com.calendarfx.view.popover.EntryPopOverContentPane panel) {
        if (panel.getPanes().isEmpty()) return;
        Node contenido = panel.getPanes().get(0).getContent();
        if (!(contenido instanceof Parent)) return;

        GridPane grilla = buscarGrillaTodoElDia((Parent) contenido);
        if (grilla == null) return;

        javafx.scene.control.CheckBox casilla = null;
        javafx.scene.control.Label etiquetaTodoElDia = null;
        for (Node nodo : new ArrayList<>(grilla.getChildren())) {
            Integer fila = GridPane.getRowIndex(nodo);
            if (fila != null && fila != 0) continue;
            if (nodo instanceof javafx.scene.control.CheckBox) {
                casilla = (javafx.scene.control.CheckBox) nodo;
            } else if (nodo instanceof javafx.scene.control.Label) {
                etiquetaTodoElDia = (javafx.scene.control.Label) nodo;
            }
        }
        if (casilla == null || etiquetaTodoElDia == null) return;

        Entry<?> entrada = panel.getEntry();
        if (entrada.isFullDay()) entrada.setFullDay(false);
        grilla.getChildren().removeAll(casilla, etiquetaTodoElDia);

        javafx.scene.control.Label etiquetaDias = new javafx.scene.control.Label("Última Auditoría:");
        javafx.scene.control.Label valorDias = new javafx.scene.control.Label();
        HBox filaAuditoria = new HBox(8, etiquetaDias, valorDias);
        filaAuditoria.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Runnable actualizarDias = () -> valorDias.setText(textoUltimaAuditoria(entrada));
        actualizarDias.run();
        entrada.intervalProperty().addListener(observable -> actualizarDias.run());
        entrada.calendarProperty().addListener(observable -> actualizarDias.run());
        grilla.add(filaAuditoria, 0, 0, 2, 1);
    }

    private String textoUltimaAuditoria(Entry<?> entrada) {
        String dias = diasDesdeUltimaAuditoria(entrada, entradas);
        if ("Sin auditoría anterior".equals(dias)) return dias;
        try {
            long cantidad = Long.parseLong(dias);
            return "hace " + cantidad + (cantidad == 1 ? " día" : " días");
        } catch (NumberFormatException ignorada) {
            return dias;
        }
    }

    private GridPane buscarGrillaTodoElDia(Parent raiz) {
        if (raiz instanceof GridPane) {
            GridPane grilla = (GridPane) raiz;
            boolean tieneCasillaFilaInicial = grilla.getChildren().stream().anyMatch(nodo ->
                    nodo instanceof javafx.scene.control.CheckBox
                            && (GridPane.getRowIndex(nodo) == null || GridPane.getRowIndex(nodo) == 0));
            if (tieneCasillaFilaInicial) return grilla;
        }
        for (Node nodo : raiz.getChildrenUnmodifiable()) {
            if (nodo instanceof Parent) {
                GridPane encontrada = buscarGrillaTodoElDia((Parent) nodo);
                if (encontrada != null) return encontrada;
            }
        }
        return null;
    }

    /** Mantiene el título como "Auditoría + industria" mientras siga siendo automático. */
    private void prepararTituloAuditoria(Entry<?> entrada) {
        Calendar calendario = entrada.getCalendar();
        if (calendario == null) return;

        String tituloPredeterminado = Messages.getString("DateControl.DEFAULT_ENTRY_TITLE");
        boolean eraPredeterminado = entrada.getTitle() == null
                || entrada.getTitle().isBlank()
                || entrada.getTitle().equalsIgnoreCase(tituloPredeterminado)
                || entrada.getTitle().equalsIgnoreCase("Nuevo Evento");
        String tituloAutomaticoAnterior = "Auditoría " + calendario.getName();
        boolean eraTituloAutomaticoAnterior = entrada.getTitle() != null
                && entrada.getTitle().equalsIgnoreCase(tituloAutomaticoAnterior);
        boolean eraTituloConExpediente = false;
        Object id = entrada.getProperties().get(PROPIEDAD_EXPEDIENTE);
        if (id instanceof String && entrada.getTitle() != null
                && entrada.getTitle().startsWith("Auditoría — ")) {
            String numero = cargarFormulario((String) id).get("fileNumber");
            if (numero != null && !numero.isBlank()
                    && entrada.getTitle().equalsIgnoreCase("Auditoría — " + numero.trim())) {
                eraTituloConExpediente = true;
            }
        }
        if (eraPredeterminado || eraTituloAutomaticoAnterior || eraTituloConExpediente) {
            entrada.setTitle(tituloAuditoria(calendario));
        }

        if (Boolean.TRUE.equals(entrada.getProperties().get(PROPIEDAD_LISTENER_TITULO_AUDITORIA))) return;
        String tituloInicial = entrada.getTitle();
        boolean tituloAutomatico = eraPredeterminado || eraTituloAutomaticoAnterior || eraTituloConExpediente
                || tituloInicial.equals(tituloAuditoria(calendario));
        final String[] ultimoTituloAutomatico = {tituloAutomatico ? tituloInicial : null};
        entrada.getProperties().put(PROPIEDAD_LISTENER_TITULO_AUDITORIA, true);
        entrada.calendarProperty().addListener((observable, anterior, nuevo) -> {
            if (nuevo == null) return;
            String tituloActual = entrada.getTitle();
            if (ultimoTituloAutomatico[0] != null && tituloActual.equals(ultimoTituloAutomatico[0])) {
                ultimoTituloAutomatico[0] = tituloAuditoria(nuevo);
                entrada.setTitle(ultimoTituloAutomatico[0]);
            }
        });
    }

    private static String tituloAuditoria(Calendar calendario) {
        return "Auditoría — " + calendario.getName();
    }

    /** Aviso modal, con owner y siempre al frente, para que no quede detrás de la app. */
    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        if (ventana != null) {
            alerta.initOwner(ventana);
        }
        alerta.initModality(Modality.APPLICATION_MODAL);
        alerta.setOnShown(evento -> {
            Window ventanaAlerta = alerta.getDialogPane().getScene().getWindow();
            if (ventanaAlerta instanceof Stage) {
                Stage stageAlerta = (Stage) ventanaAlerta;
                stageAlerta.setAlwaysOnTop(true);
                stageAlerta.toFront();
                stageAlerta.requestFocus();
            }
        });
        alerta.showAndWait();
    }

    /**
     * Coloca el botón "+" (expediente) al final del renglón "Detalles".
     * Ese renglón es un TitledPane; para alinear el botón a la derecha se
     * reemplaza su título por un HBox que ocupa todo el ancho disponible.
     */
    private void agregarBotonExpedienteEnDetalles(
            com.calendarfx.view.popover.EntryPopOverContentPane panel) {
        if (panel.getPanes().isEmpty()) return;
        javafx.scene.control.TitledPane detalles = panel.getPanes().get(0);
        if (detalles.getGraphic() instanceof HBox) return; // ya agregado

        Button botonExpediente = new Button("+");
        botonExpediente.setAccessibleText("Expediente");
        botonExpediente.setTooltip(new javafx.scene.control.Tooltip("Expediente"));
        botonExpediente.setStyle("-fx-text-fill: #16833b; -fx-font-weight: bold;"
                + " -fx-font-size: 16px; -fx-padding: 0 7 0 7;");
        botonExpediente.setOnAction(evento -> abrirDemo(panel.getEntry()));

        javafx.scene.control.Label titulo = new javafx.scene.control.Label(detalles.getText());
        titulo.setStyle("-fx-font-weight: bold;");

        javafx.scene.layout.Region espacio = new javafx.scene.layout.Region();
        HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);

        HBox fila = new HBox(6, titulo, espacio, botonExpediente);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setMaxWidth(Double.MAX_VALUE);
        fila.prefWidthProperty().bind(
                javafx.beans.binding.Bindings.max(0, detalles.widthProperty().subtract(46)));
        // Evita que el clic en el botón también abra/cierre la sección "Detalles".
        fila.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, evento -> {
            Node objetivo = (Node) evento.getTarget();
            while (objetivo != null && objetivo != fila) {
                if (objetivo == botonExpediente) {
                    evento.consume();
                    return;
                }
                objetivo = objetivo.getParent();
            }
        });

        detalles.setText("");
        detalles.setGraphic(fila);
        detalles.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        // PopOverTitledPane cambia a TEXT_ONLY al expandir; lo forzamos de nuevo
        // para que el título con el botón siga visible.
        detalles.expandedProperty().addListener((obs, anterior, expandido) ->
                detalles.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY));
    }

    /**
     * Reemplaza el campo "Agregar Ubicación" de la cabecera del evento por un
     * botón "Agregar notificación".
     */
    private void reemplazarUbicacionPorNotificacion(
            com.calendarfx.view.popover.EntryPopOverContentPane panel) {
        Node cabecera = panel.getHeader();
        if (!(cabecera instanceof GridPane)) return;

        GridPane grilla = (GridPane) cabecera;
        for (Node nodo : new ArrayList<>(grilla.getChildren())) {
            if (!(nodo instanceof javafx.scene.control.TextField)) continue;
            if (!nodo.getStyleClass().contains("location")) continue;

            Integer columna = GridPane.getColumnIndex(nodo);
            Integer fila = GridPane.getRowIndex(nodo);
            grilla.getChildren().remove(nodo);

            org.kordamp.ikonli.javafx.FontIcon icono =
                    new org.kordamp.ikonli.javafx.FontIcon(
                            org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.CALENDAR_PLUS);
            icono.setIconSize(12);
            icono.setIconColor(javafx.scene.paint.Color.web("#2a7de1"));

            Button agregarNotificacion = new Button("Agregar notificación");
            agregarNotificacion.setGraphic(icono);
            agregarNotificacion.setStyle("-fx-background-color: transparent;"
                    + " -fx-text-fill: #2a7de1; -fx-padding: 2 0 2 0;"
                    + " -fx-cursor: hand; -fx-font-size: 12px;");
            agregarNotificacion.setMaxWidth(Double.MAX_VALUE);
            agregarNotificacion.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            agregarNotificacion.setTooltip(
                    new javafx.scene.control.Tooltip("Agregar notificación al evento"));

            Entry<?> base = entradaBase(panel.getEntry());
            Notificacion existente = buscarNotificacionDe(base);
            if (existente != null) {
                agregarNotificacion.setText("Notificación · vence "
                        + formatoFecha(existente.getFechaLimite()));
            }
            agregarNotificacion.setOnAction(evento -> abrirDialogoNotificacion(panel.getEntry()));

            grilla.add(agregarNotificacion,
                    columna == null ? 0 : columna, fila == null ? 1 : fila);
            return;
        }
    }

    /** Punto único para actualizar el estado de la campana cuando definas la lógica. */
    private void actualizarNotificaciones(boolean hay) {
        hayNotificaciones = hay;
        if (botonNotificaciones != null) botonNotificaciones.marcar(hay);
    }

    // ---------- Notificaciones ----------

    /** Abre el diálogo para adjuntar el Word y definir el plazo de la notificación. */
    private void abrirDialogoNotificacion(Entry<?> entrada) {
        Entry<?> base = entradaBase(entrada);
        if (base == null) return;
        Notificacion existente = buscarNotificacionDe(base);
        DialogoNotificacion dialogo = new DialogoNotificacion(
                ventana, existente, archivoNotificacion(existente));
        dialogo.showAndWait().ifPresent(resultado ->
                guardarResultadoNotificacion(base, existente, resultado));
    }

    private void guardarResultadoNotificacion(Entry<?> entrada, Notificacion existente,
                                              DialogoNotificacion.Resultado resultado) {
        try {
            Notificacion notificacion = existente != null ? existente : new Notificacion();
            if (resultado.isEliminarArchivo()) {
                notificacion.setArchivo("");
                borrarCarpetaNotificacion(notificacion.getId());
            } else if (resultado.getArchivo() != null) {
                notificacion.setArchivo(
                        guardarArchivoNotificacion(notificacion.getId(), resultado.getArchivo()));
            }
            notificacion.setFechaLimite(resultado.getFechaLimite());
            notificacion.setDescripcion(resultado.getDescripcion());
            notificacion.setTituloEvento(entrada.getTitle());
            notificacion.setFechaEvento(entrada.getInterval().getStartDateTime());
            notificacion.setAtendida(false);

            if (existente == null) {
                notificaciones.add(notificacion);
                entrada.getProperties().put(PROPIEDAD_NOTIFICACION, notificacion.getId());
            }

            guardarNotificaciones();
            guardarEventos();
            actualizarEstadoNotificaciones();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Notificación guardada",
                    "La campana se pondrá roja a partir del "
                            + formatoFecha(notificacion.getFechaLimite()) + ".");
        } catch (IOException ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo guardar la notificación",
                    "No se pudo copiar el documento:\n" + ex.getMessage());
        }
    }

    private Path archivoNotificacion(Notificacion notificacion) {
        if (notificacion == null || notificacion.getArchivo().isBlank()) return null;
        Path carpeta = CARPETA.resolve("notificaciones").resolve(notificacion.getId()).normalize();
        Path archivo = carpeta.resolve(notificacion.getArchivo()).normalize();
        return archivo.startsWith(carpeta) ? archivo : null;
    }

    /** Copia el Word elegido a la carpeta de datos y devuelve el nombre guardado. */
    private String guardarArchivoNotificacion(String id, Path origen) throws IOException {
        Path carpeta = CARPETA.resolve("notificaciones").resolve(id);
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(origen.getFileName().toString());
        Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        // Si el archivo anterior tenía otro nombre, lo quitamos.
        try (var archivos = Files.list(carpeta)) {
            for (Path anterior : archivos.toArray(Path[]::new)) {
                if (!anterior.equals(destino)) Files.deleteIfExists(anterior);
            }
        }
        return destino.getFileName().toString();
    }

    private void borrarCarpetaNotificacion(String id) {
        Path carpeta = CARPETA.resolve("notificaciones").resolve(id);
        if (!Files.exists(carpeta)) return;
        try (var archivos = Files.walk(carpeta)) {
            for (Path ruta : archivos.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /** Si el evento es una repetición, devuelve el evento original donde se guardan los datos. */
    private static Entry<?> entradaBase(Entry<?> entrada) {
        if (entrada == null) return null;
        if (entrada.isRecurrence()) {
            Entry<?> origen = entrada.getRecurrenceSourceEntry();
            if (origen != null) return origen;
        }
        return entrada;
    }

    private Notificacion buscarNotificacionDe(Entry<?> entrada) {
        if (entrada == null) return null;
        Object id = entrada.getProperties().get(PROPIEDAD_NOTIFICACION);
        if (!(id instanceof String)) return null;
        for (Notificacion notificacion : notificaciones) {
            if (notificacion.getId().equals(id)) return notificacion;
        }
        return null;
    }

    private Entry<?> buscarEntradaDeNotificacion(String id) {
        for (Entry<?> entrada : entradas) {
            if (id.equals(entrada.getProperties().get(PROPIEDAD_NOTIFICACION))) return entrada;
        }
        return null;
    }

    /** Elimina la notificación asociada a un evento (y su archivo). */
    private void eliminarNotificacionDe(Entry<?> entrada) {
        Object id = entrada.getProperties().get(PROPIEDAD_NOTIFICACION);
        if (!(id instanceof String)) return;
        boolean quitada = notificaciones.removeIf(notificacion -> {
            if (!notificacion.getId().equals(id)) return false;
            borrarCarpetaNotificacion(notificacion.getId());
            return true;
        });
        entrada.getProperties().remove(PROPIEDAD_NOTIFICACION);
        if (quitada) {
            guardarNotificaciones();
            actualizarEstadoNotificaciones();
        }
    }

    /** Revisa los vencimientos y pone la campana roja si hay alguna notificación vencida. */
    private void actualizarEstadoNotificaciones() {
        LocalDate hoy = LocalDate.now(ZONA_ARGENTINA);
        boolean hay = false;
        for (Notificacion notificacion : notificaciones) {
            if (notificacion.estaVencida(hoy)) {
                hay = true;
                break;
            }
        }
        actualizarNotificaciones(hay);
    }

    private void iniciarControlNotificaciones() {
        controlNotificaciones = new Timeline(
                new KeyFrame(Duration.seconds(60), evento -> actualizarEstadoNotificaciones()));
        controlNotificaciones.setCycleCount(Timeline.INDEFINITE);
        controlNotificaciones.play();
    }

    /**
     * Al presionar la campana muestra las notificaciones vencidas. Si hay una sola,
     * abre directamente el evento en la barra de búsqueda; si hay varias, deja elegir.
     */
    private void abrirNotificacionesPendientes() {
        LocalDate hoy = LocalDate.now(ZONA_ARGENTINA);
        List<Notificacion> pendientes = new ArrayList<>();
        for (Notificacion notificacion : notificaciones) {
            if (notificacion.estaVencida(hoy)) pendientes.add(notificacion);
        }
        pendientes.sort(Comparator.comparing(Notificacion::getFechaLimite,
                Comparator.nullsLast(Comparator.naturalOrder())));

        if (pendientes.isEmpty()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Notificaciones",
                    "No hay notificaciones vencidas.");
            return;
        }

        if (pendientes.size() == 1) {
            mostrarEventoDeNotificacion(pendientes.get(0));
            return;
        }

        List<String> opciones = new ArrayList<>();
        for (Notificacion notificacion : pendientes) {
            opciones.add(descripcionNotificacion(notificacion));
        }

        ChoiceDialog<String> elegir = new ChoiceDialog<>(opciones.get(0), opciones);
        elegir.setTitle("Notificaciones vencidas");
        elegir.setHeaderText("Elegí la notificación que querés ver:");
        elegir.setContentText("Notificación:");
        if (ventana != null) elegir.initOwner(ventana);
        elegir.showAndWait().ifPresent(opcion -> {
            int indice = opciones.indexOf(opcion);
            if (indice >= 0) mostrarEventoDeNotificacion(pendientes.get(indice));
        });
    }

    private void mostrarEventoDeNotificacion(Notificacion notificacion) {
        Entry<?> entrada = buscarEntradaDeNotificacion(notificacion.getId());
        if (entrada == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Notificación",
                    "El evento asociado a esta notificación ya no existe.");
            return;
        }
        if (datos != null) datos.mostrarEvento(entrada);
    }

    private static String descripcionNotificacion(Notificacion notificacion) {
        String titulo = notificacion.getTituloEvento();
        if (titulo == null || titulo.isBlank()) titulo = "(sin título)";
        return titulo + " — vence " + formatoFecha(notificacion.getFechaLimite());
    }

    private static String formatoFecha(LocalDate fecha) {
        if (fecha == null) return "";
        return String.format("%02d/%02d/%04d",
                fecha.getDayOfMonth(), fecha.getMonthValue(), fecha.getYear());
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
                    CalendarApp.class.getResource("/com/caa/documentos/temp-view.fxml"));
            Parent contenido = cargador.load();
            com.caa.documentos.TempController controlador = cargador.getController();
            controlador.cargarExpediente(idExpediente);
            Stage demo = new Stage();
            demo.initOwner(ventana);
            aplicarIcono(demo);
            demo.initModality(Modality.NONE);
            demo.setAlwaysOnTop(true);
            controlador.vincularTituloVentana(demo);
            controlador.vincularAbrirFormulario(() -> abrirFormsFxDemo(entrada, demo, controlador));
            demo.setScene(new Scene(contenido));
            dimensionarYCentrar(demo, ventana, 920, 520);
            demo.setOnHidden(evento -> {
                ventanasExpediente.remove(idExpediente, demo);
            });
            ventanasExpediente.put(idExpediente, demo);
            demo.show();
        } catch (IOException ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo abrir Demo",
                    "No se pudo cargar la interfaz Demo: " + ex.getMessage());
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
            Entry<?> entradaMenu = param.getEntry();
            if (menu != null && entradaMenu != null) {
                MenuItem copiar = new MenuItem("Copiar");
                copiar.setOnAction(evento -> copiarEvento(entradaMenu));
                menu.getItems().add(0, copiar);
            }
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

        javafx.util.Callback<DateControl.ContextMenuParameter, ContextMenu> callbackVacioOriginal =
                calendarView.getContextMenuCallback();
        calendarView.setContextMenuCallback(param -> {
            ContextMenu menu = callbackVacioOriginal == null ? null : callbackVacioOriginal.call(param);
            if (menu != null) {
                for (MenuItem item : menu.getItems()) {
                    if (item.getText() != null
                            && (item.getText().equalsIgnoreCase("Nuevo evento")
                            || item.getText().equalsIgnoreCase("New Event")
                            || item.getText().equalsIgnoreCase(
                                    Messages.getString("ContextMenuProvider.ADD_NEW_EVENT")))) {
                        item.setText("Auditoría");
                    }
                }
            }
            if (eventoCopiado == null || calendarioEventoCopiado == null
                    || calendarioEventoCopiado.isReadOnly()) return menu;

            if (menu == null) menu = new ContextMenu();
            if (!menu.getItems().isEmpty()) menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            MenuItem pegar = new MenuItem("Pegar");
            pegar.setOnAction(evento -> pegarEvento(param));
            menu.getItems().add(pegar);
            return menu;
        });
    }

    /** Guarda una copia independiente de los datos básicos del evento seleccionado. */
    private void copiarEvento(Entry<?> entrada) {
        if (entrada == null || entrada.getCalendar() == null || entrada.getCalendar().isReadOnly()) return;
        Entry<Object> copia = new Entry<>(entrada.getTitle());
        copia.setInterval(entrada.getInterval());
        copia.setFullDay(false);
        copia.setLocation(entrada.getLocation());
        Object actividad = entrada.getProperties().get(PROPIEDAD_ACTIVIDAD);
        if (actividad != null) copia.getProperties().put(PROPIEDAD_ACTIVIDAD, actividad);
        eventoCopiado = copia;
        calendarioEventoCopiado = entrada.getCalendar();
    }

    /** Pega el evento copiado en el día señalado, conservando su horario y duración. */
    private void pegarEvento(DateControl.ContextMenuParameter parametro) {
        if (eventoCopiado == null || calendarioEventoCopiado == null
                || calendarioEventoCopiado.isReadOnly()) return;

        LocalDate fechaOrigen = eventoCopiado.getInterval().getStartDateTime().toLocalDate();
        LocalDate fechaDestino = parametro.getZonedDateTime().toLocalDate();
        long dias = java.time.temporal.ChronoUnit.DAYS.between(fechaOrigen, fechaDestino);
        Interval intervaloOrigen = eventoCopiado.getInterval();
        Interval intervalo = new Interval(
                intervaloOrigen.getStartDateTime().plusDays(dias),
                intervaloOrigen.getEndDateTime().plusDays(dias),
                intervaloOrigen.getZoneId());

        Entry<Object> pegada = new Entry<>(eventoCopiado.getTitle());
        pegada.setInterval(intervalo);
        pegada.setFullDay(false);
        pegada.setLocation(eventoCopiado.getLocation());
        Object actividad = eventoCopiado.getProperties().get(PROPIEDAD_ACTIVIDAD);
        if (actividad != null) pegada.getProperties().put(PROPIEDAD_ACTIVIDAD, actividad);
        calendarioEventoCopiado.addEntry(pegada);
        entradas.add(pegada);
        guardarEventos();
        if (datos != null) datos.refrescar();
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
            if (entrada.getCalendar() != null) {
                entradas.add(entrada);
            } else {
                entradas.remove(entrada);
                if (!cargando) eliminarNotificacionDe(entrada);
            }
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
                    cod(String.valueOf(en.getProperties().getOrDefault(PROPIEDAD_EXPEDIENTE, ""))),
                    cod(String.valueOf(en.getProperties().getOrDefault(PROPIEDAD_NOTIFICACION, "")))));
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
                if (f.length >= 11 && !dec(f[10]).isBlank()) {
                    entrada.getProperties().put(PROPIEDAD_NOTIFICACION, dec(f[10]));
                }

                calendario.addEntry(entrada);
                entradas.add(entrada);
            } catch (RuntimeException ex) {
                ex.printStackTrace(); // línea dañada: se ignora y se sigue con las demás
            }
        }
        cargando = false;
    }

    private void cargarNotificaciones() {
        for (String linea : leer(ARCHIVO_NOTIFICACIONES)) {
            if (linea.isBlank()) continue;
            Notificacion notificacion = Notificacion.desdeLinea(linea);
            if (notificacion != null) notificaciones.add(notificacion);
        }
    }

    private void guardarNotificaciones() {
        List<String> lineas = new ArrayList<>();
        for (Notificacion notificacion : notificaciones) lineas.add(notificacion.toLinea());
        escribir(ARCHIVO_NOTIFICACIONES, lineas);
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
        fijarLocaleArgentina();
        launch(args);
    }

    static void fijarLocaleArgentina() {
        Locale argentina = Locale.forLanguageTag("es-AR");
        Locale.setDefault(argentina);
        Locale.setDefault(Locale.Category.DISPLAY, argentina);
        Locale.setDefault(Locale.Category.FORMAT, argentina);
    }
}
