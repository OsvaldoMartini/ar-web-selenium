package com.allinweb.ch.control;

import com.allinweb.ch.component.scene.ABRMainScene;
import com.allinweb.ch.util.ABRPropertyManager;
import java.util.Arrays;
import java.util.List;
import javafx.application.Application;
import javafx.stage.Stage;

public class ABRControlPanel extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        new ABRMainScene().show();
    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("-c")) {
            int configurationValueIndex = arguments.indexOf("-c") + 1;
            String configurationValue = arguments.get(configurationValueIndex);
            ABRPropertyManager.setConfigurationFileName(configurationValue);
        }
        launch();
    }
}
