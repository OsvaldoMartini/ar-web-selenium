package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARNewHomeBankingPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.util.ARLogger;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARNewHomeBankingScene extends ARScene {

    protected static volatile ARNewHomeBankingScene instance;

    // Private constructor to prevent instantiation
    private ARNewHomeBankingScene() {

        super();
    }

    public static ARNewHomeBankingScene getInstance() {
        if (instance == null) {
            synchronized (ARNewHomeBankingScene.class) {
                if (instance == null) {
                    instance = new ARNewHomeBankingScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final PerformLists performLists = PerformLists.getInstance();
    private static final ARNewHomeBankingPane arNewHomeBankingPane = ARNewHomeBankingPane.getInstance();
    private static HomeBankingLoadDTO homeBank;

    private static final Double SCENE_HEIGHT = 750D;
    private static final Double SCENE_WIDTH = 1200D;
    private static final String TITLE = "New Organization";

    public void initialize(HomeBankingLoadDTO homeBank) {
        this.homeBank = homeBank;

        if (!isNullOrEmpty(performLists.getListHomeBanking())) {
            arNewHomeBankingPane.updateTableBankingView();
        }
    }

    private boolean isNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    public void showModal(Stage parentStage) {

        arNewHomeBankingPane.initialize(homeBank);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initOwner(parentStage); // ✅ Set the owner first
                modalStage.initModality(Modality.NONE);
                modalStage.setAlwaysOnTop(true); // Set always on top
                modalStage.toFront();
                // Reset alwaysOnTop after showing so it behaves normally afterward
                modalStage.setAlwaysOnTop(false);

                // Once shown, reset AlwaysOnTop to false so it behaves normally
                modalStage.setOnShown(event -> {
                    Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
                });
            } else {
                // Handle the case where pane creation failed
                ARLogger.getInstance(ARNewCommandScene.class).severe("Failed to build pane for modal.");
                return;
            }
        }

        modalStage.setTitle(getTitle()); // Update title if it might have changed

        if (!modalStage.isShowing()) {
            modalStage.showAndWait();
        } else {
            modalStage.requestFocus(); // 👈 only this, no forceful toFront
        }
    }

    @Override
    public IARPane buildPane() {
        //        arNewHomeBankingPane.initialize(homeBankingList);
        return arNewHomeBankingPane;
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

    public void closeModal() {
        try {
            if (modalStage != null) {
                modalStage.close();
            }
            modalStage = null;
        } catch (Exception error) {
            System.err.println("Browser Closed Before Web Scanner. Error: " + error.getMessage());
        }
    }
}
