// package com.allinweb.ch.component.listCell;
//
// import com.allinweb.ch.component.model.InstructionLoad;
// import com.allinweb.ch.util.ARConstants;
// import com.allinweb.ch.util.ARLogger;
// import javafx.application.Platform;
// import javafx.scene.control.Label;
// import javafx.scene.control.ListCell;
//
// public class BlockLoopInstructionListCell extends ListCell<InstructionLoad> {
//    @Override
//    protected void updateItem(InstructionLoad item, boolean empty) {
//        super.updateItem(item, empty);
//        boolean isValid = !empty && item != null && item.getActions() != null && item.getBlockId() != null;
//        Label graphic = new Label();
//        if (isValid) {
//            ARLogger.getInstance(BlockLoopInstructionListCell.class).info(item.getActions());
//            String actionFieldName = item.getActions().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)[1];
//            graphic.setText(actionFieldName);
//        }
//        Platform.runLater(() -> setGraphic(isValid ? graphic : null));
//    }
// }
