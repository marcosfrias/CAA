package com.caa.documentos;

import com.caa.app.Almacenamiento;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Slider;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.pixelduke.control.ribbon.RibbonGroup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class TempController {

    private Data data;
    private String idExpediente;
    private Runnable accionAbrirFormulario;
    private DisposicionImagenes disposicionImagenes = DisposicionImagenes.PREDETERMINADA;
    private File excelParaTabla;

    @FXML
    private ListView<Elemento> menuList;

    @FXML
    private TreeView<Object> archivosTree;

    @FXML
    private TabPane pestanas;

    @FXML
    private TextArea contenido;
    private StringProperty textoVinculado;

    @FXML
    private FlowPane imagenesInsertadas;

    @FXML
    private VBox tablasInsertadas;

    @FXML
    private VBox zonaFirma;

    @FXML
    private StackPane contenedorFirma;

    @FXML
    private Slider anchoFirma;

    @FXML
    private Button quitarFirma;
    private ImageView vistaFirma;
    private boolean actualizandoFirma;

    @FXML
    private HBox barraHerramientas;

    @FXML
    private Label estado;

    @FXML
    private Label tituloVentana;

    public void vincularTituloVentana(Stage stage) {
        stage.titleProperty().bind(tituloVentana.textProperty());
    }

    public void vincularAbrirFormulario(Runnable accion) {
        accionAbrirFormulario = accion;
    }

    public void generarInformeFormulario(Map<String, String> valores) {
        String nombre = valores.getOrDefault("fileNumber", "").trim();
        if (data == null || nombre.isEmpty()) return;

        String informe = generarInformeLegal(valores);
        Elemento existente = menuList.getItems().stream()
                .filter(elemento -> elemento.getNombre().equalsIgnoreCase(nombre))
                .findFirst().orElse(null);

        if (existente != null) {
            existente.textoProperty().set(informe);
            menuList.getSelectionModel().select(existente);
        } else {
            Elemento elemento = new Elemento(nombre);
            elemento.textoProperty().set(informe);
            observarCambios(elemento);
            menuList.getItems().add(elemento);
            menuList.getSelectionModel().select(elemento);
        }
        guardarDatos();
        refrescarArbolAdjuntos();
        estado.setText("Informe del expediente actualizado: " + nombre);
    }

    private static String generarInformeLegal(Map<String, String> v) {
        StringBuilder sb = new StringBuilder();
        String empresa = v.getOrDefault("company", "");
        String expediente = v.getOrDefault("fileNumber", "");
        String actividad = v.getOrDefault("actividad", "");
        String email = v.getOrDefault("email", "");
        String obs = v.getOrDefault("observations", "");

        sb.append("                    INFORME DE AUDITORÍA AMBIENTAL\n\n\n");
        sb.append("I.   IDENTIFICACIÓN\n\n");
        sb.append("     Empresa:          ").append(empresa).append("\n");
        sb.append("     Actividad:        ").append(actividad).append("\n");
        sb.append("     Expediente N.°:   ").append(expediente).append("\n");
        if (!email.isBlank())
            sb.append("     Contacto:         ").append(email).append("\n");
        sb.append("\n\n");

        sb.append("II.  AUDITORÍAS REALIZADAS\n\n");
        boolean hayAuditorias = false;
        for (int i = 1; ; i++) {
            String fecha = v.get("auditoriaFecha" + i);
            if (fecha == null || fecha.isBlank()) break;
            hayAuditorias = true;
            String consultor = v.getOrDefault("auditoriaConsultor" + i, "");
            String auditor = v.getOrDefault("auditoriaAuditor" + i, "");
            sb.append("     ").append(i).append(". Fecha de auditoría:   ").append(fecha).append("\n");
            if (!consultor.isBlank())
                sb.append("        Consultor:              ").append(consultor).append("\n");
            if (!auditor.isBlank())
                sb.append("        Auditor:                ").append(auditor).append("\n");
            if (i == 1) {
                String dias = v.getOrDefault("diasDesdeUltimaAuditoria", "");
                if (!dias.isBlank())
                    sb.append("        Días desde la anterior: ").append(dias).append("\n");
            }
            sb.append("\n");
        }
        if (!hayAuditorias)
            sb.append("     Sin auditorías registradas.\n\n");
        sb.append("\n");

        sb.append("III. RESOLUCIONES\n\n");
        boolean hayResoluciones = false;
        for (int i = 1; ; i++) {
            String res = v.get("resolucion" + i);
            if (res == null || res.isBlank()) break;
            hayResoluciones = true;
            String anio = v.getOrDefault("resolucionAnio" + i, "");
            String cert = v.getOrDefault("certifico" + i, "false");
            sb.append("     ").append(i).append(". ");
            if (!anio.isBlank()) sb.append("Año ").append(anio).append(" — ");
            sb.append(res);
            sb.append("true".equalsIgnoreCase(cert) ? "  (Certificó)" : "  (No certificó)");
            sb.append("\n");
        }
        if (!hayResoluciones)
            sb.append("     Sin resoluciones registradas.\n");
        sb.append("\n\n");

        sb.append("IV. NOTIFICACIONES\n\n");
        boolean hayNotificaciones = false;
        for (int i = 1; ; i++) {
            String notificacion = v.get("notificacion" + i);
            if (notificacion == null || notificacion.isBlank()) break;
            hayNotificaciones = true;
            sb.append("     ").append(i).append(". ").append(notificacion).append("\n");
        }
        if (!hayNotificaciones)
            sb.append("     Sin notificaciones registradas.\n");
        sb.append("\n\n");

        String notaNumero = v.getOrDefault("notaNumero", "");
        if (!notaNumero.isBlank()) {
            sb.append("V.   COMUNICACIÓN OFICIAL\n\n");
            sb.append("     Nota N.°:         ").append(notaNumero);
            String notaLetra = v.getOrDefault("notaLetra", "");
            if (!notaLetra.isBlank()) sb.append("  (").append(notaLetra).append(")");
            sb.append("\n");
            String notaFecha = v.getOrDefault("notaFecha", "");
            if (!notaFecha.isBlank())
                sb.append("     Fecha:           ").append(notaFecha).append("\n");
            String tema = v.getOrDefault("notaTema", "");
            if (!tema.isBlank())
                sb.append("     Tema:            ").append(tema).append("\n");
            String tipo = v.getOrDefault("notaTipo", "");
            if (!tipo.isBlank())
                sb.append("     Tipo:            ").append(tipo).append("\n");
            String respuestaFecha = v.getOrDefault("respuestaFecha", "");
            if (!respuestaFecha.isBlank())
                sb.append("     Fecha respuesta:  ").append(respuestaFecha).append("\n");
            String notaRespuesta = v.getOrDefault("notaRespuesta", "");
            if (!notaRespuesta.isBlank())
                sb.append("     Respuesta:       ").append(notaRespuesta).append("\n");
            sb.append("\n\n");
        }

        if (!obs.isBlank()) {
            int seccion = notaNumero.isBlank() ? 5 : 6;
            sb.append(seccion == 5 ? "V." : "VI.").append("   OBSERVACIONES\n\n");
            for (String linea : obs.split("\\R", -1)) {
                sb.append("     ").append(linea).append("\n");
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    @FXML
    public void initialize() {
        barraHerramientas.setMaxWidth(Double.MAX_VALUE);
        barraHerramientas.getStylesheets().add(
                com.pixelduke.control.Ribbon.class.getResource("fxribbon.css").toExternalForm());
        contenido.setDisable(true);
        contenido.textProperty().addListener((obs, anterior, texto) -> ajustarAlturaContenido(texto));
        HBox.setHgrow(anchoFirma, Priority.ALWAYS);
        anchoFirma.valueProperty().addListener((obs, anterior, nuevo) -> {
            if (actualizandoFirma || nuevo == null) return;
            Elemento elegido = menuList.getSelectionModel().getSelectedItem();
            if (elegido == null || elegido.getFirma().isBlank()) return;
            elegido.setAnchoFirmaCm(nuevo.doubleValue());
            if (vistaFirma != null) vistaFirma.setFitWidth(nuevo.doubleValue() * 37.8);
            guardarDatos();
        });
        quitarFirma.setOnAction(evento -> onBorrarFirma());

        menuList.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, anterior, elegido) -> {
                    vincularTexto(elegido);
                    if (imagenesInsertadas != null) mostrarImagenes(elegido);
                    if (tablasInsertadas != null) mostrarTablas(elegido);
                    mostrarFirma(elegido);
                });

        VBox.setVgrow(archivosTree, javafx.scene.layout.Priority.ALWAYS);
        archivosTree.setMinHeight(0);
        archivosTree.setShowRoot(true);
        archivosTree.getSelectionModel().selectedItemProperty().addListener((obs, anterior, seleccionado) -> {
            if (seleccionado != null && seleccionado.getValue() instanceof Elemento) {
                menuList.getSelectionModel().select((Elemento) seleccionado.getValue());
            }
        });
        archivosTree.setCellFactory(arbol -> new TreeCell<Object>() {
            @Override
            protected void updateItem(Object valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }
                if (valor instanceof Elemento) {
                    setText(((Elemento) valor).getNombre());
                    setGraphic(icono("fas-file-alt", 14, "#168eb5"));
                } else if (valor instanceof RaizExpediente) {
                    setText(((RaizExpediente) valor).getNombre());
                    setGraphic(icono("fas-folder-open", 14, "#e7a12c"));
                } else {
                    String ruta = (String) valor;
                    Path path = Path.of(ruta);
                    String nombre = path.getFileName().toString();
                    boolean carpeta = data != null && Files.isDirectory(data.resolverArchivo(ruta));
                    boolean libro = !carpeta && esLibroExcel(path.toFile());
                    String codigo = carpeta ? "fas-folder" : libro ? "fas-file-excel" : "fas-file-image";
                    setText(nombre);
                    setGraphic(icono(codigo, 14, carpeta ? "#e7a12c" : libro ? "#2e8b57" : "#6b7785"));
                    setTooltip(new javafx.scene.control.Tooltip(ruta));
                }
                setContextMenu(crearMenuContextual(valor));
            }
        });
        archivosTree.setOnMouseClicked(evento -> {
            if (evento.getClickCount() == 2) abrirAdjuntoSeleccionado();
        });

        armarRibbon();
    }

    public void cargarExpediente(String idExpediente) {
        this.idExpediente = idExpediente;
        excelParaTabla = null;
        data = new Data(idExpediente);
        menuList.getItems().clear();
        contenido.clear();
        contenido.setDisable(true);
        boolean datosMigrados = false;
        for (Data.Item item : data.cargarElementos()) {
            Elemento elemento = new Elemento(item.getNombre());
            elemento.textoProperty().set(item.getTexto());
            elemento.getImagenes().addAll(item.getImagenes());
            elemento.getTablas().addAll(item.getTablas());
            datosMigrados |= migrarTablasAntiguas(elemento);
            elemento.setFirma(item.getFirma());
            elemento.setAnchoFirmaCm(item.getAnchoFirmaCm());
            observarCambios(elemento);
            menuList.getItems().add(elemento);
        }
        if (!menuList.getItems().isEmpty()) {
            menuList.getSelectionModel().selectFirst();
        }
        refrescarArbolAdjuntos();
        if (datosMigrados) guardarDatos();
        estado.setText("Expediente del evento cargado");
    }

    // ------------------------------------------------------------------
    // Ribbon
    // ------------------------------------------------------------------

    private FontIcon icono(String codigo, int tamano, String color) {
        FontIcon fi = new FontIcon(codigo);
        fi.setIconSize(tamano);
        fi.setIconColor(Color.web(color));
        return fi;
    }

    private Button botonGrande(String texto, String codigoIcono, String color, Runnable accion) {
        Button b = new Button(texto, icono(codigoIcono, 32, color));
        b.setContentDisplay(ContentDisplay.TOP);
        b.setFocusTraversable(false);
        if (accion != null) {
            b.setOnAction(e -> accion.run());
        }
        return b;
    }

    private void armarRibbon() {

        RibbonGroup grupoArchivo = new RibbonGroup();
        grupoArchivo.setTitle("Archivo");
        Separator separadorExportar = new Separator(Orientation.VERTICAL);
        grupoArchivo.getNodes().addAll(
                botonGrande("Abrir", "fas-folder-open", "#f5a623", this::onAbrir),
                botonGrande("Expediente", "fas-file-alt", "#5aa9e6", this::abrirFormulario),
                botonGrande("Guardar", "fas-save", "#2a7de1", this::guardarManual),
                separadorExportar,
                botonGrande("Exportar", "fas-file-export", "#2a7de1",
                        () -> exportar(pestanas.getScene().getWindow()))
        );

        // --- Grupo Elementos: botones grandes con ícono ---
        RibbonGroup grupoElementos = new RibbonGroup();
        grupoElementos.setTitle("Elementos");
        grupoElementos.getNodes().add(botonGrande("Eliminar", "fas-trash", "#808080", this::onEliminar));

        // --- Grupo Texto: controles en filas ---
        ComboBox<Integer> tamano = new ComboBox<>();
        tamano.getItems().addAll(10, 12, 14, 16, 18, 24);
        tamano.setValue(12);
        tamano.setPrefWidth(72);
        ComboBox<String> tipoLetra = new ComboBox<>();
        tipoLetra.getItems().setAll(Font.getFamilies());
        tipoLetra.setValue("System");
        tipoLetra.setPrefWidth(160);
        tipoLetra.setMinWidth(110);
        tipoLetra.setMaxWidth(Double.MAX_VALUE);
        Runnable actualizarTipografia = () -> contenido.setStyle(
                "-fx-padding: 8; -fx-font-size: " + tamano.getValue()
                        + "px; -fx-font-family: \"" + tipoLetra.getValue() + "\";");
        actualizarTipografia.run();
        tamano.valueProperty().addListener((obs, anterior, nuevo) -> actualizarTipografia.run());
        tipoLetra.valueProperty().addListener((obs, anterior, nuevo) -> actualizarTipografia.run());

        CheckBox ajuste = new CheckBox("Ajuste de línea");
        ajuste.setSelected(true);
        contenido.wrapTextProperty().bind(ajuste.selectedProperty());

        HBox formato = new HBox(10, new Label("Tamaño:"), tamano, new Label("Fuente:"), tipoLetra);
        formato.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tipoLetra, Priority.ALWAYS);
        HBox edicion = new HBox(12, ajuste,
                botonPequeno("Deshacer", "fas-undo", "#2a7de1", () -> contenido.undo()),
                botonPequeno("Rehacer", "fas-redo", "#2a7de1", () -> contenido.redo()));
        edicion.setAlignment(Pos.CENTER_LEFT);
        VBox filas = new VBox(10, formato, edicion);
        filas.setAlignment(Pos.CENTER_LEFT);
        filas.setMinWidth(0);
        filas.setMaxWidth(Double.MAX_VALUE);

        RibbonGroup grupoTexto = new RibbonGroup();
        grupoTexto.setTitle("Texto");
        grupoTexto.getNodes().add(filas);

        // --- Grupo Imágenes ---
        RibbonGroup grupoImagenes = new RibbonGroup();
        grupoImagenes.setTitle("Imágen");
        Button botonImagen = botonGrande("Imágen", "fas-image", "#2a7de1", this::onImagenes);
        Button botonFlechaImagen = new Button(null, icono("fas-chevron-down", 10, "#2a7de1"));
        botonFlechaImagen.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        botonFlechaImagen.setFocusTraversable(false);
        botonFlechaImagen.setTooltip(new javafx.scene.control.Tooltip("Opciones de imagen"));
        MenuItem disposicion = new MenuItem("Disposición de imágenes...");
        disposicion.setOnAction(evento ->
                DialogoDisposicionImagenes.abrir(pestanas.getScene().getWindow(), disposicionImagenes,
                        opciones -> disposicionImagenes = opciones));
        ContextMenu menuImagen = new ContextMenu(disposicion);
        botonFlechaImagen.setOnAction(evento ->
                menuImagen.show(botonFlechaImagen, Side.BOTTOM, 0, 0));
        HBox botonesImagen = new HBox(0, botonImagen, botonFlechaImagen);
        botonesImagen.setAlignment(Pos.CENTER);
        grupoImagenes.getNodes().add(botonesImagen);

        RibbonGroup grupoTabla = new RibbonGroup();
        grupoTabla.setTitle("Tabla");
        grupoTabla.getNodes().add(botonGrande("Tabla", "fas-table", "#2a7de1", this::onTabla));

        // --- Grupo Ayuda ---
        RibbonGroup grupoAyuda = new RibbonGroup();
        grupoAyuda.setTitle("Ayuda");
        grupoAyuda.getNodes().add(
                botonGrande("Ayuda", "fas-question-circle", "#2a7de1", this::onAyuda));

        // --- Grupo Firmas ---
        Button btnAgregarFirma = botonPequeno("Agregar", "fas-signature", "#2e9e4f", this::onAgregarFirma);
        Button btnBorrarFirma = botonPequeno("Borrar", "fas-times-circle", "#d9534f", this::onBorrarFirma);
        btnAgregarFirma.setMaxWidth(Double.MAX_VALUE);
        btnBorrarFirma.setMaxWidth(Double.MAX_VALUE);
        VBox columnaFirmas = new VBox(6, btnAgregarFirma, btnBorrarFirma);
        columnaFirmas.setAlignment(Pos.CENTER);

        RibbonGroup grupoFirmas = new RibbonGroup();
        grupoFirmas.setTitle("Firmas");
        grupoFirmas.getNodes().add(columnaFirmas);

        // --- Barra de herramientas ---
        barraHerramientas.getChildren().setAll(
                grupoArchivo, grupoElementos, grupoTexto, grupoImagenes, grupoTabla, grupoFirmas, grupoAyuda);
        HBox.setHgrow(grupoTexto, Priority.ALWAYS);
        grupoTexto.setMaxWidth(Double.MAX_VALUE);
        grupoTexto.skinProperty().addListener((obs, anterior, nueva) -> {
            if (nueva == null) return;
            Node contenidoTexto = grupoTexto.lookup(".ribbon-group-content");
            if (contenidoTexto instanceof HBox) {
                HBox.setHgrow(filas, Priority.ALWAYS);
                Node columnaTexto = contenidoTexto.getParent();
                if (columnaTexto instanceof Region) {
                    Region columna = (Region) columnaTexto;
                    columna.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(columna, Priority.ALWAYS);
                }
            }
        });

    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    private void abrirFormulario() {
        accionAbrirFormulario.run();
    }

    private void onAyuda() {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Ayuda");
        alerta.setHeaderText("Cómo usar el expediente");
        alerta.setContentText(
                "Seleccioná un expediente de la lista para editar su texto.\n"
                        + "Abrí imágenes y después usá Imágen para insertarlas debajo del texto.\n"
                        + "Abrí un libro de Excel y usá Tabla para insertarlo en el punto del texto donde está el cursor.\n"
                        + "La flecha junto a Imágen permite elegir su disposición.\n"
                        + "En Firmas podés subir una imagen, dibujar y ajustar su tamaño al pie.\n"
                        + "Usá Formulario para completar los datos del evento; al guardar se agrega o actualiza el informe con el número de expediente.\n"
                        + "Exportar permite descargar el elemento seleccionado.\n\n"
                        + "Los datos se guardan en:\n" + Almacenamiento.carpetaDatos());
        alerta.initOwner(pestanas.getScene().getWindow());
        alerta.showAndWait();
    }

    private void onAgregarFirma() {
        Elemento elegido = menuList.getSelectionModel().getSelectedItem();
        if (data == null || elegido == null) {
            showWarning("Elegí un expediente antes de agregar una firma.");
            return;
        }

        Dialog<String> dialogo = new Dialog<>();
        dialogo.setTitle("Agregar firma");
        dialogo.setHeaderText("Subí una imagen o dibujá tu firma");
        dialogo.initOwner(pestanas.getScene().getWindow());

        Button subir = new Button("Subir imagen");
        Button dibujar = new Button("Dibujar");
        subir.setOnAction(evento -> {
            FileChooser selector = new FileChooser();
            selector.setTitle("Seleccionar firma");
            selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Imágenes", "*.png", "*.jpg", "*.jpeg"));
            File archivo = selector.showOpenDialog(dialogo.getDialogPane().getScene().getWindow());
            if (archivo == null) return;
            try {
                dialogo.setResult(data.copiarFirma(archivo));
                dialogo.close();
            } catch (IllegalArgumentException | IllegalStateException e) {
                showWarning(e.getMessage());
            }
        });
        dibujar.setOnAction(evento -> {
            String ruta = dibujarFirma();
            if (ruta != null) {
                dialogo.setResult(ruta);
                dialogo.close();
            }
        });

        VBox contenidoDialogo = new VBox(12,
                new Label("La firma se colocará al pie del documento seleccionado."),
                new HBox(10, subir, dibujar));
        contenidoDialogo.setStyle("-fx-padding: 16;");
        dialogo.getDialogPane().setContent(contenidoDialogo);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialogo.showAndWait().ifPresent(this::aplicarFirma);
    }

    private String dibujarFirma() {
        Dialog<BufferedImage> dialogo = new Dialog<>();
        dialogo.setTitle("Dibujar firma");
        dialogo.setHeaderText("Dibujá tu firma dentro del recuadro");
        dialogo.initOwner(pestanas.getScene().getWindow());

        Canvas lienzo = new Canvas(560, 220);
        StackPane areaDibujo = new StackPane(lienzo);
        areaDibujo.setStyle("-fx-background-color: white; -fx-border-color: #b8b8b8;");
        GraphicsContext pincel = lienzo.getGraphicsContext2D();
        pincel.setStroke(Color.BLACK);
        pincel.setLineWidth(2.5);
        pincel.setLineCap(StrokeLineCap.ROUND);
        pincel.setLineJoin(StrokeLineJoin.ROUND);

        Slider grosor = new Slider(1, 8, 2.5);
        grosor.setPrefWidth(180);
        grosor.setShowTickLabels(true);
        grosor.setShowTickMarks(true);
        grosor.valueProperty().addListener((obs, anterior, nuevo) -> pincel.setLineWidth(nuevo.doubleValue()));
        Button limpiar = new Button("Limpiar");
        ButtonType guardar = ButtonType.OK;
        dialogo.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        Node botonGuardar = dialogo.getDialogPane().lookupButton(guardar);
        botonGuardar.setDisable(true);

        lienzo.setOnMousePressed(evento -> {
            pincel.beginPath();
            pincel.moveTo(evento.getX(), evento.getY());
            pincel.lineTo(evento.getX() + 0.1, evento.getY() + 0.1);
            pincel.stroke();
            botonGuardar.setDisable(false);
        });
        lienzo.setOnMouseDragged(evento -> {
            pincel.lineTo(evento.getX(), evento.getY());
            pincel.stroke();
        });
        limpiar.setOnAction(evento -> {
            pincel.clearRect(0, 0, lienzo.getWidth(), lienzo.getHeight());
            botonGuardar.setDisable(true);
        });

        VBox contenidoDialogo = new VBox(12, areaDibujo,
                new HBox(12, new Label("Grosor:"), grosor, limpiar));
        contenidoDialogo.setStyle("-fx-padding: 16;");
        dialogo.getDialogPane().setContent(contenidoDialogo);
        dialogo.setResultConverter(boton -> boton == guardar ? capturarFirma(lienzo) : null);

        Optional<BufferedImage> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() == null) return null;
        try {
            return data.guardarFirmaDibujada(resultado.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            showWarning(e.getMessage());
            return null;
        }
    }

    private static BufferedImage capturarFirma(Canvas lienzo) {
        WritableImage captura = lienzo.snapshot(null, null);
        PixelReader lector = captura.getPixelReader();
        BufferedImage imagen = new BufferedImage(
                (int) captura.getWidth(), (int) captura.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int color = lector.getArgb(x, y);
                // Canvas.snapshot pinta de blanco el fondo vacío.
                imagen.setRGB(x, y, color == 0xffffffff ? 0 : color);
            }
        }
        return recortarFirma(imagen);
    }

    private static BufferedImage recortarFirma(BufferedImage imagen) {
        int minX = imagen.getWidth();
        int minY = imagen.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                if ((imagen.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) return null;
        int ancho = maxX - minX + 1;
        int alto = maxY - minY + 1;
        BufferedImage recorte = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graficos = recorte.createGraphics();
        graficos.drawImage(imagen, 0, 0, ancho, alto, minX, minY, maxX + 1, maxY + 1, null);
        graficos.dispose();
        return recorte;
    }

    private void aplicarFirma(String ruta) {
        Elemento elegido = menuList.getSelectionModel().getSelectedItem();
        if (elegido == null || ruta == null || ruta.isBlank()) return;
        String anterior = elegido.getFirma();
        elegido.setFirma(ruta);
        elegido.setAnchoFirmaCm(6.0);
        guardarDatos();
        mostrarFirma(elegido);
        refrescarArbolAdjuntos();
        eliminarFirmaSiNoSeUsa(anterior);
        estado.setText("Firma colocada al pie del documento");
    }

    private void onBorrarFirma() {
        Elemento elegido = menuList.getSelectionModel().getSelectedItem();
        if (elegido == null || elegido.getFirma().isBlank()) return;
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Quitar la firma de este documento?", ButtonType.OK, ButtonType.CANCEL);
        confirmacion.setTitle("Quitar firma");
        confirmacion.setHeaderText(null);
        confirmacion.initOwner(pestanas.getScene().getWindow());
        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String anterior = elegido.getFirma();
        elegido.setFirma("");
        elegido.setAnchoFirmaCm(6.0);
        guardarDatos();
        mostrarFirma(elegido);
        refrescarArbolAdjuntos();
        eliminarFirmaSiNoSeUsa(anterior);
        estado.setText("Firma quitada del documento");
    }

    private void eliminarFirmaSiNoSeUsa(String ruta) {
        if (ruta == null || ruta.isBlank()) return;
        for (Elemento elemento : menuList.getItems()) {
            if (ruta.equals(elemento.getFirma())) return;
        }
        try {
            data.eliminarAdjunto(ruta);
        } catch (IOException e) {
            showWarning("No se pudo eliminar la firma anterior: " + e.getMessage());
        }
    }

    private void mostrarFirma(Elemento elemento) {
        contenedorFirma.getChildren().clear();
        vistaFirma = null;
        if (elemento == null || elemento.getFirma().isBlank() || data == null) {
            zonaFirma.setVisible(false);
            zonaFirma.setManaged(false);
            return;
        }
        File archivo = data.resolverArchivo(elemento.getFirma()).toFile();
        if (!archivo.isFile()) {
            zonaFirma.setVisible(false);
            zonaFirma.setManaged(false);
            return;
        }
        Image imagen = new Image(archivo.toURI().toString(), false);
        ImageView vista = new ImageView(imagen);
        vista.setPreserveRatio(true);
        vista.setSmooth(true);
        vista.setFitWidth(elemento.getAnchoFirmaCm() * 37.8);
        vistaFirma = vista;
        contenedorFirma.getChildren().add(vista);
        actualizandoFirma = true;
        anchoFirma.setValue(elemento.getAnchoFirmaCm());
        actualizandoFirma = false;
        zonaFirma.setVisible(true);
        zonaFirma.setManaged(true);
    }

    private void onImagenes() {
        Elemento elegido = menuList.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            showWarning("Elegí un elemento del expediente antes de insertar una imagen.");
            return;
        }
        List<String> disponibles = data.listarImagenes();
        if (disponibles.isEmpty()) {
            showWarning("Primero usá Abrir para agregar imágenes al expediente.");
            return;
        }
        ChoiceDialog<String> selector = new ChoiceDialog<>(disponibles.get(0), disponibles);
        selector.setTitle("Insertar imagen");
        selector.setHeaderText("Elegí una imagen del expediente");
        selector.setContentText("Imagen:");
        selector.initOwner(pestanas.getScene().getWindow());
        selector.showAndWait().ifPresent(ruta -> {
            if (!elegido.getImagenes().contains(ruta)) {
                elegido.getImagenes().add(ruta);
                guardarDatos();
            }
            mostrarImagenes(elegido);
            pestanas.getSelectionModel().select(0);
            estado.setText("Imagen agregada debajo del texto: " + new File(ruta).getName());
        });
    }

    private void onAbrir() {
        if (data == null) {
            showWarning("No hay un expediente cargado.");
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Abrir imágenes o un libro de Excel");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imágenes y libros de Excel", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp",
                "*.xlsx", "*.xlsm", "*.xls"));
        List<File> seleccionadas = selector.showOpenMultipleDialog(pestanas.getScene().getWindow());
        if (seleccionadas == null || seleccionadas.isEmpty()) return;

        List<File> libros = new ArrayList<>();
        for (File archivo : seleccionadas) if (esLibroExcel(archivo)) libros.add(archivo);
        if (!libros.isEmpty()) {
            if (libros.size() != 1 || seleccionadas.size() != 1) {
                showWarning("Abrí un solo libro de Excel por vez.");
                return;
            }
            try {
                String ruta = data.copiarLibroExcel(libros.get(0));
                excelParaTabla = data.resolverArchivo(ruta).toFile();
                refrescarArbolAdjuntos();
                estado.setText("Libro agregado en Libros: " + excelParaTabla.getName()
                        + ". Usá Tabla para insertarlo en el expediente.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                showWarning("No se pudo agregar el libro de Excel: " + e.getMessage());
            }
            return;
        }

        try {
            for (File imagen : seleccionadas) data.copiarImagen(imagen);
            excelParaTabla = null;
            refrescarArbolAdjuntos();
            estado.setText("Imágenes agregadas al expediente: " + seleccionadas.size());
        } catch (IllegalArgumentException | IllegalStateException e) {
            showWarning("No se pudieron agregar las imágenes: " + e.getMessage());
        }
    }

    private boolean esLibroExcel(File archivo) {
        String nombre = archivo.getName().toLowerCase(java.util.Locale.ROOT);
        return nombre.endsWith(".xlsx") || nombre.endsWith(".xlsm") || nombre.endsWith(".xls");
    }

    private void onTabla() {
        Elemento elegido = menuList.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            showWarning("Elegí un elemento del expediente antes de insertar una tabla.");
            return;
        }
        if (excelParaTabla == null || !excelParaTabla.isFile()) {
            showWarning("Primero usá Abrir para elegir un libro de Excel.");
            return;
        }
        try {
            List<String> hojas = TablaExcel.hojas(excelParaTabla.toPath());
            if (hojas.isEmpty()) throw new IOException("El libro no tiene hojas.");
            ChoiceDialog<String> selector = new ChoiceDialog<>(hojas.get(0), hojas);
            selector.setTitle("Insertar tabla");
            selector.setHeaderText("Elegí la hoja que querés insertar");
            selector.setContentText("Hoja:");
            selector.initOwner(pestanas.getScene().getWindow());
            selector.showAndWait().ifPresent(hoja -> {
                try {
                    List<List<String>> celdas = TablaExcel.leer(excelParaTabla.toPath(), hoja);
                    String tabla = TablaExcel.serializar(celdas);
                    if (tabla.isBlank()) throw new IOException("La hoja no contiene datos.");
                    elegido.getTablas().add(tabla);
                    int cursor = contenido.getCaretPosition();
                    String separador = cursor > 0 && !contenido.getText().substring(0, cursor).endsWith("\n")
                            ? "\n" : "";
                    String marcador = TablaExcel.marcador(elegido.getTablas().size(), hoja);
                    contenido.insertText(cursor, separador + marcador + "\n");
                    mostrarTablas(elegido);
                    guardarDatos();
                    contenido.requestFocus();
                    estado.setText("Tabla insertada en el expediente: " + hoja);
                } catch (IOException | RuntimeException e) {
                    showWarning("No se pudo insertar la tabla: " + e.getMessage());
                }
            });
        } catch (IOException | RuntimeException e) {
            showWarning("No se pudo abrir el libro de Excel: " + e.getMessage());
        }
    }

    private boolean migrarTablasAntiguas(Elemento elemento) {
        List<String> lineas = java.util.Arrays.asList(elemento.textoProperty().get().split("\\R", -1));
        StringBuilder texto = new StringBuilder();
        boolean migrado = false;
        for (int i = 0; i < lineas.size(); i++) {
            List<List<String>> tabla = TablaExcel.tablaEn(lineas, i);
            if (tabla != null) {
                migrado = true;
                elemento.getTablas().add(TablaExcel.serializar(tabla));
                if (texto.length() > 0 && texto.charAt(texto.length() - 1) != '\n') texto.append('\n');
                texto.append(TablaExcel.marcador(elemento.getTablas().size(), "Importada")).append('\n');
                while (i + 1 < lineas.size() && TablaExcel.esFila(lineas.get(i + 1))) i++;
            } else {
                texto.append(lineas.get(i)).append('\n');
            }
        }
        elemento.textoProperty().set(texto.toString().stripTrailing());
        return migrado;
    }

    private void mostrarTablas(Elemento elemento) {
        tablasInsertadas.getChildren().clear();
        if (elemento == null || elemento.getTablas().isEmpty()) {
            tablasInsertadas.setVisible(false);
            tablasInsertadas.setManaged(false);
            return;
        }
        tablasInsertadas.setVisible(true);
        tablasInsertadas.setManaged(true);
        tablasInsertadas.getChildren().add(new Label("Tablas"));
        for (int i = 0; i < elemento.getTablas().size(); i++) {
            try {
                List<List<String>> celdas = TablaExcel.deserializar(elemento.getTablas().get(i));
                BufferedImage tabla = TablaExcel.imagenPrevia(celdas);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(tabla, "png", png);
                Image vistaPrevia = new Image(new ByteArrayInputStream(png.toByteArray()), 720, 420, true, true);
                ImageView vista = new ImageView(vistaPrevia);
                vista.setPreserveRatio(true);
                int filasVisibles = Math.min(celdas.size(), TablaExcel.filasVistaPrevia());
                String detalle = celdas.size() > filasVisibles
                        ? " (vista previa: " + filasVisibles + " de " + celdas.size() + " filas)" : "";
                Label nombre = new Label("Tabla " + (i + 1) + detalle);
                VBox tarjeta = new VBox(4, nombre, vista);
                tarjeta.setStyle("-fx-padding: 8; -fx-background-color: white; -fx-border-color: #c8c8c8;");
                tablasInsertadas.getChildren().add(tarjeta);
            } catch (IOException e) {
                showWarning("No se pudo mostrar la tabla " + (i + 1) + ": " + e.getMessage());
            }
        }
    }

    private void refrescarArbolAdjuntos() {
        if (archivosTree == null || data == null) return;
        List<String> adjuntos = data.listarAdjuntos();
        List<Elemento> elementos = new ArrayList<>(menuList.getItems());
        if (elementos.isEmpty() && adjuntos.isEmpty()) {
            archivosTree.setRoot(null);
            return;
        }

        TreeItem<Object> raiz;
        if (elementos.size() == 1) {
            raiz = new TreeItem<>(elementos.get(0));
        } else {
            raiz = new TreeItem<>(new RaizExpediente(elementos.isEmpty() ? "Expediente" : "Expedientes"));
            for (Elemento elemento : elementos) raiz.getChildren().add(new TreeItem<>(elemento));
        }

        Map<String, TreeItem<Object>> nodos = new HashMap<>();
        for (String ruta : adjuntos) {
            int separador = ruta.lastIndexOf('/');
            String padre = separador < 0 ? "" : ruta.substring(0, separador);
            TreeItem<Object> item = new TreeItem<>(ruta);
            TreeItem<Object> nodoPadre = nodos.getOrDefault(padre, raiz);
            nodoPadre.getChildren().add(item);
            nodos.put(ruta, item);
        }
        raiz.setExpanded(true);
        nodos.values().forEach(nodo -> nodo.setExpanded(true));
        archivosTree.setRoot(raiz);
        Elemento seleccionado = menuList.getSelectionModel().getSelectedItem();
        if (seleccionado != null) seleccionarElementoEnArbol(raiz, seleccionado);
    }

    private void abrirAdjuntoSeleccionado() {
        TreeItem<Object> seleccionado = archivosTree.getSelectionModel().getSelectedItem();
        if (seleccionado == null || data == null) return;
        if (!(seleccionado.getValue() instanceof String)) return;
        String ruta = (String) seleccionado.getValue();
        File archivo = data.resolverArchivo(ruta).toFile();
        if (!archivo.isFile()) return;
        if (esLibroExcel(archivo)) {
            excelParaTabla = archivo;
            estado.setText("Libro listo para insertar: " + archivo.getName() + ". Pulsá Tabla.");
            return;
        }
        abrirArchivoEnPestana(archivo, ruta);
    }

    private ContextMenu crearMenuContextual(Object valor) {
        if (valor instanceof RaizExpediente) {
            MenuItem borrarTodo = new MenuItem("Vaciar expediente");
            borrarTodo.setOnAction(evento -> onEliminar());
            return new ContextMenu(borrarTodo);
        }
        if (valor instanceof Elemento) {
            Elemento elemento = (Elemento) valor;
            boolean raizElemento = archivosTree.getRoot() != null
                    && archivosTree.getRoot().getValue() == elemento;
            MenuItem eliminar = new MenuItem(raizElemento ? "Vaciar expediente" : "Eliminar expediente");
            eliminar.setOnAction(evento -> {
                if (raizElemento) onEliminar();
                else eliminarElemento(elemento);
            });
            return new ContextMenu(eliminar);
        }
        if (valor instanceof String) {
            String ruta = (String) valor;
            boolean carpeta = Files.isDirectory(data.resolverArchivo(ruta));
            MenuItem eliminar = new MenuItem(carpeta ? "Eliminar carpeta" : "Eliminar archivo");
            eliminar.setOnAction(evento -> eliminarAdjunto(ruta));
            return new ContextMenu(eliminar);
        }
        return null;
    }

    private void eliminarElemento(Elemento elemento) {
        int indice = menuList.getItems().indexOf(elemento);
        if (indice < 0) return;
        boolean seleccionado = menuList.getSelectionModel().getSelectedItem() == elemento;
        menuList.getItems().remove(elemento);
        if (seleccionado) {
            if (menuList.getItems().isEmpty()) vincularTexto(null);
            else menuList.getSelectionModel().select(Math.min(indice, menuList.getItems().size() - 1));
        }
        guardarDatos();
        refrescarArbolAdjuntos();
        estado.setText("Expediente eliminado");
    }

    private void eliminarAdjunto(String ruta) {
        try {
            Path rutaBorrada = data.resolverArchivo(ruta);
            if (excelParaTabla != null && (excelParaTabla.toPath().equals(rutaBorrada)
                    || excelParaTabla.toPath().startsWith(rutaBorrada))) excelParaTabla = null;
            data.eliminarAdjunto(ruta);
            String prefijo = ruta.endsWith("/") ? ruta : ruta + "/";
            for (Elemento elemento : menuList.getItems()) {
                elemento.getImagenes().removeIf(imagen -> imagen.equals(ruta) || imagen.startsWith(prefijo));
                if (ruta.equals(elemento.getFirma()) || elemento.getFirma().startsWith(prefijo)) {
                    elemento.setFirma("");
                    elemento.setAnchoFirmaCm(6.0);
                }
            }
            pestanas.getTabs().removeIf(pestana -> pestana.getUserData() instanceof String
                    && (pestana.getUserData().equals(ruta) || ((String) pestana.getUserData()).startsWith(prefijo)));
            guardarDatos();
            Elemento seleccionado = menuList.getSelectionModel().getSelectedItem();
            mostrarImagenes(seleccionado);
            mostrarFirma(seleccionado);
            refrescarArbolAdjuntos();
            estado.setText("Archivo o carpeta eliminados");
        } catch (IOException e) {
            showWarning("No se pudo eliminar el archivo: " + e.getMessage());
        }
    }

    private void seleccionarElementoEnArbol(TreeItem<Object> nodo, Elemento elemento) {
        if (nodo.getValue() == elemento) {
            archivosTree.getSelectionModel().select(nodo);
            return;
        }
        for (TreeItem<Object> hijo : nodo.getChildren()) seleccionarElementoEnArbol(hijo, elemento);
    }

    private void abrirArchivoEnPestana(File archivo, String ruta) {
        for (Tab pestana : pestanas.getTabs()) {
            if (ruta.equals(pestana.getUserData())) {
                pestanas.getSelectionModel().select(pestana);
                return;
            }
        }

        Tab pestana = new Tab(archivo.getName());
        pestana.setUserData(ruta);
        StackPane lienzo = new StackPane();
        lienzo.setMinSize(0, 0);
        lienzo.setStyle("-fx-background-color: #202124;");
        ImageView imagen = new ImageView(new Image(archivo.toURI().toString(), 1600, 1200, true, true, true));
        imagen.setPreserveRatio(false);
        imagen.setSmooth(true);
        imagen.fitWidthProperty().bind(lienzo.widthProperty());
        imagen.fitHeightProperty().bind(lienzo.heightProperty());
        lienzo.getChildren().add(imagen);
        pestana.setContent(lienzo);
        pestana.setClosable(true);
        pestanas.getTabs().add(pestana);
        pestanas.getSelectionModel().select(pestana);
    }

    private void mostrarImagenes(Elemento elemento) {
        imagenesInsertadas.getChildren().clear();
        if (elemento == null) return;
        for (String ruta : elemento.getImagenes()) {
            File archivo = data.resolverArchivo(ruta).toFile();
            if (!archivo.isFile()) continue;
            Image imagen = new Image(archivo.toURI().toString(), 104, 104, true, true, true);
            ImageView vista = new ImageView(imagen);
            vista.setPreserveRatio(false);
            vista.setFitWidth(104);
            vista.setFitHeight(104);
            vista.setSmooth(true);
            StackPane marco = new StackPane(vista);
            marco.setPrefSize(108, 108);
            marco.setMinSize(108, 108);
            marco.setMaxSize(108, 108);
            marco.setStyle("-fx-background-color: white; -fx-border-color: #b8b8b8; -fx-border-radius: 3;");
            Label nombre = new Label(archivo.getName());
            nombre.setMaxWidth(124);
            nombre.setWrapText(true);
            VBox tarjeta = new VBox(5, marco, nombre);
            tarjeta.setAlignment(Pos.TOP_CENTER);
            tarjeta.setPrefSize(124, 142);
            tarjeta.setMinSize(124, 142);
            tarjeta.setMaxSize(124, 142);
            tarjeta.setOnMouseClicked(evento -> {
                if (evento.getClickCount() == 2) abrirArchivoEnPestana(archivo, ruta);
            });

            MenuItem eliminar = new MenuItem("Eliminar imagen");
            eliminar.setOnAction(evento -> eliminarImagen(ruta));
            ContextMenu menu = new ContextMenu(eliminar);
            tarjeta.setOnContextMenuRequested(evento -> menu.show(tarjeta, evento.getScreenX(), evento.getScreenY()));
            imagenesInsertadas.getChildren().add(tarjeta);
        }
    }

    private void eliminarImagen(String ruta) {
        Elemento seleccionado = menuList.getSelectionModel().getSelectedItem();
        if (seleccionado != null) seleccionado.getImagenes().remove(ruta);
        boolean sigueEnUso = false;
        for (Elemento elemento : menuList.getItems()) {
            if (elemento != seleccionado && elemento.getImagenes().contains(ruta)) sigueEnUso = true;
        }
        if (!sigueEnUso) {
            try {
                data.eliminarImagen(ruta);
                cerrarPestana(ruta);
            } catch (IOException e) {
                showWarning("No se pudo eliminar la imagen: " + e.getMessage());
                return;
            }
        }
        guardarDatos();
        mostrarImagenes(menuList.getSelectionModel().getSelectedItem());
        refrescarArbolAdjuntos();
        estado.setText(sigueEnUso ? "Imagen quitada de este elemento" : "Imagen eliminada del expediente");
    }

    private void cerrarPestana(String ruta) {
        pestanas.getTabs().removeIf(pestana -> ruta.equals(pestana.getUserData()));
    }

    private void ajustarAlturaContenido(String texto) {
        int filas = 0;
        for (String linea : texto.split("\\R", -1)) {
            filas += Math.max(1, (int) Math.ceil(linea.length() / 72.0));
        }
        contenido.setPrefHeight(Math.max(58, Math.min(440, filas * 20 + 20)));
    }

    private Button botonPequeno(String texto, String codigoIcono, String color, Runnable accion) {
        Button boton = new Button(texto, icono(codigoIcono, 16, color));
        boton.setContentDisplay(ContentDisplay.LEFT);
        boton.setFocusTraversable(false);
        if (accion != null) {
            boton.setOnAction(evento -> accion.run());
        }
        return boton;
    }

    public void exportar(Window owner) {
        Elemento elegido = menuList.getSelectionModel().getSelectedItem();
        if (elegido == null) {
            showWarning("Elegí un expediente para exportar.");
            return;
        }
        List<File> imagenes = new ArrayList<>();
        for (String ruta : elegido.getImagenes()) {
            File archivo = data.resolverArchivo(ruta).toFile();
            if (archivo.isFile()) imagenes.add(archivo);
        }
        File firma = elegido.getFirma().isBlank()
                ? null : data.resolverArchivo(elegido.getFirma()).toFile();
        // Si la ventana de expediente está abierta, el diálogo debe colgar de ella:
        // esa ventana es always-on-top y un diálogo con otro owner quedaría detrás.
        Window duenio = ventanaPropia() != null ? ventanaPropia() : owner;
        DialogoExportacionExpediente.abrir(
                elegido.getNombre(), contenido.getText(), elegido.getTablas(), imagenes, disposicionImagenes,
                firma, elegido.getAnchoFirmaCm(), duenio);
    }

    /**
     * Abre una ventana para ponerle nombre al elemento y lo deja en la lista de la izquierda.
     */
    @FXML
    private void onAgregar() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuevo Expediente");
        dialog.setHeaderText(null);
        dialog.setContentText("Nro de expediente:");
        dialog.setGraphic(null);

        dialog.initOwner(pestanas.getScene().getWindow());
        dialog.setOnShown(evento -> {
            Window ventanaDialogo = dialog.getDialogPane().getScene().getWindow();
            if (ventanaDialogo instanceof Stage) {
                Stage stageDialogo = (Stage) ventanaDialogo;
                stageDialogo.setAlwaysOnTop(true);
                stageDialogo.toFront();
                stageDialogo.requestFocus();
            }
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String nombre = result.get().trim();
        if (nombre.isEmpty()) {
            showWarning("Escribí un número de expediente.");
            return;
        }

        boolean yaExiste = menuList.getItems().stream()
                .anyMatch(existente -> existente.getNombre().equalsIgnoreCase(nombre));
        if (yaExiste) {
            showWarning("Ya existe un expediente con ese número.");
            return;
        }

        Elemento elemento = new Elemento(nombre);
        observarCambios(elemento);
        menuList.getItems().add(elemento);
        guardarDatos();
        menuList.getSelectionModel().select(elemento);
        refrescarArbolAdjuntos();
        contenido.requestFocus();
        estado.setText("Expediente agregado: " + nombre);
    }

    @FXML
    private void onEliminar() {
        if (data == null) {
            return;
        }
        try {
            data.eliminarTodo();
            menuList.getItems().clear();
            vincularTexto(null);
            archivosTree.setRoot(null);
            pestanas.getTabs().removeIf(Tab::isClosable);
            estado.setText("Expediente vacío: 0 archivos");
        } catch (IOException e) {
            showWarning("No se pudo vaciar el expediente: " + e.getMessage());
        }
    }

    private void observarCambios(Elemento elemento) {
        elemento.textoProperty().addListener((obs, anterior, actual) -> guardarDatos());
    }

    private void guardarManual() {
        guardarDatos();
        if (data != null) data.marcarGuardado();
    }

    private void guardarDatos() {
        if (data == null) return;
        List<Data.Item> elementos = new ArrayList<>();
        for (Elemento elemento : menuList.getItems()) {
            elementos.add(new Data.Item(elemento.getNombre(), elemento.textoProperty().get(),
                    new ArrayList<>(elemento.getImagenes()), elemento.getFirma(),
                    elemento.getAnchoFirmaCm(), new ArrayList<>(elemento.getTablas())));
        }
        data.guardarElementos(elementos);
        estado.setText("Expediente guardado");
    }

    private void vincularTexto(Elemento elegido) {
        if (textoVinculado != null) {
            contenido.textProperty().unbindBidirectional(textoVinculado);
            textoVinculado = null;
        }
        if (elegido == null) {
            contenido.clear();
            contenido.setDisable(true);
            imagenesInsertadas.getChildren().clear();
            if (tablasInsertadas != null) {
                tablasInsertadas.getChildren().clear();
                tablasInsertadas.setVisible(false);
                tablasInsertadas.setManaged(false);
            }
            mostrarFirma(null);
            return;
        }
        contenido.setDisable(false);
        textoVinculado = elegido.textoProperty();
        contenido.textProperty().bindBidirectional(textoVinculado);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("No se pudo agregar");
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Sin owner, el aviso se dibuja como ventana independiente y queda detrás
        // de la ventana de expediente (que es always-on-top). Además, si no es
        // modal, se pueden apilar varios avisos superpuestos.
        Window owner = ventanaPropia();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOnShown(evento -> {
            Window ventanaAlerta = alert.getDialogPane().getScene().getWindow();
            if (ventanaAlerta instanceof Stage) {
                Stage stageAlerta = (Stage) ventanaAlerta;
                stageAlerta.setAlwaysOnTop(true);
                stageAlerta.toFront();
                stageAlerta.requestFocus();
            }
        });
        alert.showAndWait();
    }

    /** Ventana de la vista de expediente, o null si todavía no está en escena. */
    private Window ventanaPropia() {
        if (pestanas != null && pestanas.getScene() != null) {
            return pestanas.getScene().getWindow();
        }
        return null;
    }

    /**
     * Un ítem de la lista. El nombre se ve a la izquierda y el texto se escribe en el centro.
     */

    public static class Elemento {
        private final String nombre;
        private final StringProperty texto = new SimpleStringProperty("");
        private final List<String> imagenes = new ArrayList<>();
        private final List<String> tablas = new ArrayList<>();
        private String firma = "";
        private double anchoFirmaCm = 6.0;

        public Elemento(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }

        public StringProperty textoProperty() {
            return texto;
        }

        public List<String> getImagenes() {
            return imagenes;
        }

        public List<String> getTablas() {
            return tablas;
        }

        public String getFirma() {
            return firma;
        }

        public void setFirma(String firma) {
            this.firma = firma == null ? "" : firma;
        }

        public double getAnchoFirmaCm() {
            return anchoFirmaCm;
        }

        public void setAnchoFirmaCm(double anchoFirmaCm) {
            this.anchoFirmaCm = Math.max(3.0, Math.min(12.0, anchoFirmaCm));
        }

        @Override
        public String toString() {
            return nombre;
        }
    }

    private static final class RaizExpediente {
        private final String nombre;

        private RaizExpediente(String nombre) {
            this.nombre = nombre;
        }

        private String getNombre() {
            return nombre;
        }
    }
}


