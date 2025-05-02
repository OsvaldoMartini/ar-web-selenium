package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARMainPane;
import com.allinweb.ch.component.pane.ARViewBotJobPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.socket.WebSocketTestClient;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.collections.ObservableList;
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

    private int portSocket = 54525;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final CountDownLatch latch = new CountDownLatch(1);
    private Session session;

    private ExecutorService executorWebSocket = Executors.newSingleThreadExecutor();

    private ObservableList<BotJobLoadDTO> botJobList;

    private ARScene currentScene;
    private ARWebDriver arWebDriver;
    private BotJobLoadDTO botJobLoad;

    private static final ARPropertyManager arPropertyManager;
    private static final PerformDataBase performDataBase;
    private static ARNewCommandScene arNewCommandScene;
    private static final ARViewBotJobPane arViewBotJobPane;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performDataBase = PerformDataBase.getInstance();
        arNewCommandScene = ARNewCommandScene.getInstance();
        arViewBotJobPane = ARViewBotJobPane.getInstance();
    }

    public void initialize(
            ARWebDriver arWebDriver, BotJobLoadDTO botJobLoad, ObservableList<BotJobLoadDTO> botJobList) {
        this.arWebDriver = arWebDriver;
        this.botJobLoad = botJobLoad;
        this.botJobList = botJobList;

        this.currentScene = currentScene;

        String port = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
        if (!Strings.isNullOrEmpty(port)) {
            portSocket = Integer.parseInt(port);
        }

        arNewCommandScene.connectWebSocketClient(portSocket, "new-command-scene-" + botJobLoad.getId());

        connectWebSocketClient(portSocket, "bot-job-scene-" + botJobLoad.getId());
    }

    private BotJobLoadDTO botLoadJob = null;
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private HomeBankingLoadDTO homeBankingLoadDTO;

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

        //        PerformDataBase..cacheEntitiesFromDB();

        //        BotJobDTO botJobDTO = PerformDataBase..getEntityById(BotJobDTO.class, this.botJobId);

        //        boolean updBotJobStatus = performDataBase.updateBotStatus();
        //        if (!updBotJobStatus) {
        //            ARLogger.getInstance(ARViewBotJobScene.class)
        //                    .info(String.format("Failed to Update ALL Bot Job Active = 1"));
        //        }

        this.blockLoadList = performDataBase.loadBlocksByBotJobId(this.botJobLoad.getId());
        //        this.botLoadJobs = performDataBase.loadBotJobWithBlock(this.botJobId);
        this.botLoadJob = performDataBase.loadBotJobById(this.botJobLoad.getId());
        this.homeBankingLoadDTO = performDataBase.loadHomeBanking(this.botJobLoad.getHomeBankingId());
        if (homeBankingLoadDTO != null) {
            this.botLoadJob.setHomeBankingLoadDTO(homeBankingLoadDTO);
        }

        if (this.botLoadJob.getBlockLoadDTOList() == null) {
            this.botLoadJob.setBlockLoadDTOList(this.blockLoadList);
        }
        // It Prevents Start without blocks
        if (this.botLoadJob != null && blockLoadList.isEmpty()) {

            // It Prevents Start without blocks
            BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
            newBlockDetails.setBlockName(this.botLoadJob.getName() + " default block");
            newBlockDetails.setBlockDescription(this.botLoadJob.getName() + " block description");
            newBlockDetails.setTypeId(1);
            newBlockDetails.setActive(true);
            newBlockDetails.setWait(3);
            newBlockDetails.setBlockOrderNumber(1);

            newBlockDetails.setBotJobId(this.botLoadJob.getId());

            int newBlockId = performDataBase.createNewBlock(newBlockDetails);
            ARLogger.getInstance(Thread.class)
                    .info(String.format(
                            "Created a new Block id %d for bot job Id %d", newBlockId, this.botLoadJob.getId()));
        }

        arViewBotJobPane.initialize(this, this.botLoadJob, botJobList);
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
        return TITLE;
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
            String uri = "wss://localhost:" + portSocket + "/websocket?sessionId=" + sessionId;
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            try {
                // Load keystore from resources and copy to a temp file
                String keystorePassword = "Martini!383940";
                File keystoreTempFile = copyResourceToTempFile("keystore.jks", "keystore", ".jks");
                System.setProperty("javax.net.ssl.keyStore", keystoreTempFile.getAbsolutePath());
                System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);

                // Load truststore from resources and copy to a temp file
                String truststorePassword = "Martini!383940";
                File truststoreTempFile = copyResourceToTempFile("truststore.jks", "truststore", ".jks");
                System.setProperty("javax.net.ssl.trustStore", truststoreTempFile.getAbsolutePath());
                System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
            } catch (Exception erroTemp) {

            }

            try {
                container.connectToServer(this, new URI(uri));
                latch.await();
                startKeepAlivePings();
            } catch (Exception e) {
                System.err.println("WebSocket connection failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static File copyResourceToTempFile(String resourceName, String prefix, String suffix) throws IOException {
        URL resourceUrl = WebSocketTestClient.class.getClassLoader().getResource(resourceName);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource not found: " + resourceName);
        }

        File tempFile = Files.createTempFile(prefix, suffix).toFile();
        tempFile.deleteOnExit();

        try (InputStream in = resourceUrl.openStream();
                OutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
}
