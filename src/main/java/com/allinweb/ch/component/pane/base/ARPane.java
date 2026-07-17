package com.allinweb.ch.component.pane.base;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

public abstract class ARPane implements IARPane {

    protected Pane pane;

    @Override
    public final Pane createPane() {
        initUI();
        return pane;
    }

    public abstract Pane getPaneReference();

    private void initUI() {
        initUIComponents();
        initUIBehaviour();
        pane = getPaneReference();
    }

    @Override
    public final void addNodesToPane(Pane panel, Node... toAdd) {
        for (Node node : toAdd) {
            if (!panel.getChildren().contains(node)) {
                panel.getChildren().add(node);
            }
        }
    }

    @Override
    public void clearPane(Pane panel) {
        try {
            if (panel.getChildren() != null) {
                panel.getChildren().clear();
            }
        } catch (Exception ignore) {

        }
    }

    @Override
    public void clear() {
        if (pane != null) {
            clearPane(pane);
            pane = null;
        }
    }

    @Override
    public final void removeNodesFromPane(Pane panel, Node... toRemove) {
        panel.getChildren().removeAll(toRemove);
    }

    @Override
    public void removeNodesFromPane(Pane bottomPane) {
        if (!bottomPane.getChildren().isEmpty()) {
            bottomPane.getChildren().remove(bottomPane.getChildren().size() - 1);
        }
    }

}
