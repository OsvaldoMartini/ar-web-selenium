package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.AROrganizationManagerPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.facade.OrganizationManagerLifecycle;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AROrganizationManagerScene extends ARScene {

    private static final AROrganizationManagerPane organizationManagerPane = AROrganizationManagerPane.getInstance();
    private static final Double SCENE_HEIGHT = 760D;
    private static final Double SCENE_WIDTH = 1240D;
    private static final String TITLE = "Organizations";

    protected static volatile AROrganizationManagerScene instance;

    private Stage modalStage;
    private Scene modalScene;

    private AROrganizationManagerScene() {
        super();
        OrganizationManagerLifecycle.getInstance().install(new SceneOrganizationManagerHandler());
    }

    public static AROrganizationManagerScene getInstance() {
        if (instance == null) {
            synchronized (AROrganizationManagerScene.class) {
                if (instance == null) {
                    instance = new AROrganizationManagerScene();
                }
            }
        }
        return instance;
    }

    public void showModal() {
        showModal(null);
    }

    public void showModal(Stage parentStage) {
        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane == null) {
                log.error("Failed to build organization manager pane.");
                return;
            }
            modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
            modalStage.setScene(modalScene);
            modalStage.setTitle(getTitle());
            if (parentStage != null) {
                modalStage.initOwner(parentStage);
            }
            modalStage.initModality(Modality.NONE);
            modalStage.setAlwaysOnTop(true);
            modalStage.setOnShown(event -> Platform.runLater(() -> modalStage.setAlwaysOnTop(false)));
        }

        if (!modalStage.isShowing()) {
            modalStage.show();
            modalStage.toFront();
            modalStage.setAlwaysOnTop(false);
        } else {
            modalStage.requestFocus();
        }
    }

    public void closeModal() {
        if (modalStage != null && modalStage.isShowing()) {
            modalStage.close();
        }
    }

    @Override
    public IARPane buildPane() {
        return organizationManagerPane;
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

    private final class SceneOrganizationManagerHandler implements OrganizationManagerLifecycle.Handler {
        @Override
        public void openOrganizations() {
            AROrganizationManagerScene.this.showModal();
        }

        @Override
        public void closeModal() {
            AROrganizationManagerScene.this.closeModal();
        }
    }
}
