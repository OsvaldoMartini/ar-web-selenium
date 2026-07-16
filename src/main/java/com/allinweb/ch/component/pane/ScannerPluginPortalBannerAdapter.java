package com.allinweb.ch.component.pane;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class ScannerPluginPortalBannerAdapter {

    private static final String PORTAL_URL = "https://www.multiplugins.ch/portal";

    VBox build(boolean noPlugins, Consumer<String> openUrl) {
        VBox banner = new VBox(6);
        banner.setPadding(new Insets(10, 12, 10, 12));
        banner.setStyle("-fx-background-color:#fff7ed;"
                + "-fx-border-color:#fb923c;"
                + "-fx-border-width:1;"
                + "-fx-background-radius:6;"
                + "-fx-border-radius:6;");

        String title = noPlugins ? "No plugins installed yet" : "Some mandatory plugins are missing";
        Label head = new Label("\u26A0  " + title);
        head.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#9a3412;");

        Label body = new Label(
                noPlugins
                        ? "To run the scanner you need to install the plugin bundle first."
                        : "The scanner requires all plugins to be present to work correctly.");
        body.setStyle("-fx-font-size:12px;-fx-text-fill:#7c2d12;");
        body.setWrapText(true);

        HBox linkRow = new HBox(6);
        Label prompt = new Label("Get them from the portal:");
        prompt.setStyle("-fx-font-size:12px;-fx-text-fill:#7c2d12;");

        Hyperlink portalLink = new Hyperlink("www.multiplugins.ch");
        portalLink.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#0b5394;-fx-padding:0;");
        portalLink.setOnAction(e -> openUrl.accept(PORTAL_URL));

        linkRow.getChildren().addAll(prompt, portalLink);
        linkRow.setVisible(false);
        linkRow.setManaged(false);
        banner.getChildren().addAll(head, body, linkRow);
        return banner;
    }
}
