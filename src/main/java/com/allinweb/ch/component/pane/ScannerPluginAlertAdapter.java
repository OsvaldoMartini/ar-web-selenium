package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import javafx.application.Platform;
import javafx.scene.control.Alert;

final class ScannerPluginAlertAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void warning(String header, String body) {
        show(Alert.AlertType.WARNING, ScannerDialogPublisher.Severity.WARNING, header, body);
    }

    void error(String header, String body) {
        show(Alert.AlertType.ERROR, ScannerDialogPublisher.Severity.ERROR, header, body);
    }

    void information(String header, String body) {
        show(Alert.AlertType.INFORMATION, ScannerDialogPublisher.Severity.INFO, header, body);
    }

    void show(Alert.AlertType type, ScannerDialogPublisher.Severity severity, String header, String body) {
        if (dialogPublisher.alert(severity, "Plugin Test", header, body)) {
            return;
        }
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle("Plugin Test");
            alert.setHeaderText(header);
            alert.setContentText(body);
            alert.showAndWait();
        });
    }
}
