package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.ARExcelFilePane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARExcelFileScene extends ARScene {

  protected static volatile ARExcelFileScene instance;

  // Private constructor to prevent instantiation
  private ARExcelFileScene() {
    // Initialize if necessary
    super();
  }

  public static ARExcelFileScene getInstance() {
    if (instance == null) {
      synchronized (ARExcelFileScene.class) {
        if (instance == null) {
          instance = new ARExcelFileScene();
        }
      }
    }
    return instance;
  }

  public void initialize(String sessionId, BlockDetailsDTO blockExcelDTO) {
    this.sessionId = sessionId;
    this.blockExcelDTO = blockExcelDTO;
  }

  private Stage modalStage;
  private Scene modalScene;

  private static ARExcelFilePane arExcelFilePane;

  static {
    arExcelFilePane = ARExcelFilePane.getInstance();
  }

  private static final Double SCENE_HEIGHT = 300D;
  private static final Double SCENE_WIDTH = 800D;
  private static final String TITLE = "Create or Delete the Export Excel File";
  private BlockDetailsDTO blockExcelDTO;
  private String sessionId;

  @Override
  public IARPane buildPane() {
    //        arExcelFilePane.initialize(sessionId, blockExcelDTO, modalStage);
    return arExcelFilePane;
  }

  @Override
  public Double getSceneHeight() {
    return SCENE_HEIGHT;
  }

  @Override
  public Double getSceneWidth() {
    return SCENE_WIDTH;
  }

  @Override
  public String getTitle() {
    return TITLE;
  }

  public void showModal() {

    arExcelFilePane.initialize(sessionId, blockExcelDTO, modalStage);

    if (modalStage == null) {
      modalStage = new Stage();
      IARPane pane = buildPane();
      if (pane != null) {
        modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
        modalStage.setScene(modalScene);
        modalStage.setTitle(getTitle());
        modalStage.initModality(Modality.WINDOW_MODAL); // Changed to NONE
        modalStage.setAlwaysOnTop(true); // Set always on top
        modalStage.toFront();
        // Reset alwaysOnTop after showing so it behaves normally afterward
        modalStage.setAlwaysOnTop(false);

        // Once shown, reset AlwaysOnTop to false so it behaves normally
        modalStage.setOnShown(
            event -> {
              Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
            });
      } else {
        // Handle the case where pane creation failed
        ARLogger.getInstance(ARExcelFileScene.class).severe("Failed to build pane for modal.");
        return;
      }
    }
    modalStage.setTitle(getTitle()); // Update title if it might have changed

    // Check if the stage is already showing
    if (!modalStage.isShowing()) {
      modalStage.showAndWait(); // Show and wait only if not already showing
    }
  }
}
