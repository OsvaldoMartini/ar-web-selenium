package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Getter;
import org.openqa.selenium.WebDriver;

public class ARScannedElementScene extends ARScene {

    protected static volatile ARScannedElementScene instance;

    // Private constructor to prevent instantiation
    private ARScannedElementScene() {
        // Initialize if necessary
        super();
    }

    public static ARScannedElementScene getInstance() {
        if (instance == null) {
            synchronized (ARScannedElementScene.class) {
                if (instance == null) {
                    instance = new ARScannedElementScene();
                }
            }
        }
        return instance;
    }

    private static final Double SCENE_HEIGHT = 650D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "AR Web Factory";

    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private HomeBankingLoadDTO homeBankingLoadDTO;

    @Getter
    private BotJobLoadDTO botJobLoadDTO;

    private BlockLoadDTO blockLoadDTO;

    private ExecutorService executorWebSocket;
    private ExecutorService executorServicePreLaunch;

    private Stage modalStage;
    private Scene modalScene;

    private static final ARScannedElementPane arScannedElementPane;
    private static final ARWebDriver arWebDriver;
    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;

    static {
        arScannedElementPane = ARScannedElementPane.getInstance();
        arWebDriver = ARWebDriver.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    public ARScannedElementScene initialize(
            HomeBankingLoadDTO homeBankingLoadDTO, BotJobLoadDTO botJobLoadDTO, BlockLoadDTO blockLoadDTO) {
        this.homeBankingLoadDTO = homeBankingLoadDTO;
        this.botJobLoadDTO = botJobLoadDTO;
        this.blockLoadDTO = blockLoadDTO;
        this.executorWebSocket = Executors.newSingleThreadExecutor();
        this.executorServicePreLaunch = Executors.newSingleThreadExecutor();
        return this;
    }

    @Override
    public IARPane buildPane() {
        //        arScannedElementPane.initialize(
        //                arWebDriver,
        //                homeBankingLoadDTO,
        //                botJobLoadDTO,
        //                blockLoadDTO,
        //                executorWebSocket,
        //                executorServicePreLaunch);
        return arScannedElementPane;
    }

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage); // Call the parent class method

        // Only set the close request handler if it's not already set
        if (!isCloseHandlerSet) {
            stage.setOnCloseRequest(this::handleCloseRequest);
            isCloseHandlerSet = true; // Update the flag to prevent setting it again
        }
    }

    public void handleCloseRequest(WindowEvent event) {
        System.out.println("Handle Close: Exiting Threads and Quitting WebDriver");

        // Interrupt running threads
        threadList.forEach(this::interruptThread);

        // Close WebDriver if it's initialized
        if (arWebDriver != null) {
            try {
                closeWebDrivers();

                //                arWebDriver.closeDriver(); // Quit WebDriver
                arWebDriver.getCurrentDriver().quit(); // Quit WebDriver
                arWebDriver.setCurrentDriver(null);

                shutDownExecutorService(executorWebSocket);
                shutDownExecutorService(executorServicePreLaunch);

                System.out.println("WebDriver quit successfully.");
            } catch (Exception e) {
                System.err.println("Error closing WebDriver: " + e.getMessage());
            }
        }
    }

    // Method to close all WebDriver instances
    public void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                driver.quit();
                ARLogger.getInstance(ARScannedElementPane.class).info("WebDriver closed.");
            } catch (Exception e) {
                ARLogger.getInstance(ARScannedElementPane.class).warning("Closing WebDriver: " + e.getMessage());
            }
        }
        Platform.runLater(() -> arWebDriver.getWebDriverList().clear());
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    private void shutDownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate");
                    ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            ARLogger.getInstance(ARWebDriver.class).severe("ExecutorService did not terminate\n" + e.getMessage());
        }
    }

    public void showModal() {

        arScannedElementPane.initialize(
                arWebDriver,
                homeBankingLoadDTO,
                botJobLoadDTO,
                blockLoadDTO,
                executorWebSocket,
                executorServicePreLaunch);

        try {

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
                    modalStage.setOnShown(event -> {
                        Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
                    });
                } else {
                    // Handle the case where pane creation failed
                    ARLogger.getInstance(ARViewBotJobScene.class).severe("Failed to build pane for modal.");
                    return;
                }
            }

            modalStage.setTitle(getTitle());

            // Check if the stage is already showing
            if (!modalStage.isShowing()) {
                modalStage.showAndWait(); // Show and wait only if not already showing
            }
        } catch (Exception error) {
            closeWebDrivers();
            closeModal();

            String browser = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
            String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
            int lastSlashIndex = webDriverPath.lastIndexOf('\\');
            String directoryPath = webDriverPath.substring(0, lastSlashIndex + 1); // includes the last backslash
            String fileName = webDriverPath.substring(lastSlashIndex + 1);

            performMessage.errorMessage(
                    "WebDriver Version Incompatibility",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>WebDriver version might be incompatible.</span>",
                    "<span style='font-weight: bold;'>Please verify the following:</span>",
                    "<ul>"
                            + "   <li>The installed browser version: <span style='color: #008b8b ; font-weight: bold;'>"
                            + browser
                            + "</span></li>"
                            + "   <li>The WebDriver path:<br><span style='color: #008b8b ; font-weight: bold;'>"
                            + directoryPath
                            + "</span></li>"
                            + "<li>The WebDriver file:<br><span style='color: #008b8b ; font-weight: bold;'>"
                            + fileName
                            + "</span></li>"
                            + "   <li>Ensure the WebDriver version is the correct one for your browser version.</li>"
                            + "</ul>",
                    "<span style='font-style: italic;'>Refer to your browser's documentation or the WebDriver's release notes for compatibility information.</span>",
                    0);
        }
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

    public void destroyPanel() {
        arScannedElementPane.destroy();
    }
}
