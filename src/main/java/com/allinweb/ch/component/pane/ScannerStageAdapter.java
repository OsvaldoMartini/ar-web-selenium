package com.allinweb.ch.component.pane;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

final class ScannerStageAdapter {

    void closeOwnerWindow(Node node) {
        if (node == null || node.getScene() == null) {
            return;
        }
        closeWindow(node.getScene().getWindow());
    }

    void close(Stage stage, Runnable afterClose) {
        if (stage == null) {
            return;
        }
        Platform.runLater(() -> {
            stage.close();
            if (afterClose != null) {
                afterClose.run();
            }
        });
    }

    private void closeWindow(Window window) {
        if (window instanceof Stage stage) {
            stage.close();
        }
    }
}
