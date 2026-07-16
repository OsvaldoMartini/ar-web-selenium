package com.allinweb.ch.component.pane;

import javafx.scene.control.Label;

final class ScannerFieldLabelsAdapter {

    Label searchTerms() {
        return new Label("Search by :");
    }

    Label elementFocus() {
        return new Label("Focus :");
    }

    Label defineName() {
        return new Label("DEFINE ELEMENT NAME");
    }

    Label coordinates() {
        return new Label("Main Coordinates");
    }

    Label definedName(String placeholder) {
        Label label = new Label(placeholder);
        label.setStyle("-fx-border-color: #9aa0a6; "
                + "-fx-border-radius: 3; "
                + "-fx-background-color: #f8f9fa; "
                + "-fx-background-radius: 3; "
                + "-fx-padding: 4 8 4 8; "
                + "-fx-text-fill: #202124;");
        return label;
    }
}
