package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.SingletonSupplier;
import java.time.format.DateTimeFormatter;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.websocket.Session;

public class ARScannedElementScene extends ARScene {

    private static final Double SCENE_HEIGHT = 650D;
    private static final Double SCENE_WIDTH = 1100D;
    private static final String TITLE = "AR Web Factory";

    private PerformDataBase performDataBase;
    private PerformActions performActions;
    private PerformMessage performMessage;

    private ARWebDriver arWebDriver;
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<ARScannedElementScene> instance = () -> new ARScannedElementScene();

    // Public method to access the singleton instance
    public static ARScannedElementScene getInstance() {
        return instance.get();
    }

    // Private constructor to prevent instantiation
    public ARScannedElementScene() {
        // Initialize if necessary
        super();
    }

    private Integer botJobId;
    private Integer blockId;
    private HomeBankingLoadDTO homeBankingLoadDTO;
    private BotJobLoadDTO botJobLoadDTO;
    private BlockLoadDTO blockLoadDTO;
    private String priority;
    private Session session;
    private String sessionId;

    public ARScannedElementScene initialize(
            PerformDataBase performDataBase,
            PerformActions performActions,
            PerformMessage performMessage,
            ARWebDriver arWebDriver,
            HomeBankingLoadDTO homeBankingLoadDTO,
            BotJobLoadDTO botJobLoadDTO,
            BlockLoadDTO blockLoadDTO) {
        this.performDataBase = performDataBase;
        this.performActions = performActions;
        this.performMessage = performMessage;
        this.arWebDriver = arWebDriver;
        this.homeBankingLoadDTO = homeBankingLoadDTO;
        this.botJobLoadDTO = botJobLoadDTO;
        this.blockLoadDTO = blockLoadDTO;
        return this;
    }

    @Override
    public IARPane buildPane() {
        return new ARScannedElementPane(
                performDataBase,
                performActions,
                performMessage,
                arWebDriver,
                homeBankingLoadDTO,
                botJobLoadDTO,
                blockLoadDTO);
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

    private void handleCloseRequest(WindowEvent event) {
        System.out.println("Handle Close: Exiting Threads and Quitting WebDriver");

        // Interrupt running threads
        threadList.forEach(this::interruptThread);

        // Close WebDriver if it's initialized
        if (arWebDriver != null) {
            try {
                //                arWebDriver.closeDriver(); // Quit WebDriver
                arWebDriver.getDriver().quit(); // Quit WebDriver
                arWebDriver.setDriver(null);
                //                arWebDriver.setDriver(null);
                System.out.println("WebDriver quit successfully.");
            } catch (Exception e) {
                System.err.println("Error closing WebDriver: " + e.getMessage());
            }
        }
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
}
