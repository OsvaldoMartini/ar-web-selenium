package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import javafx.scene.control.Label;

final class ScannerPluginHintAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    Label createLabel() {
        Label label = new Label();
        label.setStyle("-fx-font-size: 11px;");
        label.setVisible(false);
        label.setManaged(false);
        label.setWrapText(true);
        return label;
    }

    void show(Label label, String message, String color, double seconds) {
        dialogPublisher.toast(severityFor(color), message, seconds);
    }

    private ScannerDialogPublisher.Severity severityFor(String color) {
        if ("#f44336".equalsIgnoreCase(color)) {
            return ScannerDialogPublisher.Severity.ERROR;
        }
        if ("#ff9800".equalsIgnoreCase(color)) {
            return ScannerDialogPublisher.Severity.WARNING;
        }
        return ScannerDialogPublisher.Severity.INFO;
    }
}
