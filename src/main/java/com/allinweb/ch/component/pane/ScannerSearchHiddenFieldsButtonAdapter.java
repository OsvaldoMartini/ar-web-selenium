package com.allinweb.ch.component.pane;

import javafx.scene.control.Button;

final class ScannerSearchHiddenFieldsButtonAdapter {

    Button build() {
        Button button = new Button("Search Hidden Fields: Off");
        button.setStyle("-fx-background-color: grey; -fx-text-fill: white;");
        return button;
    }
}
