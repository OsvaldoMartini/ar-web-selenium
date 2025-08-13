package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ARMainPane;
import com.allinweb.ch.component.pane.ARViewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.openqa.selenium.WebDriver;

@ClientEndpoint
public class ARViewBotJobScene extends ARScene {

    protected static volatile ARViewBotJobScene instance;

    // Private constructor to prevent instantiation
    private ARViewBotJobScene() {
        // Initialize if necessary
        super();
    }

    public static ARViewBotJobScene getInstance() {
        if (instance == null) {
            synchronized (ARViewBotJobScene.class) {
                if (instance == null) {
                    instance = new ARViewBotJobScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final ARPropertyManager arPropertyManager;
    private static final PerformLists performLists;
    private static final PerformDataBase performDataBase;
    private static ARNewCommandScene arNewCommandScene;
    private static final ARViewBotJobPane arViewBotJobPane;
    private static final PerformMessage performMessage;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performLists = PerformLists.getInstance();
        performDataBase = PerformDataBase.getInstance();
        arNewCommandScene = ARNewCommandScene.getInstance();
        arViewBotJobPane = ARViewBotJobPane.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    private int portSocketInitial = 54525;
    private boolean isConnectWebSocket = false;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final CountDownLatch latch = new CountDownLatch(1);
    private Session session;

    private ExecutorService executorWebSocket = Executors.newSingleThreadExecutor();

    private ARWebDriver arWebDriver;

    private BotJobLoadDTO selectedBojJob;

    public void initialize(ARWebDriver arWebDriver, BotJobLoadDTO selectedBojJob) {
        this.arWebDriver = arWebDriver;
        this.selectedBojJob = selectedBojJob;
        reloadList();

        String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
        if (!Strings.isNullOrEmpty(port)) {
            portSocketInitial = Integer.parseInt(port);
        }

        if (!arNewCommandScene.isConnectWebSocket) {
            arNewCommandScene.connectWebSocketClient(portSocketInitial, "new-command-scene"); // + botJobLoad.getId());
        }

        if (!isConnectWebSocket) {
            connectWebSocketClient(portSocketInitial, "bot-job-scene"); // + botJobLoad.getId());
        }
    }

    private void reloadList() {
        //        PerformDataBase..cacheEntitiesFromDB();

        //        BotJobDTO botJobDTO = PerformDataBase..getEntityById(BotJobDTO.class, this.botJobId);

        //        boolean updBotJobStatus = performDataBase.updateBotStatus();
        //        if (!updBotJobStatus) {
        //            ARLogger.getInstance(ARViewBotJobScene.class)
        //                    .info(String.format("Failed to Update ALL Bot Job Active = 1"));
        //        }

        performDataBase.loadBlocks(selectedBojJob.getId(), selectedBojJob.getName(), "block");
        //        this.botLoadJobs = performDataBase.loadBotJobWithBlock(this.botJobId);

        BotJobLoadDTO botJobLoad = performLists.getQuickBotJobs().stream()
                .filter(job -> job.getId().equals(selectedBojJob.getId()))
                .findFirst()
                .orElse(null); // orElseThrow(...) if you want an exception when not found

        if (performLists.getListHomeUrl().isEmpty()) {
            performDataBase.loadHomeUrls(null);
        }
        //        performDataBase.loadHomeBanking(selectedBojJob.getHomeBankingId());

        if (botJobLoad != null && botJobLoad.getBlockLoadDTOList() == null) {
            botJobLoad.setBlockLoadDTOList(performLists.getListBlock());
        }
        // It Prevents Start without blocks
        if (performLists.getListBlock().isEmpty()) {

            // It Prevents Start without blocks
            BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
            newBlockDetails.setBlockName(selectedBojJob.getName() + " default block");
            newBlockDetails.setBlockDescription(selectedBojJob.getName() + " block description");
            newBlockDetails.setTypeId(1);
            newBlockDetails.setActive(true);
            newBlockDetails.setWait(3);
            newBlockDetails.setBlockOrderNumber(1);

            newBlockDetails.setBotJobId(selectedBojJob.getId());

            ErrorMessage errorMessage = performDataBase.initiateNewBlock(newBlockDetails, selectedBojJob.getId());
            if (errorMessage == null) {
                ARLogger.getInstance(Thread.class)
                        .info(String.format("A new Block was created for bot job Id %d", selectedBojJob.getId()));
            } else {
                performMessage.errorMessage(
                        errorMessage.getErrorTitle(),
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                + errorMessage.getErrorTitle(),
                        "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                        null,
                        0);
            }
        }
    }

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "Bot Job Details";

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage); // Call the parent class method

        // Only set the close request handler if it's not already set
        if (!isCloseHandlerSet) {
            stage.setOnCloseRequest(this::handleCloseRequest);
            isCloseHandlerSet = true; // Update the flag to prevent setting it again
        }
    }

    private void handleCloseRequest(WindowEvent event) {
        System.out.println("Handle Close: Exiting Threads and Quitting WebDriver");

        // Interrupt running threads
        threadList.forEach(this::interruptThread);

        // Close WebDriver if it's initialized
        //        closeWebDrivers();
    }

    // Method to close all WebDriver instances
    private void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                driver.quit();
                ARLogger.getInstance(ARMainPane.class).info("WebDriver closed.");
            } catch (Exception e) {
                ARLogger.getInstance(ARMainPane.class).warning("Error closing WebDriver: " + e.getMessage());
            }
        }
        Platform.runLater(() -> arWebDriver.getWebDriverList().clear());
    }

    @Override
    public IARPane buildPane() {
        //        arViewBotJobPane.initialize(this, this.botLoadJob, botJobList);
        return arViewBotJobPane;
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
        if (selectedBojJob.getId() != null) {
            return TITLE + " WebSite Id: " + selectedBojJob.getHomeBankingId() + " Id: " + selectedBojJob.getId();
        }

        return TITLE;
    }

    public void showModal() {

        arViewBotJobPane.initialize(this, selectedBojJob);

        if (modalStage == null) {
            modalStage = new Stage();
            modalStage.getIcons().add(icon);
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL);
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
    }

    public void closeModal() {
        try {
            if (modalStage != null) { // && modalStage.isShowing()) {
                modalStage.close();
            }
            modalStage = null;
        } catch (Exception error) {

        }
    }

    public void destroyPanel() {
        arViewBotJobPane.destroy();
    }

    private void stopKeepAlivePings() {
        scheduler.shutdownNow();
    }

    private void startKeepAlivePings() {
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        if (session != null && session.isOpen()) {
                            session.getBasicRemote().sendText("ping-bot-job-scene"); // Or a specific keep-alive message
                        }
                    } catch (IOException e) {
                        System.err.println("Error sending ping: " + e.getMessage());
                        // Handle potential disconnection
                    }
                },
                0,
                15,
                TimeUnit.SECONDS); // Adjust interval as needed
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received: " + message);
        if (message == null || message.trim().isEmpty() || message.contains("CONNECT") || message.contains("ping")) {
            // Ignore null or empty messages
            message = message.replaceAll("ping-", "");
            System.out.println("Active : " + message);
            return;
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        latch.countDown(); // Release the latch after connection is established
        System.out.println("Connected to WebSocket server at: " + session.getRequestURI());
        // Sending an initial message
        sendMessage("Hello from JavaFX WebSocket client!");
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("Connection closed.");
        stopKeepAlivePings();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error: " + throwable.getMessage());
        stopKeepAlivePings();
    }

    // Method to send a message
    public void sendMessage(String message) {
        executorWebSocket.submit(() -> {
            //            if (session != null && session.isOpen()) {
            //                try {
            //                    session.getBasicRemote().sendText(message);
            //                } catch (Exception e) {
            //                    e.printStackTrace();
            //                }
            //            }
        });
    }

    public void connectWebSocketClient(int portSocket, String sessionId) {
        executorWebSocket.submit(() -> {
            String serverUri = "ws://localhost:" + portSocket + "/websocket?sessionId=" + sessionId;
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                container.connectToServer(this, new URI(serverUri));
                latch.await();
                startKeepAlivePings();
                isConnectWebSocket = true;
            } catch (Exception e) {
                isConnectWebSocket = false;
                System.err.println("WebSocket connection failed sessionId: " + sessionId + " error: " + e.getMessage());
            }
        });
    }
}
