package com.allinweb.ch.component.listCell;

import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.TilePane;

public class HomeBankingListCell extends ListCell<HomeBankingDTO> {
    @Override
    protected void updateItem(HomeBankingDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getUrl() != null) {
            Label name = new Label(item.getName());
            Label url = new Label(item.getUrl());
            TilePane pane = new TilePane(name, url);
            pane.setPrefColumns(2);
            pane.setPrefRows(1);
            pane.prefTileWidthProperty().bind(pane.widthProperty().divide(2).subtract(ABRConstants.SPACE_SM));
            graphic = pane;
        }
        Node finalGraphic = graphic;
        Platform.runLater(() -> setGraphic(finalGraphic));
    }
}
