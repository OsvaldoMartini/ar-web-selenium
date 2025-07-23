package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class HomeBankingListCell extends ListCell<HomeBankingLoadDTO> {
    @Override
    protected void updateItem(HomeBankingLoadDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;

        if (!empty && item != null && item.getUrl() != null) {
            Label name = new Label(item.getName());
            Label url = new Label(item.getUrl());

            name.setPrefWidth(100); // fixed width for name
            name.setMinWidth(100); // avoid shrinking too much
            name.setMaxWidth(100); // avoid expanding

            url.setWrapText(true); // allow wrapping if needed
            HBox.setHgrow(url, Priority.ALWAYS); // make url expand

            HBox hbox = new HBox(10, name, url); // 10 is the spacing
            hbox.setAlignment(Pos.CENTER_LEFT);

            graphic = hbox;
        }

        Node finalGraphic = graphic;
        Platform.runLater(() -> setGraphic(finalGraphic));
    }
}
