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
import com.calendarfx.model.CalendarSource;
import com.calendarfx.model.Entry;
import com.calendarfx.view.CalendarView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Controles y lógica para filtrar visitas por industria o fecha. */
public final class Datos {

    private static final String NOMBRE = "Industria";
    private static final String FECHA = "Fecha";
    private static final String CATEGORIA = "Actividad  ";
    private static final DateTimeFormatter FORMATO_DIA = new DateTimeFormatterBuilder()
            .appendPattern("dd/MM/")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final CalendarView vista;
    private final List<CalendarSource> grupos;
    private final CalendarSource actividades;
    private final Set<Entry<?>> entradas;
    private final ComboBox<String> filtro = new ComboBox<>();
    private final ComboBox<String> selectorCategoria = new ComboBox<>();
    private final TextField campo = new TextField();
    private Timeline colocador;
    private HBox barraFiltros;
    private ListView<?> listaResultados;
    private Node accesorioFinal;

    public Datos(CalendarView vista, List<CalendarSource> grupos, CalendarSource actividades, Set<Entry<?>> entradas) {
        this.vista = vista;
        this.grupos = grupos;
        this.actividades = actividades;
        this.entradas = entradas;

        vista.setShowSearchField(false);
        vista.setEnableTimeZoneSupport(false);

        filtro.getItems().setAll(NOMBRE, FECHA, CATEGORIA);
        filtro.setValue(NOMBRE);
        filtro.setPrefWidth(115);
        filtro.valueProperty().addListener((observable, anterior, actual) -> {
            actualizarControlesFiltro();
            buscar(campo.getText());
        });

        selectorCategoria.setPromptText("Elegir categoría");
        selectorCategoria.setPrefWidth(155);
        selectorCategoria.valueProperty().addListener((observable, anterior, actual) -> {
            if (CATEGORIA.equals(filtro.getValue())) buscarPorCategoria(actual);
        });
        actividades.getCalendars().addListener((ListChangeListener<Calendar>) cambio -> {
            actualizarCategorias();
            refrescar();
        });
        actualizarCategorias();

        campo.setPrefColumnCount(18);
        campo.textProperty().addListener((observable, anterior, actual) -> buscar(actual));
        vista.getSearchResultView().getSearchResults().addListener(
                (ListChangeListener<Entry<?>>) cambio -> Platform.runLater(this::actualizarColoresResultados));
        for (CalendarSource grupo : grupos) {
            grupo.getCalendars().addListener((ListChangeListener<Calendar>) cambio -> refrescar());
        }
    }

    /** Ocupa en la barra superior el espacio del selector de zona y del buscador. */
    public void instalar(BorderPane raiz) {
        colocador = new Timeline(new KeyFrame(Duration.millis(300), evento -> {
            Node nodo = raiz.lookup(".right-toolbar-container");
            if (nodo instanceof HBox) {
                HBox barra = (HBox) nodo;
                barra.setSpacing(8);
                barraFiltros = barra;
                actualizarControlesFiltro();
                colocador.stop();
            }
        }));
        colocador.setCycleCount(40);
        colocador.play();
    }

    /** Recalcula los resultados si cambian o se eliminan eventos. */
    public void refrescar() {
        buscar(campo.getText());
    }

    /** Muestra en la barra de búsqueda el evento indicado. */
    public void mostrarEvento(Entry<?> entrada) {
        if (entrada == null) return;
        filtro.setValue(NOMBRE);
        if (entrada.getCalendar() != null) {
            campo.setText(entrada.getCalendar().getName());
        } else {
            campo.setText(entrada.getTitle());
        }
        Platform.runLater(() -> mostrarResultados(new ArrayList<>(List.of(entrada))));
    }

    private void actualizarCategorias() {
        String seleccionActual = selectorCategoria.getValue();
        List<String> nombres = new ArrayList<>();
        for (Calendar actividad : actividades.getCalendars()) nombres.add(actividad.getName());
        selectorCategoria.getItems().setAll(nombres);
        if (seleccionActual != null && selectorCategoria.getItems().contains(seleccionActual)) {
            selectorCategoria.setValue(seleccionActual);
        } else {
            selectorCategoria.setValue(null);
        }
    }

    /** Nodo opcional que se mantiene siempre al final de la barra de filtros. */
    public void setAccesorioFinal(Node nodo) {
        accesorioFinal = nodo;
        actualizarControlesFiltro();
    }

    private void actualizarControlesFiltro() {
        if (barraFiltros == null) return;
        List<Node> controles = new ArrayList<>();
        if (CATEGORIA.equals(filtro.getValue())) {
            controles.addAll(List.of(filtro, selectorCategoria));
        } else {
            controles.addAll(List.of(filtro, campo));
        }
        if (accesorioFinal != null) controles.add(accesorioFinal);
        barraFiltros.getChildren().setAll(controles);
    }

    private void buscar(String texto) {
        if (CATEGORIA.equals(filtro.getValue())) {
            buscarPorCategoria(selectorCategoria.getValue());
            return;
        }
        String consulta = texto == null ? "" : texto.trim();
        if (consulta.isEmpty()) {
            limpiarResultados();
        } else if (FECHA.equals(filtro.getValue())) {
            buscarPorFecha(consulta);
        } else {
            buscarPorNombre(consulta);
        }
    }

