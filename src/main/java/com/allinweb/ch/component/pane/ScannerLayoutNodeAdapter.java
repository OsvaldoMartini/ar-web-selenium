package com.allinweb.ch.component.pane;

import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

final class ScannerLayoutNodeAdapter {

    Node verticalSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    Node horizontalSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    Separator separator(Color color, double width) {
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setValignment(VPos.CENTER);
        separator.setPrefHeight(width);
        separator.setStyle("-fx-background-color: " + color.toString().replace("0x", "#") + ";");
        return separator;
    }
}
