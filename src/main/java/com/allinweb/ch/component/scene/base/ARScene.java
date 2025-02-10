package com.allinweb.ch.component.scene.base;

import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public abstract class ARScene implements IARScene {

    private Image icon;
    protected Stage stage; // Make stage protected to allow access in subclass
    private Scene scene;
    protected final List<Thread> threadList = new ArrayList<>();
    // Flag to track if the close request handler has been set
    protected boolean isCloseHandlerSet = false;

    public ARScene() {
        setupStage(); // Initialize the stage and set its behavior
        loadIcon();
    }

    public abstract IARPane buildPane();

    public abstract Double getSceneHeight();

    public abstract Double getSceneWidth();

    public void setStageBehaviour(Stage stage) {
        // By default, do nothing. The subclass will define behavior.
    }

    private void setupStage() {
        try {
            stage = new Stage(); // Create the stage here
            setStageBehaviour(stage); // Ensure stage behavior is set at creation time
        } catch (IllegalStateException e) {
            handleIllegalState(e);
        }
    }

    private void handleIllegalState(IllegalStateException e) {
        ARLogger.getInstance(ARScene.class).severe("ARScene IllegalStateException\n" + e);
        Platform.runLater(() -> {
            try {
                stage = new Stage(); // Retry stage creation if failed
                setStageBehaviour(stage); // Ensure stage behavior is set
            } catch (IllegalStateException ex) {
                ARLogger.getInstance(ARScene.class).severe("ARScene IllegalStateException\n" + ex);
            }
        });
    }

    private void loadIcon() {
        try {
            icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(ARConstants.ICON_APPLICATION)));
        } catch (Exception e) {
            ARLogger.getInstance(ARScene.class).severe("Error loading icon in ARScene\n" + e);
        }
    }

    public void createScene() {
        IARPane mainPane = buildPane();
        if (mainPane != null) {
            scene = new Scene(mainPane.createPane(), getSceneWidth(), getSceneHeight());
        }
    }

    @Override
    public void show() {
        createScene(); // Create the scene before showing
        Platform.runLater(() -> {
            if (stage != null) {
                stage.setTitle(getTitle());
                stage.getIcons().add(icon);
                stage.setScene(scene);
                stage.show(); // Show the stage

                if (stage.getTitle().equalsIgnoreCase("AR Web Scanner")) {
                    handleCloseApp(stage);
                } else {
                    handleCloseThreads(); // Close threads on other scenes
                }
            }
        });
    }

    private void handleCloseApp(Stage stage) {
        stage.setOnCloseRequest(event -> {
            handleCloseThreads();
            // Notify the command prompt
            handleWindowClose(event);
        });
    }

    protected void handleCloseThreads() {
        if (!isCloseHandlerSet) {
            System.out.println("Setting close handler for the first time");
            this.stage.setOnCloseRequest(event -> {
                threadList.forEach(this::interruptThread);
            });
            isCloseHandlerSet = true; // Mark the flag as true after setting
        } else {
            System.out.println("Close handler already set, skipping...");
        }
    }

    protected void interruptThread(Thread thread) {
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void startNewThread(Runnable task) {
        Thread thread = new Thread(task);
        threadList.add(thread);
        thread.start();
    }

    private void handleWindowClose(WindowEvent event) {
        ARLogger.getInstance(ARScene.class).info("X button clicked. Window is closing.");
        // Platform.exit();
        System.exit(0);
    }
}