    private void buscarPorCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            limpiarResultados();
            return;
        }

        LocalDate hoy = LocalDate.now(vista.getZoneId());
        LocalDate hasta = hoy.plusYears(1);
        Map<String, Entry<?>> resultados = new LinkedHashMap<>();
        for (Entry<?> ocurrencia : ocurrenciasEnRango(hoy, hasta)) {
            if (categoria.equals(ocurrencia.getProperties().get(CalendarApp.PROPIEDAD_ACTIVIDAD))) {
                agregarUnico(resultados, ocurrencia);
            }
        }
        for (Entry<?> entrada : entradas) {
            if (!entrada.isRecurrence()
                    && categoria.equals(entrada.getProperties().get(CalendarApp.PROPIEDAD_ACTIVIDAD))) {
                agregarUnico(resultados, entrada);
            }
        }
        mostrarResultados(new ArrayList<>(resultados.values()));
    }

    private void buscarPorNombre(String consulta) {
        String normalizada = consulta.toLowerCase(Locale.ROOT);
        List<Calendar> calendariosCoincidentes = new ArrayList<>();
        for (CalendarSource grupo : grupos) {
            for (Calendar calendario : grupo.getCalendars()) {
                if (calendario.getName().toLowerCase(Locale.ROOT).contains(normalizada)) {
                    calendariosCoincidentes.add(calendario);
                }
            }
        }
        if (calendariosCoincidentes.isEmpty()) {
            limpiarResultados();
            return;
        }

        LocalDate hoy = LocalDate.now(vista.getZoneId());
        LocalDate hasta = hoy.plusYears(1);
        Map<String, Entry<?>> resultados = new LinkedHashMap<>();
        for (Entry<?> ocurrencia : ocurrenciasEnRango(hoy, hasta)) {
            if (calendariosCoincidentes.contains(ocurrencia.getCalendar())) agregarUnico(resultados, ocurrencia);
        }
        for (Entry<?> entrada : entradas) {
            if (calendariosCoincidentes.contains(entrada.getCalendar()) && !entrada.isRecurrence()) {
                agregarUnico(resultados, entrada);
            }
        }
        mostrarResultados(new ArrayList<>(resultados.values()));
    }

    private void buscarPorFecha(String texto) {
        if (texto.matches("\\d{2}/\\d{2}/\\d{2}")) {
            try {
                LocalDate fecha = LocalDate.parse(texto, FORMATO_DIA);
                vista.showDate(fecha);
                mostrarResultados(ocurrenciasEnRango(fecha, fecha));
                return;
            } catch (RuntimeException ignorada) {
                limpiarResultados();
                return;
            }
        }

        if (texto.matches("\\d{2}/\\d{4}")) {
            try {
                YearMonth mes = YearMonth.parse(texto, FORMATO_MES);
                vista.showYearMonth(mes);
                mostrarResultados(ocurrenciasEnRango(mes.atDay(1), mes.atEndOfMonth()));
                return;
            } catch (RuntimeException ignorada) {
                limpiarResultados();
                return;
            }
        }

        limpiarResultados();
    }

    private List<Entry<?>> ocurrenciasEnRango(LocalDate desde, LocalDate hasta) {
        Map<String, Entry<?>> resultados = new LinkedHashMap<>();
        for (CalendarSource grupo : grupos) {
            for (Calendar calendario : grupo.getCalendars()) {
                Map<LocalDate, List<Entry<?>>> porDia = calendario.findEntries(desde, hasta, vista.getZoneId());
                for (List<Entry<?>> entradasDelDia : porDia.values()) {
                    for (Entry<?> entrada : entradasDelDia) agregarUnico(resultados, entrada);
                }
            }
        }
        return new ArrayList<>(resultados.values());
    }

    private void agregarUnico(Map<String, Entry<?>> destino, Entry<?> entrada) {
        String clave = entrada.getCalendar().getName() + "|" + entrada.getTitle() + "|"
                + entrada.getInterval().getStartDateTime() + "|" + entrada.getInterval().getEndDateTime();
        destino.putIfAbsent(clave, entrada);
    }

    /** Corrige clases de color acumuladas por las celdas reutilizadas de CalendarFX. */
    private void actualizarColoresResultados() {
        if (listaResultados == null) {
            for (Node nodo : vista.getSearchResultView().lookupAll(".list-view")) {
                if (nodo instanceof ListView<?>) {
                    listaResultados = (ListView<?>) nodo;
                    listaResultados.addEventFilter(ScrollEvent.SCROLL,
                            evento -> Platform.runLater(this::actualizarColoresVisibles));
                    break;
                }
            }
        }
        actualizarColoresVisibles();
    }

    private void actualizarColoresVisibles() {
        for (Node nodo : vista.getSearchResultView().lookupAll(".search-result-cell")) {
            if (!(nodo instanceof ListCell<?>)) continue;
            Object elemento = ((ListCell<?>) nodo).getItem();
            if (elemento instanceof Entry<?>) actualizarColor(nodo, (Entry<?>) elemento);
        }
    }

    private void actualizarColor(Node nodo, Entry<?> entrada) {
        if (nodo instanceof Circle) {
            nodo.getStyleClass().removeIf(estilo -> estilo.matches("style\\d+-icon"));
            nodo.getStyleClass().add(entrada.getCalendar().getStyle() + "-icon");
        } else if (nodo instanceof Parent) {
            for (Node hijo : ((Parent) nodo).getChildrenUnmodifiable()) actualizarColor(hijo, entrada);
        }
    }

    private void mostrarResultados(List<Entry<?>> resultados) {
        resultados.sort(Comparator.comparing(entrada -> entrada.getInterval().getStartDateTime()));
        vista.getSearchResultView().getSearchResults().setAll(resultados);
    }

    private void limpiarResultados() {
        vista.getSearchResultView().getSearchResults().clear();
    }
}
