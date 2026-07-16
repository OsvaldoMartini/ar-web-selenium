package com.allinweb.ch.component.pane;

import javafx.scene.control.TextField;

final class ScannerTextFieldsAdapter {

    TextField searchTerms(String defaultText) {
        TextField textField = new TextField();
        textField.setPromptText("button, label, input, with id, with text");
        textField.setPrefWidth(300);
        textField.setText(defaultText);
        return textField;
    }

    TextField searchAttribute() {
        TextField textField = new TextField();
        textField.setPromptText("Search per Attrib");
        return textField;
    }

    TextField coordinates() {
        TextField textField = new TextField();
        textField.setPromptText("Coordinates");
        return textField;
    }
}
