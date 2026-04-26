package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.AROcrConfigPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

/** Modal window that hosts {@link AROcrConfigPane}. Opened from the Page Scanner button row. */
@Slf4j
public class AROcrConfigScene extends ARScene {

    private static final Double SCENE_HEIGHT = 820D;
    private static final Double SCENE_WIDTH = 1000D;
    private static final String TITLE = "OCR Configuration";

    protected static volatile AROcrConfigScene instance;

    private AROcrConfigScene() {
        super();
    }

    public static AROcrConfigScene getInstance() {
        if (instance == null) {
            synchronized (AROcrConfigScene.class) {
                if (instance == null) instance = new AROcrConfigScene();
            }
        }
        return instance;
    }

    @Override
    public IARPane buildPane() {
        return AROcrConfigPane.getInstance();
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

    @Override
    public void setStageBehaviour(Stage stage) {
        stage.initModality(Modality.APPLICATION_MODAL);
    }

    /** Convenience: bind the current scope + open. */
    public void openFor(Integer homebankingId, Integer homeUrlId) {
        AROcrConfigPane.getInstance().bindScope(homebankingId, homeUrlId);
        show();
    }
}
