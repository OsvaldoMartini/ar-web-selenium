package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

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
        if (dialogPublisher.toast(severityFor(color), message, seconds)) {
            return;
        }
        Platform.runLater(() -> {
            label.setText(message);
            label.setStyle(
                    "-fx-font-size: 11px; -fx-padding: 0 0 0 10; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
            label.setOpacity(1.0);
            label.setVisible(true);
            label.setManaged(true);

            PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
            pause.setOnFinished(ev -> {
                FadeTransition fade = new FadeTransition(Duration.seconds(1.5), label);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(fe -> {
                    label.setVisible(false);
                    label.setManaged(false);
                });
                fade.play();
            });
            pause.play();
        });
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
