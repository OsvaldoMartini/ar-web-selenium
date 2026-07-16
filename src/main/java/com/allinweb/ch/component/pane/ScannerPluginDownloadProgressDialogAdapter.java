package com.allinweb.ch.component.pane;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

final class ScannerPluginDownloadProgressDialogAdapter {

    private Alert progressDialog;

    void bind(String pluginName, Task<?> downloadTask) {
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(350);

        Label statusLabel = new Label("Downloading " + pluginName + "...");
        statusLabel.setStyle("-fx-font-size: 12px;");

        VBox dialogContent = new VBox(10, statusLabel, progressBar);
        dialogContent.setPadding(new Insets(15));

        progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle("Download Plugin");
        progressDialog.setHeaderText("Downloading: " + pluginName);
        progressDialog.getDialogPane().setContent(dialogContent);
        progressDialog.getButtonTypes().setAll(ButtonType.CANCEL);
        progressDialog.setOnCloseRequest(e -> downloadTask.cancel());

        progressBar.progressProperty().bind(downloadTask.progressProperty());
        statusLabel.textProperty().bind(downloadTask.messageProperty());
    }

    void show() {
        progressDialog.show();
    }

    void close() {
        progressDialog.close();
    }
}
