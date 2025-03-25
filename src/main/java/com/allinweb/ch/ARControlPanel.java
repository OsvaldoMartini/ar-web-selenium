package com.allinweb.ch;

import com.allinweb.ch.component.scene.ARLicenseScene;
import com.allinweb.ch.component.scene.ARMainScene;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.licence.LicenseManager;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ARControlPanel extends Application {

    private static final PerformMessage performMessage;

    static {
        performMessage = PerformMessage.getInstance();
    }

    private static boolean isEnabledLicence = false;

    @Override
    public void start(Stage stage) throws Exception {
        ARMainScene primaryStage = new ARMainScene();
        primaryStage.show();
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(0)) { // Port 0 = auto-assign
            int availablePort = serverSocket.getLocalPort();
            System.out.println("Available port: " + availablePort);
        } catch (IOException e) {

            System.out.println("Fixed Port : " + 54525);
        }

        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("-c")) {
            int configurationValueIndex = arguments.indexOf("-c") + 1;
            String configurationValue = arguments.get(configurationValueIndex);
            ARPropertyManager.setConfigurationFileName(configurationValue);
        }

        if (isEnabledLicence) {
            try {
                if (!LicenseManager.checkLicenseFile().isActive()) {
                    // If the license is not active, launch the license activation app
                    //                    Application.launch(LicenseActivationApp.class, args);
                    Platform.runLater(() -> {
                        new ARLicenseScene().showModal();

                        ARMainScene primaryStage = new ARMainScene();
                        primaryStage.show();
                    });

                } else {
                    // If the license is active, proceed with the main application
                    //                    Application.launch();
                    ARMainScene primaryStage = new ARMainScene();
                    primaryStage.show();
                }
            } catch (Exception e) {
                Text variableText1Styled = new Text("The license file is corrupted.");
                variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                Text variableText2Styled = new Text("Please contact the system administrator for assistance!");
                variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                VBox combinedTextContainer = new VBox();
                combinedTextContainer.setSpacing(5); // Add some space
                combinedTextContainer.getChildren().addAll(variableText1Styled, variableText2Styled);

                Platform.runLater(() -> performMessage.showAlertCombinedVBOX(
                        Alert.AlertType.ERROR,
                        "License Validation!",
                        "Validation Failed",
                        null,
                        combinedTextContainer));
                ARLogger.getInstance(ARControlPanel.class).fine(e.getMessage());
            }
        } else {
            // If the license is disabled, directly proceed with the main application
            // Ensure launch(args) is only called once: JavaFX does not allow calling Application.launch() twice.
            launch();
        }
    }
}
