module com.caa.app {
    requires transitive javafx.graphics;
    requires fr.brouillard.oss.cssfx;
    requires javafx.controls;
    requires com.calendarfx.view;
    requires java.desktop;
    requires javafx.fxml;
    requires fxribbon;
    requires com.dlsc.formsfx;
    requires org.apache.poi.ooxml;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    exports com.caa.app;
    opens com.caa.documentos to javafx.fxml;
}
