package com.allinweb.ch.component.pane;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class ScannerPluginUpdateContentAdapter {

    VBox build(VBox tableBox, String pluginsDir) {
        Label folderLabel = infoLabel("Plugins folder:  " + pluginsDir);
        Label addLabel = infoLabel("Plugins can be added via:  Download from server, email, or USB/pendrive copy.");

        VBox content = new VBox(10, tableBox, folderLabel, addLabel);
        content.setPadding(new Insets(10));
        return content;
    }

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#1565C0;");
        label.setPrefWidth(500);
        return label;
    }
}
