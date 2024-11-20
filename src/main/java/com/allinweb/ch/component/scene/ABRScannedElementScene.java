package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ABRScannedElementPane;
import com.allinweb.ch.component.pane.base.IABRPane;
import com.allinweb.ch.component.scene.base.ABRScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class ABRScannedElementScene extends ABRScene {

    private static final Double SCENE_HEIGHT = 650D;
    private static final Double SCENE_WIDTH = 1000D;
    private static final String TITLE = "Scanner Tool";

    private ABRWebDriver abrWebDriver;
    private final Integer botJobId;
    private final Integer blockId;
    private final String priority;

    public ABRScannedElementScene(String priority, Integer botJobId, Integer blockId) {
        super();
        this.priority = priority;
        this.botJobId = botJobId;
        this.blockId = blockId;
    }

    @Override
    public IABRPane buildPane() {
        abrWebDriver = new ABRWebDriver(); // Initialize WebDriver
        return new ABRScannedElementPane(
                ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId),
                blockId != null ? ABRSharedResources.getInstance().getEntityById(BlockDTO.class, blockId) : null,
                abrWebDriver);
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
        if (abrWebDriver != null) {
            try {
                //                abrWebDriver.closeDriver(); // Quit WebDriver
                abrWebDriver.getDriver().quit(); // Quit WebDriver
                abrWebDriver.setDriver(null);
                //                abrWebDriver.setDriver(null);
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
