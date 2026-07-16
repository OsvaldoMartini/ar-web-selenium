package com.allinweb.ch.component.pane;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;

final class ScannerElementFocusComboBoxAdapter {

    ComboBox<ARScannedElementPane.ElementScanProfile> build(
            List<ARScannedElementPane.ElementScanProfile> profiles,
            ARScannedElementPane.ElementScanProfile defaultProfile) {
        ComboBox<ARScannedElementPane.ElementScanProfile> comboBox =
                new ComboBox<>(FXCollections.observableArrayList(profiles));
        comboBox.setPrefWidth(260);
        comboBox.setTooltip(new Tooltip("Choose which type of web element the Page Scanner should focus."));
        comboBox.getSelectionModel().select(defaultProfile);
        comboBox.setButtonCell(new ElementScanProfileCell());
        comboBox.setCellFactory(list -> new ElementScanProfileCell());
        return comboBox;
    }

    private static final class ElementScanProfileCell extends ListCell<ARScannedElementPane.ElementScanProfile> {
        @Override
        protected void updateItem(ARScannedElementPane.ElementScanProfile item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                setStyle("");
                return;
            }

            setText(item.label());
            setTooltip(new Tooltip(item.description()));
            if (item.label().startsWith("All -")) {
                setStyle("-fx-font-weight: bold;");
            } else {
                setStyle("");
            }
        }
    }
}
