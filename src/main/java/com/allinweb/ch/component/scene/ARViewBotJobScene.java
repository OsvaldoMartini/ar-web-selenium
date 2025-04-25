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
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.PerformPreLoad;
import com.allinweb.ch.util.ARLogger;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.openqa.selenium.WebDriver;

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

    private ObservableList<BotJobLoadDTO> botJobList;

    private ARScene currentScene;
    private ARWebDriver arWebDriver;
    private PerformDataBase performDataBase;
    private PerformActions performActions;
    private PerformMessage performMessage;
    private PerformPreLoad performPreLoad;
    private BotJobLoadDTO botJobLoad;

    public void initialize(
            ARWebDriver arWebDriver,
            PerformDataBase performDataBase,
            PerformActions performActions,
            PerformMessage performMessage,
            PerformPreLoad performPreLoad,
            BotJobLoadDTO botJobLoad,
            ObservableList<BotJobLoadDTO> botJobList) {
        this.arWebDriver = arWebDriver;
        this.performDataBase = performDataBase;
        this.performActions = performActions;
        this.performMessage = performMessage;
        this.performPreLoad = performPreLoad;
        this.botJobLoad = botJobLoad;
        this.botJobList = botJobList;

        this.currentScene = currentScene;
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

        return new ARViewBotJobPane(this, this.botLoadJob, botJobList);
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

    // Now you can access currentScene anywhere in this class
    public ARScene getCurrentScene() {
        return currentScene;
    }
}
