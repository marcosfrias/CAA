module com.eia.app {
    requires transitive javafx.graphics;
    requires fr.brouillard.oss.cssfx;
    requires javafx.controls;
    requires com.calendarfx.view;
    requires java.desktop;
    requires javafx.fxml;
    requires fxribbon;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    exports com.eia.app;
    opens com.eia.documentos to javafx.fxml;
}
