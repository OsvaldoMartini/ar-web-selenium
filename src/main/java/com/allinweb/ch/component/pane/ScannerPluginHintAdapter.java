package com.allinweb.ch.component.pane;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

final class ScannerPluginHintAdapter {

    Label createLabel() {
        Label label = new Label();
        label.setStyle("-fx-font-size: 11px;");
        label.setVisible(false);
        label.setManaged(false);
        label.setWrapText(true);
        return label;
    }

    void show(Label label, String message, String color, double seconds) {
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
}
