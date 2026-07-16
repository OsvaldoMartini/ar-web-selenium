package com.allinweb.ch.component.pane;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

final class ScannerPluginStatusButtonAdapter {

    private static final String READY_STYLE = "-fx-background-color: #166534;"
            + "-fx-text-fill: #dcfce7;"
            + "-fx-font-size: 12px;"
            + "-fx-font-weight: bold;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 6 14 6 14;"
            + "-fx-cursor: hand;";

    private static final String WARNING_STYLE = "-fx-background-color: #7c2d12;"
            + "-fx-text-fill: #fed7aa;"
            + "-fx-font-size: 12px;"
            + "-fx-font-weight: bold;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 6 14 6 14;"
            + "-fx-cursor: hand;";

    Button build(int installed, int total, Runnable onOpen) {
        Button button = new Button();
        button.setVisible(false);

        if (total > 0 && installed == total) {
            button.setText("\u2B24  Plugin Update (" + installed + "/" + total + ")");
            button.setStyle(READY_STYLE);
            button.setTooltip(new Tooltip("All " + installed + " plugins installed - click to manage"));
        } else if (total > 0) {
            button.setText("\u26A0  Plugin Update (" + installed + "/" + total + ")");
            button.setStyle(WARNING_STYLE);
            button.setTooltip(new Tooltip(installed + " of " + total + " plugins installed - click to download missing"));
        } else {
            button.setText("\u26A0  Plugin Update");
            button.setStyle(WARNING_STYLE);
            button.setTooltip(new Tooltip("No plugins found - click to scan or download"));
        }

        button.setOnAction(e -> onOpen.run());
        return button;
    }
}
