package com.allinweb.ch.component.listCell;

import com.allinweb.ch.driver.ARWebElement;
import com.allinweb.ch.persistence.InstructionDTO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ListCell;

public class InstructionListCell extends ListCell<InstructionDTO> {
    @Override
    protected void updateItem(InstructionDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getBlock() != null) {
            graphic = (new ARWebElement(item)).getGraphicRepresentation();
            if (getScene() != null) {
                graphic.setUserData(getScene().getRoot().getUserData());
            }
        }
        Node finalGraphic = graphic;
        String css = getClass().getResource("/listView.css").toExternalForm();

        Platform.runLater(() -> {
            getStylesheets().add(css);
            setGraphic(finalGraphic);
        });
    }
}
