package com.allinweb.ch.component.listCell;

import com.allinweb.ch.driver.ABRWebElement;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ListCell;

public class InstructionListCell extends ListCell<BlockLoopInstructionDTO> {
    @Override
    protected void updateItem(BlockLoopInstructionDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getBlock() != null) {
            graphic = (new ABRWebElement(item)).getGraphicRepresentation();
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
