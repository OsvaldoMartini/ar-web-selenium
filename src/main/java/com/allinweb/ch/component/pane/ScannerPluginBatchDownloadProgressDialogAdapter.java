package com.allinweb.ch.component.pane;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

final class ScannerPluginBatchDownloadProgressDialogAdapter {

    private Alert progressDialog;
    private Label counterLabel;

    void bind(int totalPlugins, Task<?> downloadTask) {
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);

        Label statusLabel = new Label("Starting...");
        counterLabel = new Label("0 / " + totalPlugins);
        statusLabel.setStyle("-fx-font-size: 12px;");
        counterLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #636e72;");

        VBox dialogContent = new VBox(8, statusLabel, progressBar, counterLabel);
        dialogContent.setPadding(new Insets(12));

        progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle("Plugin Test");
        progressDialog.setHeaderText("Downloading Plugins");
        progressDialog.getDialogPane().setContent(dialogContent);
        progressDialog.getButtonTypes().setAll(ButtonType.CANCEL);
        progressDialog.setOnCloseRequest(e -> downloadTask.cancel());

        progressBar.progressProperty().bind(downloadTask.progressProperty());
        statusLabel.textProperty().bind(downloadTask.messageProperty());
    }

    void updateCounter(int current, int total) {
        Platform.runLater(() -> counterLabel.setText(current + " / " + total));
    }

    void show() {
        progressDialog.show();
    }

    void close() {
        progressDialog.close();
    }
}
