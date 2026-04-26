package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.AROcrTestResultsPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.model.OcrConfigParam;
import com.allinweb.ch.vision.OcrTestResultRow;
import java.nio.file.Path;
import java.util.List;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

/** Modal that hosts {@link AROcrTestResultsPane}. Opened from the OCR Config "Test On Current Page" button. */
@Slf4j
public class AROcrTestResultsScene extends ARScene {

    private static final Double SCENE_HEIGHT = 720D;
    private static final Double SCENE_WIDTH = 1280D;
    private static final String TITLE = "OCR Test Results";

    protected static volatile AROcrTestResultsScene instance;

    private AROcrTestResultsScene() {
        super();
    }

    public static AROcrTestResultsScene getInstance() {
        if (instance == null) {
            synchronized (AROcrTestResultsScene.class) {
                if (instance == null) instance = new AROcrTestResultsScene();
            }
        }
        return instance;
    }

    @Override
    public IARPane buildPane() {
        return AROcrTestResultsPane.getInstance();
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
        // Sit on top of the OCR Config modal but allow it to remain interactive
        // so the user can keep tweaking after reviewing.
        stage.initModality(Modality.NONE);
    }

    /** Convenience: load data into the pane and show the modal. */
    public void openWith(
            String headerSummary,
            List<OcrTestResultRow> rows,
            Path annotatedImagePath,
            OcrConfigParam approvedXPathsParam) {
        AROcrTestResultsPane.getInstance().populate(headerSummary, rows, annotatedImagePath, approvedXPathsParam);
        show();
    }
}
