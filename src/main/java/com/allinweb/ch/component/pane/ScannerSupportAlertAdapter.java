package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerSupportCaptureResultService;
import com.allinweb.ch.facade.ScannerSupportSavedFileMessageService;
import javafx.scene.control.Alert;

final class ScannerSupportAlertAdapter {

    void showCaptureResult(ScannerSupportCaptureResultService.AlertMessage message) {
        Alert alert = new Alert(message.ok() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setHeaderText(message.header());
        alert.setContentText(message.content());
        alert.showAndWait();
    }

    void showSavedFile(ScannerSupportSavedFileMessageService.Message message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(message.header());
        alert.setContentText(message.content());
        alert.showAndWait();
    }
}
