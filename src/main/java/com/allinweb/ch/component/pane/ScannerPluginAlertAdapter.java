package com.allinweb.ch.component.pane;

import javafx.application.Platform;
import javafx.scene.control.Alert;

final class ScannerPluginAlertAdapter {

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
