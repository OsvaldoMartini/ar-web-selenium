package com.allinweb.ch.component.pane;

import javafx.scene.paint.Color;
import javafx.scene.text.Text;

final class ScannerCurrentUrlTextAdapter {

    Text build() {
        Text text = new Text("");
        text.setFill(Color.BLUE);
        text.setStyle("-fx-font-size: 16px;");
        return text;
    }
}
