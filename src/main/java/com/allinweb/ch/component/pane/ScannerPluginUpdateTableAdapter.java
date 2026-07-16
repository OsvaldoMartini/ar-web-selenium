package com.allinweb.ch.component.pane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class ScannerPluginUpdateTableAdapter {

    Result build(List<String[]> rows, boolean serverConfigured) {
        VBox tableBox = new VBox(4);
        tableBox.setPadding(new Insets(5));

        HBox header = new HBox(10);
        header.getChildren()
                .addAll(
                        headerLabel("Plugin", 140),
                        headerLabel("Version", 60),
                        headerLabel("Size", 60),
                        headerLabel("Status", 80));
        tableBox.getChildren().add(header);

        List<DownloadSelection> downloadSelections = new ArrayList<>();

        for (String[] row : rows) {
            HBox line = new HBox(10);
            Label name = rowLabel(row[1], 140);
            Label version = rowLabel(row[2], 60);
            Label size = rowLabel(row[3], 60);
            Label status = rowLabel("", 80);
            status.setStyle(status.getStyle() + "-fx-font-weight:bold;");

            if ("LOCAL".equals(row[5])) {
                status.setText("\u2713 Installed");
                status.setStyle(status.getStyle() + "-fx-text-fill:#166534;");
                line.getChildren().addAll(name, version, size, status);
            } else {
                status.setText("\u2717 Missing");
                status.setStyle(status.getStyle() + "-fx-text-fill:#dc2626;");
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(true);
                if (serverConfigured && !row[4].isEmpty()) {
                    checkBox.setDisable(false);
                    downloadSelections.add(new DownloadSelection(row, checkBox::isSelected));
                } else {
                    checkBox.setDisable(true);
                }
                line.getChildren().addAll(checkBox, name, version, size, status);
            }
            tableBox.getChildren().add(line);
        }

        return new Result(tableBox, downloadSelections);
    }

    private static Label headerLabel(String text, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setStyle("-fx-font-weight:bold;-fx-font-size:12px;");
        return label;
    }

    private static Label rowLabel(String text, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setStyle("-fx-font-size:12px;");
        return label;
    }

    record Result(VBox tableBox, List<DownloadSelection> downloadSelections) {}

    record DownloadSelection(String[] row, BooleanSupplier selected) {

        boolean isSelected() {
            return selected.getAsBoolean();
        }
    }
}
