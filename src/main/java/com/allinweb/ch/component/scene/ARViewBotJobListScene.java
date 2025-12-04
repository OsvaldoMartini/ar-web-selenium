package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARViewBotJobListPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARViewBotJobListScene extends ARScene {

    private static final int SCENE_HEIGHT = 600;
    private static final int SCENE_WIDTH = 800;
    private static final String TITLE = "Bot Job List";
    private static final ARViewBotJobListPane arViewBotJobListPane;
    protected static volatile ARViewBotJobListScene instance;

    static {
        arViewBotJobListPane = ARViewBotJobListPane.getInstance();
    }

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private List<WebDriver> webDriverList;
    private JDialog modalDialog;

    private ARViewBotJobListScene() {
        super();
    }

    public static ARViewBotJobListScene getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobListScene.class) {
                if (instance == null) {
                    instance = new ARViewBotJobListScene();
                }
            }
        }
        return instance;
    }

    public void initialize(
            ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, List<WebDriver> webDriverList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.webDriverList = webDriverList;
    }

    @Override
    public IARPane buildPane() {
        return arViewBotJobListPane;
    }

    @Override
    public int getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public int getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    public void showModal() {
        SwingUtilities.invokeLater(() -> {
            try {
                arViewBotJobListPane.initialize(arViewBotJobScene, arWebDriver, webDriverList);

                if (modalDialog == null) {
                    modalDialog = new JDialog((Frame) null, getTitle(), true); // modal dialog
                    IARPane pane = buildPane();
                    if (pane != null) {
                        modalDialog.getContentPane().add(pane.createPane());
                        modalDialog.setSize(getSceneWidth(), getSceneHeight());
                        modalDialog.setLocationRelativeTo(null); // center on screen
                        modalDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                        // Optional: set icon if you have one
                        if (icon != null) {
                            modalDialog.setIconImage(icon);
                        }
                    } else {
                        log.error("Failed to build pane for modal.");
                        return;
                    }
                }

                modalDialog.setTitle(getTitle());

                if (!modalDialog.isVisible()) {
                    modalDialog.setVisible(true);
                }
            } catch (Exception e) {
                log.error("Error showing modal: {}", e.getMessage(), e);
            }
        });
    }
}
