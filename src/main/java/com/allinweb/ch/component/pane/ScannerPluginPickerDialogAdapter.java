package com.allinweb.ch.component.pane;

import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class ScannerPluginPickerDialogAdapter {

    Optional<Selection> show(List<String[]> plugins) {
        ComboBox<String> comboBox = new ComboBox<>();
        for (String[] plugin : plugins) {
            comboBox.getItems().add(labelFor(plugin));
        }
        comboBox.getSelectionModel().selectFirst();
        comboBox.setPrefWidth(400);

        Label descLabel = new Label(plugins.get(0)[1]);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        descLabel.setPrefWidth(400);

        comboBox.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            int idx = newVal.intValue();
            if (idx >= 0 && idx < plugins.size()) {
                descLabel.setText(plugins.get(idx)[1]);
            }
        });

        VBox content = new VBox(8, new Label("Select a plugin to download:"), comboBox, descLabel);
        content.setPadding(new Insets(10));

        Alert pickerDialog = new Alert(Alert.AlertType.CONFIRMATION);
        pickerDialog.setTitle("Download Plugin");
        pickerDialog.setHeaderText("Available Plugins");
        pickerDialog.getDialogPane().setContent(content);
        pickerDialog
                .getButtonTypes()
                .setAll(new ButtonType("Download", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);

        Optional<ButtonType> result = pickerDialog.showAndWait();
        if (result.isEmpty() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return Optional.empty();
        }

        int selectedIndex = comboBox.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= plugins.size()) {
            return Optional.empty();
        }

        String[] selected = plugins.get(selectedIndex);
        return Optional.of(new Selection(selected[0], selected[4]));
    }

    private static String labelFor(String[] plugin) {
        String label = plugin[0];
        if (!plugin[2].isEmpty()) {
            label += "  (v" + plugin[2] + ")";
        }
        if (!plugin[3].isEmpty()) {
            label += "  -  " + plugin[3];
        }
        return label;
    }

    record Selection(String pluginName, String fileName) {}
}
