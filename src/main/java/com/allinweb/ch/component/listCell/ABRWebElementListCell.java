package com.allinweb.ch.component.listCell;

import com.allinweb.ch.driver.ABRWebElement;
import javafx.scene.control.ListCell;

public class ABRWebElementListCell<T extends ABRWebElement> extends ListCell<T> {

    @Override
    protected void updateItem(T abrWebElement, boolean empty) {
        super.updateItem(abrWebElement, empty);
        String css = getClass().getResource("/listView.css").toExternalForm();
        getStylesheets().add(css);
        if (empty || abrWebElement == null || abrWebElement.getGraphicRepresentation() == null) {
            setGraphic(null);
        } else {
            setGraphic(abrWebElement.getGraphicRepresentation());
        }
    }
}
