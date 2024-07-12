package com.allinweb.ch.component.scene.base;

import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public abstract class ABRScene implements IABRScene {

    private Image icon = null;

    private Stage stage = null;
    private Scene scene = null;

    public abstract IABRPane buildPane();

    public abstract Double getSceneHeight();

    public abstract Double getSceneWidth();

    public void setStageBehaviour(Stage stage) {
        // no stage behaviour changed by default
    }

    public ABRScene() {
        try {
            stage = new Stage();
            setStageBehaviour(stage);
        } catch (IllegalStateException e) {
            ABRLogger.getInstance(ABRWebDriver.class).severe("ABRScene (1) IllegalStateException\n" + e);
            try {
                Platform.runLater(() -> {
                    stage = new Stage();
                    setStageBehaviour(stage);
                });
            } catch (IllegalStateException ex) {
                ABRLogger.getInstance(ABRWebDriver.class).severe("ABRScene (2) IllegalStateException\n" + ex);
            }
        }
        try {
            icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(ABRConstants.ICON_APPLICATION)));
        } catch (Exception e) {
            ABRLogger.getInstance(ABRWebDriver.class).severe("ABRScene\n" + e);
        }
    }

    public void createScene() {
        IABRPane mainPane = buildPane();
        // new Alert(AlertType.WARNING, "Error" + (mainPane == null)).show();
        if (mainPane != null) {
            scene = new Scene(mainPane.createPane(), getSceneWidth(), getSceneHeight());
        }
    }

    @Override
    public void show() {
        createScene();
        Platform.runLater(() -> {
            if (stage != null) {
                stage.setTitle(getTitle());
                stage.getIcons().add(icon);
                stage.setScene(scene);
                // stage.setResizable(false);
                stage.show();
            } else {
                show();
            }
        });
    }
}
