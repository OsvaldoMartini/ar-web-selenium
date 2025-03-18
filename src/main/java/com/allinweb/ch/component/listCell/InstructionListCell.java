package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.driver.ARWebElement;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ListCell;

public class InstructionListCell extends ListCell<InstructionLoadDTO> {
    @Override
    protected void updateItem(InstructionLoadDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getBlockId() != null) {
            graphic = (new ARWebElement(item, null)).getGraphicRepresentation();
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
