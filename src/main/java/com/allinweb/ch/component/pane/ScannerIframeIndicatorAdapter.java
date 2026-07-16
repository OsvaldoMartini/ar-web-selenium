package com.allinweb.ch.component.pane;

import javafx.scene.text.Text;

final class ScannerIframeIndicatorAdapter {

    Text build() {
        Text text = new Text("");
        text.setStyle("-fx-font-size: 12px; -fx-fill: blue;");
        return text;
    }
}
