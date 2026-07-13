package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARViewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.BotJobLoadDTO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.extern.slf4j.Slf4j;

/** Temporary JavaFX window host for Bot Job Details while its React workspace is redirected. */
@Slf4j
public final class ARViewBotJobScene extends ARScene {

    private static final ARViewBotJobPane PANE = ARViewBotJobPane.getInstance();
    private static final double SCENE_HEIGHT = 600D;
    private static final double SCENE_WIDTH = 1100D;
    private static final String TITLE = "Bot Job Details";
    private static volatile ARViewBotJobScene instance;

    private Stage modalStage;
    private Scene modalScene;
    private boolean licenseGuardEnabled;
    private BotJobLoadDTO selectedBotJob;

    private ARViewBotJobScene() { super(); }

    public static ARViewBotJobScene getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobScene.class) {
                if (instance == null) instance = new ARViewBotJobScene();
            }
        }
        return instance;
    }

    public void initialize(ARWebDriver ignoredDriver, BotJobLoadDTO selectedBotJob, boolean licenseGuardEnabled) {
        this.selectedBotJob = selectedBotJob;
        this.licenseGuardEnabled = licenseGuardEnabled;
    }

    @Override public IARPane buildPane() { return PANE; }
    @Override public Double getSceneHeight() { return SCENE_HEIGHT; }
    @Override public Double getSceneWidth() { return SCENE_WIDTH; }

    @Override
    public String getTitle() {
        return selectedBotJob != null && selectedBotJob.getId() != null
                ? TITLE + " WebSite Id: " + selectedBotJob.getHomeBankingId() + " Id: " + selectedBotJob.getId()
                : TITLE;
    }

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage);
        if (!isCloseHandlerSet) {
            stage.setOnCloseRequest(this::handleCloseRequest);
            isCloseHandlerSet = true;
        }
    }

    private void handleCloseRequest(WindowEvent event) {
        if (!PANE.canCloseWorkspace()) {
            event.consume();
            return;
        }
        PANE.markWorkspaceClosed();
        threadList.forEach(this::interruptThread);
    }

    public void showModal() {
        PANE.initialize(this, selectedBotJob, licenseGuardEnabled);
        if (modalStage == null) {
            modalStage = new Stage();
            PANE.setStage(modalStage);
            modalStage.setOnCloseRequest(this::handleCloseRequest);
            modalStage.setOnHidden(event -> PANE.markWorkspaceClosed());
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane == null) {
                log.error("Failed to build pane for Bot Job Details modal");
                return;
            }
            modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(modalScene);
            modalStage.initModality(Modality.WINDOW_MODAL);
            modalStage.setOnShown(event -> Platform.runLater(() -> modalStage.setAlwaysOnTop(false)));
        }
        PANE.setStage(modalStage);
        modalStage.setTitle(getTitle());
        if (!modalStage.isShowing()) modalStage.showAndWait();
    }

    public void closeModal() {
        if (modalStage == null || !PANE.canCloseWorkspace()) return;
        PANE.markWorkspaceClosed();
        modalStage.close();
        modalStage = null;
    }

    public void destroyPanel() {
        if (!PANE.canCloseWorkspace()) return;
        PANE.markWorkspaceClosed();
        PANE.destroy();
    }
}
