package com.allinweb.ch.component.listCell;

import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;

public class BlockLoopInstructionListCell extends ListCell<BlockLoopInstructionDTO> {
    @Override
    protected void updateItem(BlockLoopInstructionDTO item, boolean empty) {
        super.updateItem(item, empty);
        boolean isValid = !empty && item != null && item.getActions() != null && item.getBlock() != null;
        Label graphic = new Label();
        if (isValid) {
            ABRLogger.getInstance(BlockLoopInstructionListCell.class).info(item.getActions());
            String actionFieldName = item.getActions().split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER)[1];
            graphic.setText(actionFieldName);
        }
        Platform.runLater(() -> setGraphic(isValid ? graphic : null));
    }
}
