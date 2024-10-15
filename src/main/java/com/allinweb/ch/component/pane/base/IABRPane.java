package com.allinweb.ch.component.pane.base;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

public interface IABRPane {

    Pane createPane();

    Pane getPaneReference();

    void initUIComponents();

    void initUIBehaviour();

    default void addNodesToPane(Pane panel, Node... toAdd) {
        // Default implementation can be overridden
    }

    default void clearPane(Pane panel) {
        // Default implementation
    }

    default void removeNodesFromPane(Pane panel, Node... toRemove) {
        // Default implementation
    }

    default void removeNodesFromPane(Pane bottomPane) {
        // Default implementation
    }
}
