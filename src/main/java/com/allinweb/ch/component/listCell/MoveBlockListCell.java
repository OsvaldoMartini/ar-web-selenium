package com.allinweb.ch.component.listCell;

import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.util.ABRConstants;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Border;
import javafx.scene.paint.Color;

public class MoveBlockListCell extends ListCell<BlockDTO> {

    @Override
    protected void updateItem(BlockDTO item, boolean empty) {
        super.updateItem(item, empty);

        Node graphic = null;

        if (!empty && item != null && item.getBotJobDTO() != null) {
            Label blockNameLabel = new Label(item.getName());
            blockNameLabel.setAlignment(Pos.CENTER_LEFT);
            blockNameLabel.setTextFill(Color.BLACK);

            int currentPosition = getListView().getItems().indexOf(item) + 2;
            if (item.getName() != null) {
                blockNameLabel.setText("#" + Integer.toString(currentPosition) + " " + item.getName());
            } else {
                blockNameLabel.setText("#" + Integer.toString(currentPosition) + " ");
            }

            AnchorPane pane = new AnchorPane(blockNameLabel);

            AnchorPane.setLeftAnchor(blockNameLabel, ABRConstants.SPACE_XS);
            AnchorPane.setTopAnchor(blockNameLabel, ABRConstants.SPACE_XS);
            AnchorPane.setBottomAnchor(blockNameLabel, ABRConstants.SPACE_XS);

            AnchorPane.setLeftAnchor(pane, ABRConstants.SPACE_M);
            AnchorPane.setTopAnchor(pane, ABRConstants.SPACE_M);
            AnchorPane.setBottomAnchor(pane, ABRConstants.SPACE_M);

            setBorder(Border.stroke(Color.LIGHTGRAY));
            setPrefHeight(ABRConstants.SPACE_L);
            // VBox.setMargin(this, new Insets(5));

            graphic = pane;
        }

        Node finalGraphic = graphic;
        Platform.runLater(() -> {
            setGraphic(finalGraphic);
        });
    }
}
