package com.allinweb.ch.component.pane.base;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

public interface IABRPane {

    Pane createPane();

    Pane getPaneReference();

    /***
     * This method initialize every UI component from a graphic point of view for the entire application.
     * After this method is executed the UI components must be fully built to be referenced or added in the various
     * part of the application.
     */
    void initUIComponents();

    /***
     * This method sets the behaviour for the various elements in the application. After this method is executed,
     * all the elements must be fully working.
     */
    void initUIBehaviour();

    void addNodesToPane(Pane panel, Node... toAdd);

    void clearPane(Pane panel);

    void removeNodesFromPane(Pane panel, Node... toRemove);
}
