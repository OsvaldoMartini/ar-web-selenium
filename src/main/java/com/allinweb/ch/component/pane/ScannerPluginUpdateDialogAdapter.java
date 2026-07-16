package com.allinweb.ch.component.pane;

import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;

final class ScannerPluginUpdateDialogAdapter {

    boolean show(VBox content, boolean hasDownloads) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Plugin Update");
        dialog.setHeaderText("Plugins Status");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(580);

        if (!hasDownloads) {
            dialog.getButtonTypes().setAll(ButtonType.CLOSE);
            dialog.showAndWait();
            return false;
        }

        ButtonType downloadButton = new ButtonType("Download Selected", ButtonBar.ButtonData.OK_DONE);
        dialog.getButtonTypes().setAll(downloadButton, ButtonType.CLOSE);

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }
}
