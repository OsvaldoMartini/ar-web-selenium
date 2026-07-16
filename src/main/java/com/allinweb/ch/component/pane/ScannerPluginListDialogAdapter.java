package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableView;

final class ScannerPluginListDialogAdapter {

    void show(
            ScannerPluginListContentAdapter.Result content,
            TableView<PluginDTO> table,
            List<PluginDTO> allPlugins,
            Runnable onNoSelection,
            Consumer<List<PluginDTO>> onDownload) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Plugin Test");
        dialog.setHeaderText("Available Plugins");
        dialog.getDialogPane().setContent(content.content());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);

        content.close().setOnAction(e -> dialog.close());

        content.downloadSelected().setOnAction(e -> {
            List<PluginDTO> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                onNoSelection.run();
                return;
            }
            dialog.close();
            onDownload.accept(selected);
        });

        content.downloadAll().setOnAction(e -> {
            dialog.close();
            onDownload.accept(new ArrayList<>(allPlugins));
        });

        dialog.showAndWait();
    }
}
