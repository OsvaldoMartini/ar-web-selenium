package com.allinweb.ch.component.pane;

import javafx.scene.control.TextArea;

final class ScannerPreLaunchStatusTextAreaAdapter {

    TextArea build() {
        TextArea textArea = new TextArea("Pre-Launch status: Ready");
        textArea.setStyle("-fx-font-size: 12px; -fx-text-fill: blue;");
        textArea.setEditable(true);
        return textArea;
    }
}
