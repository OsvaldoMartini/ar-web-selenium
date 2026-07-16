package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

final class ScannerPluginListDialogAdapter {

    void show(
            ScannerPluginListContentAdapter.Result content,
            Supplier<List<PluginDTO>> selectedPlugins,
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
            List<PluginDTO> selected = selectedPlugins.get();
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
