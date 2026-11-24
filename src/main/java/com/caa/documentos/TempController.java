package com.caa.documentos;

import com.caa.app.Almacenamiento;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Slider;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ListView;
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
import javafx.stage.Stage;
import javafx.stage.Window;

import com.pixelduke.control.ribbon.RibbonGroup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TempController {

    private Data data;
    private String idExpediente;
    private DisposicionImagenes disposicionImagenes = DisposicionImagenes.PREDETERMINADA;

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
                    String codigo = carpeta ? "fas-folder" : "fas-file-image";
                    setText(nombre);
                    setGraphic(icono(codigo, 14, carpeta ? "#e7a12c" : "#6b7785"));
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
        data = new Data(idExpediente);
        menuList.getItems().clear();
        contenido.clear();
        contenido.setDisable(true);
        for (Data.Item item : data.cargarElementos()) {
            Elemento elemento = new Elemento(item.getNombre());
            elemento.textoProperty().set(item.getTexto());
            elemento.getImagenes().addAll(item.getImagenes());
            elemento.setFirma(item.getFirma());
            elemento.setAnchoFirmaCm(item.getAnchoFirmaCm());
            observarCambios(elemento);
            menuList.getItems().add(elemento);
        }
        if (!menuList.getItems().isEmpty()) {
            menuList.getSelectionModel().selectFirst();
        }
        refrescarArbolAdjuntos();
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

        // --- Grupo Archivo (sin acciones por ahora) ---
        RibbonGroup grupoArchivo = new RibbonGroup();
        grupoArchivo.setTitle("Archivo");
        grupoArchivo.getNodes().addAll(
                botonGrande("Abrir", "fas-folder-open", "#f5a623", this::onAbrir),
                botonGrande("Nuevo", "fas-file", "#5aa9e6", this::onNuevoInforme),
                botonGrande("Guardar", "fas-save", "#2a7de1", this::guardarManual)
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
        grupoImagenes.getNodes().add(
                botonGrande("Imágen", "fas-image", "#2a7de1", this::onImagenes));

        RibbonGroup grupoTabla = new RibbonGroup();
        grupoTabla.setTitle("Tabla");
        grupoTabla.getNodes().add(botonGrande("Tabla", "fas-table", "#2a7de1", () ->
                DialogoDisposicionImagenes.abrir(pestanas.getScene().getWindow(), disposicionImagenes,
                        opciones -> disposicionImagenes = opciones)));

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

    private void onAyuda() {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Ayuda");
        alerta.setHeaderText("Cómo usar el expediente");
        alerta.setContentText(
                "Seleccioná un expediente de la lista para editar su texto.\n"
                        + "Abrí imágenes y después usá Imágen para insertarlas debajo del texto.\n"
                        + "Tabla permite elegir la disposición de las imágenes.\n"
                        + "En Firmas podés subir una imagen, dibujar y ajustar su tamaño al pie.\n"
                        + "Pulsá Guardar para habilitar la opción Exportar en el evento.\n\n"
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
        selector.setTitle("Agregar imágenes al expediente");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        List<File> seleccionadas = selector.showOpenMultipleDialog(pestanas.getScene().getWindow());
        if (seleccionadas == null || seleccionadas.isEmpty()) return;

        try {
            for (File imagen : seleccionadas) data.copiarImagen(imagen);
            refrescarArbolAdjuntos();
            estado.setText("Imágenes agregadas al expediente: " + seleccionadas.size());
        } catch (IllegalArgumentException | IllegalStateException e) {
            showWarning("No se pudieron agregar las imágenes: " + e.getMessage());
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
        DialogoExportacionExpediente.abrir(
                elegido.getNombre(), contenido.getText(), imagenes, disposicionImagenes,
                firma, elegido.getAnchoFirmaCm(), owner);
    }

    /**
     * Lee el formulario.tsv del expediente actual y devuelve un mapa clave→valor decodificado.
     */
    private Map<String, String> leerFormulario() {
        Map<String, String> valores = new LinkedHashMap<>();
        if (idExpediente == null) return valores;
        Path archivo = Almacenamiento.carpetaDatos().resolve("expedientes")
                .resolve(idExpediente).resolve("formulario.tsv");
        if (!Files.exists(archivo)) return valores;
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String[] partes = linea.split("\t", -1);
                if (partes.length == 2) {
                    String clave = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
                    String valor = URLDecoder.decode(partes[1], StandardCharsets.UTF_8);
                    valores.put(clave, valor);
                }
            }
        } catch (IOException e) { /* ignorar */ }
        return valores;
    }

    /**
     * Genera el texto de un informe legal a partir de los valores del formulario.
     */
    private String generarInformeLegal(Map<String, String> v) {
        StringBuilder sb = new StringBuilder();
        String empresa    = v.getOrDefault("company", "");
        String expediente = v.getOrDefault("fileNumber", "");
        String actividad  = v.getOrDefault("actividad", "");
        String email      = v.getOrDefault("email", "");
        String obs        = v.getOrDefault("observations", "");

        // Encabezado formal
        sb.append("                    INFORME DE AUDITORÍA AMBIENTAL\n\n\n");

        // Identificación del expediente
        sb.append("I.   IDENTIFICACIÓN\n\n");
        sb.append("     Empresa:          ").append(empresa).append("\n");
        sb.append("     Actividad:        ").append(actividad).append("\n");
        sb.append("     Expediente N.°:   ").append(expediente).append("\n");
        if (!email.isBlank())
            sb.append("     Contacto:         ").append(email).append("\n");
        sb.append("\n\n");

        // Auditorías
        sb.append("II.  AUDITORÍAS REALIZADAS\n\n");
        boolean hayAuditorias = false;
        for (int i = 1; ; i++) {
            String fecha = v.get("auditoriaFecha" + i);
            if (fecha == null || fecha.isBlank()) break;
            hayAuditorias = true;
            String consultor = v.getOrDefault("auditoriaConsultor" + i, "");
            String auditor   = v.getOrDefault("auditoriaAuditor" + i, "");
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

        // Resoluciones
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
            if ("true".equalsIgnoreCase(cert)) sb.append("  (Certificó)");
            sb.append("\n");
        }
        if (!hayResoluciones)
            sb.append("     Sin resoluciones registradas.\n");
        sb.append("\n\n");

        // Notificaciones
        sb.append("IV.  NOTIFICACIONES\n\n");
        boolean hayNotif = false;
        for (int i = 1; ; i++) {
            String notif = v.get("notificacion" + i);
            if (notif == null || notif.isBlank()) break;
            hayNotif = true;
            sb.append("     ").append(i).append(". ").append(notif).append("\n");
        }
        if (!hayNotif)
            sb.append("     Sin notificaciones registradas.\n");
        sb.append("\n\n");

        // Comunicación / Nota
        String notaNum = v.getOrDefault("notaNumero", "");
        if (!notaNum.isBlank()) {
            sb.append("V.   COMUNICACIÓN OFICIAL\n\n");
            sb.append("     Nota N.°:         ").append(notaNum);
            String notaLetra = v.getOrDefault("notaLetra", "");
            if (!notaLetra.isBlank()) sb.append("  (").append(notaLetra).append(")");
            sb.append("\n");
            String notaFecha = v.getOrDefault("notaFecha", "");
            if (!notaFecha.isBlank())
                sb.append("     Fecha:            ").append(notaFecha).append("\n");
            String tema = v.getOrDefault("notaTema", "");
            if (!tema.isBlank())
                sb.append("     Tema:             ").append(tema).append("\n");
            String tipo = v.getOrDefault("notaTipo", "");
            if (!tipo.isBlank())
                sb.append("     Tipo:             ").append(tipo).append("\n");
            String respFecha = v.getOrDefault("respuestaFecha", "");
            if (!respFecha.isBlank())
                sb.append("     Fecha respuesta:  ").append(respFecha).append("\n");
            String notaRespuesta = v.getOrDefault("notaRespuesta", "");
            if (!notaRespuesta.isBlank())
                sb.append("     Respuesta:        ").append(notaRespuesta).append("\n");
            sb.append("\n\n");
        }

        // Observaciones
        if (!obs.isBlank()) {
            int seccion = notaNum.isBlank() ? 5 : 6;
            sb.append(seccion == 5 ? "V." : "VI.").append("   OBSERVACIONES\n\n");
            // Sangría en cada línea de observaciones
            for (String linea : obs.split("\\R", -1)) {
                sb.append("     ").append(linea).append("\n");
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Crea o actualiza un elemento "Informe Legal" con el contenido generado desde el formulario.
     */
    private void onNuevoInforme() {
        if (idExpediente == null || data == null) {
            showWarning("No hay un expediente cargado.");
            return;
        }
        Map<String, String> valores = leerFormulario();
        if (valores.isEmpty()) {
            showWarning("No se encontraron datos del formulario para este expediente.\nCompletá el Formulario primero.");
            return;
        }
        String informe = generarInformeLegal(valores);
        String nombreElemento = "Informe Legal";

        Elemento existente = menuList.getItems().stream()
                .filter(e -> nombreElemento.equalsIgnoreCase(e.getNombre()))
                .findFirst().orElse(null);

        if (existente != null) {
            existente.textoProperty().set(informe);
            menuList.getSelectionModel().select(existente);
        } else {
            Elemento elemento = new Elemento(nombreElemento);
            elemento.textoProperty().set(informe);
            observarCambios(elemento);
            menuList.getItems().add(elemento);
            menuList.getSelectionModel().select(elemento);
        }
        guardarDatos();
        refrescarArbolAdjuntos();
        estado.setText("Informe legal generado desde el formulario.");
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
                    elemento.getAnchoFirmaCm()));
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
        alert.showAndWait();
    }

    /**
     * Un ítem de la lista. El nombre se ve a la izquierda y el texto se escribe en el centro.
     */

    public static class Elemento {
        private final String nombre;
        private final StringProperty texto = new SimpleStringProperty("");
        private final List<String> imagenes = new ArrayList<>();
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


