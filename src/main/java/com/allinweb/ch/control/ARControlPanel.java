package com.allinweb.ch.control;

import com.allinweb.ch.component.scene.ARMainScene;
import com.allinweb.ch.util.ARPropertyManager;
import java.util.Arrays;
import java.util.List;
import javafx.application.Application;
import javafx.stage.Stage;

public class ARControlPanel extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        ARMainScene primaryStage = new ARMainScene();
        primaryStage.show();
        // Capture the close button click (X button on the window)
        //        primaryStage.hsetOnCloseRequest(event -> {
        //            // Call your method here when the X button is clicked
        //            handleWindowClose(event);
        //        });

    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("-c")) {
            int configurationValueIndex = arguments.indexOf("-c") + 1;
            String configurationValue = arguments.get(configurationValueIndex);
            ARPropertyManager.setConfigurationFileName(configurationValue);
        }
        launch();
    }
}
