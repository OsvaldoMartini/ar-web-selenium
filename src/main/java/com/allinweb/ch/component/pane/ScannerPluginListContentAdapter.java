package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import com.allinweb.ch.model.PluginManifestDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class ScannerPluginListContentAdapter {

    Result build(PluginManifestDTO manifest, Node table) {
        String headerText = "Plugin List  \u00B7  manifest v" + manifest.getVersion()
                + (manifest.getUpdated() != null ? "  \u00B7  updated " + manifest.getUpdated() : "");
        Label headerLabel = new Label(headerText);
        headerLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #636e72;");

        Label infoLabel = new Label("Select one or more plugins, then click Download Selected.");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #636e72;");

        Button downloadSelected = new Button("\u2B07  Download Selected");
        Button downloadAll = new Button("\u2B07  Download All");
        Button close = new Button("Close");

        downloadSelected.setDefaultButton(false);
        downloadAll.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white;");
        close.setCancelButton(true);

        HBox buttonBar = new HBox(10, downloadSelected, downloadAll, close);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        VBox content = new VBox(8, headerLabel, table, infoLabel, buttonBar);
        content.setPadding(new Insets(12));
        content.setPrefWidth(680);
        return new Result(content, downloadSelected, downloadAll, close);
    }

    record Result(VBox content, Button downloadSelected, Button downloadAll, Button close) {}
}
