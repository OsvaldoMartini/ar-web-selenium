package com.allinweb.ch.component.listCell;

import com.allinweb.ch.driver.ARWebElement;
import javafx.scene.control.ListCell;

public class ARWebElementListCell<T extends ARWebElement> extends ListCell<T> {

    @Override
    protected void updateItem(T arWebElement, boolean empty) {
        super.updateItem(arWebElement, empty);
        String css = getClass().getResource("/listView.css").toExternalForm();
        getStylesheets().add(css);
        if (empty || arWebElement == null || arWebElement.getGraphicRepresentation() == null) {
            setGraphic(null);
        } else {
            setGraphic(arWebElement.getGraphicRepresentation());
        }
    }
}
