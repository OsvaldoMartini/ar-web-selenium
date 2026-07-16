package com.allinweb.ch.component.pane;

import javafx.application.Platform;
import javafx.scene.control.Alert;

final class ScannerPluginAlertAdapter {

    void warning(String header, String body) {
        show(Alert.AlertType.WARNING, header, body);
    }

    void error(String header, String body) {
        show(Alert.AlertType.ERROR, header, body);
    }

    void information(String header, String body) {
        show(Alert.AlertType.INFORMATION, header, body);
    }

    void show(Alert.AlertType type, String header, String body) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle("Plugin Test");
            alert.setHeaderText(header);
            alert.setContentText(body);
            alert.showAndWait();
        });
    }
}
