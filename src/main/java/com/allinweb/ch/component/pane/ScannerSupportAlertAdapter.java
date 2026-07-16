package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.facade.ScannerSupportCaptureResultService;
import com.allinweb.ch.facade.ScannerSupportSavedFileMessageService;
import javafx.application.Platform;
import javafx.scene.control.Alert;

final class ScannerSupportAlertAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void showNoActiveBrowser() {
        show(
                ScannerDialogPublisher.Severity.INFO,
                Alert.AlertType.INFORMATION,
                "Support",
                "No active browser session",
                "There is no open browser to capture.");
    }

    void showCaptureResult(ScannerSupportCaptureResultService.AlertMessage message) {
        show(
                message.ok() ? ScannerDialogPublisher.Severity.INFO : ScannerDialogPublisher.Severity.ERROR,
                message.ok() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                "Support",
                message.header(),
                message.content());
    }

    void showSavedFile(ScannerSupportSavedFileMessageService.Message message) {
        show(
                ScannerDialogPublisher.Severity.INFO,
                Alert.AlertType.INFORMATION,
                "Support",
                message.header(),
                message.content());
    }

    private void show(
            ScannerDialogPublisher.Severity severity,
            Alert.AlertType fallbackType,
            String title,
            String header,
            String body) {
        if (dialogPublisher.alert(severity, title, header, body)) {
            return;
        }
        Platform.runLater(() -> {
            Alert alert = new Alert(fallbackType);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(body);
            alert.showAndWait();
        });
    }
}
