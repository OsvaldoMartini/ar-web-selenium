package com.allinweb.ch.component.pane.base;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ARPane extends Application implements IARPane {

    private Pane pane;
    private Scene scene;

    public Scene getScene() {
        return scene;
    }

    public void createScene(double width, double height) {
        pane = getPaneReference();
        if (pane != null) {
            this.scene = new Scene(pane, width, height);
        }
    }

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
        panel.getChildren().clear();
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

    @Override
    public void start(Stage stage) throws Exception {
        log.error("start from ARPane");
    }
}
