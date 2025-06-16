package com.allinweb.ch.component.pane.base;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public abstract class ARPane extends Application implements IARPane {

  protected Pane pane;
  protected Scene scene;

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
    scene = null;
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
    ARLogger.getInstance(ARWebDriver.class).severe("start from ARPane");
  }
}
