package com.allinweb.ch.component.pane;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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

    GridPane scannerTopGrid() {
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10);
        return gridPane;
    }

    HBox pageScannerRow(Node... children) {
        HBox row = new HBox(6, children);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    VBox checkboxColumn(Node... children) {
        VBox column = new VBox();
        column.getChildren().addAll(children);
        column.setSpacing(6);
        return column;
    }

    VBox scannerContentColumn() {
        VBox column = new VBox();
        column.setSpacing(10);
        column.setPadding(new Insets(10));
        VBox.setVgrow(column, Priority.ALWAYS);
        return column;
    }

    HBox launchButtonRow(Node... children) {
        HBox row = new HBox();
        row.setSpacing(10);
        row.getChildren().addAll(children);
        return row;
    }

    HBox spacedRow(double spacing, Node... children) {
        HBox row = new HBox();
        row.setSpacing(spacing);
        row.getChildren().addAll(children);
        return row;
    }

    VBox textFieldColumn(Node... children) {
        VBox column = new VBox();
        column.setSpacing(6);
        column.getChildren().addAll(children);
        return column;
    }

    HBox centeredBox(Node child) {
        StackPane stack = new StackPane();
        stack.getChildren().add(child);
        stack.setAlignment(Pos.CENTER);
        return new HBox(stack);
    }

    StackPane centeredStack(Node child) {
        StackPane stack = new StackPane();
        stack.getChildren().add(child);
        stack.setAlignment(Pos.CENTER);
        return stack;
    }

    HBox listViewsRow() {
        HBox row = new HBox();
        row.setSpacing(5);
        VBox.setVgrow(row, Priority.ALWAYS);
        HBox.setHgrow(row, Priority.ALWAYS);
        return row;
    }

    VBox elementsColumn(Node header, Node content) {
        VBox column = new VBox(header, content);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }
}
