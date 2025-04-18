package com.allinweb.ch;

import com.allinweb.ch.component.scene.ARLicenseScene;
import com.allinweb.ch.component.scene.ARMainScene;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.licence.LicenceVal;
import com.allinweb.ch.licence.LicenseManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ARControlPanel extends Application {

    private static final PerformMessage performMessage;
    private static final ARPropertyManager arPropertyManager;
    private static String configurationFileName = ARConstants.CURRENT_PATH + ARConstants.FILE_NAME_CONFIGURATION;

    static {
        performMessage = PerformMessage.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
    }

    private static boolean isEnabledLicence = true;

    @Override
    public void start(Stage stage) throws Exception {
        ARMainScene primaryStage = new ARMainScene();
        primaryStage.show();
    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("-c")) {
            int configurationValueIndex = arguments.indexOf("-c") + 1;
            String configurationValue = arguments.get(configurationValueIndex);
            ARPropertyManager.setConfigurationFileName(configurationValue);
            arPropertyManager.loadProperties();
            ARLogger.getInstance(ARControlPanel.class).fine("Configuration file path: " + configurationFileName);
        } else {
            ARPropertyManager.setConfigurationFileName(configurationFileName);
            arPropertyManager.loadProperties();
            ARLogger.getInstance(ARControlPanel.class).fine("Configuration file path: " + configurationFileName);
        }

        arPropertyManager.setProperty(ARPropertyEnum.VERSION.getValue(), "ARS Web v4.0f Beta Test");
        arPropertyManager.setProperty(ARPropertyEnum.BUILD.getValue(), "Build: 16-04-2025");

        try (ServerSocket serverSocket = new ServerSocket(0)) { // Port 0 = auto-assign
            int availablePort = serverSocket.getLocalPort();
            System.out.println("Available port: " + availablePort);
            ARPropertyManager.getInstance()
                    .setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(availablePort));
        } catch (IOException e) {
            System.out.println("Fixed Port : " + 54525);
            ARPropertyManager.getInstance().setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(54525));
        }

        if (isEnabledLicence) {
            AtomicReference<LicenceVal> license = new AtomicReference<>();
            try {
                license.set(LicenseManager.checkLicenseFile());
            } catch (Exception e) {
                performMessage.errorMessage(
                        "Error reading/writing to the file!",
                        "File Name:",
                        "Please verify that you have permission to read/write to the Desktop.",
                        null,
                        null,
                        0);
            }
            try {
                if (license.get().isMissing()) {
                    // If the license is not active, launch the license activation app
                    //                    Application.launch(LicenseActivationApp.class, args);
                    Platform.runLater(() -> {
                        new ARLicenseScene().showModal();

                        try {
                            license.set(LicenseManager.checkLicenseFile());
                            if (license.get().isActive()) {
                                ARMainScene primaryStage = new ARMainScene();
                                primaryStage.show();
                            } else {
                                licenseMessages(license.get());
                            }
                        } catch (Exception e) {
                            performMessage.errorMessage(
                                    "Error reading/writing to the file!",
                                    "File Name:",
                                    "Please verify that you have permission to read/write to the Desktop.",
                                    null,
                                    null,
                                    0);
                        }
                    });

                } else {
                    license.set(LicenseManager.checkLicenseFile());
                    if (license.get().isActive()) {
                        ARMainScene primaryStage = new ARMainScene();
                        primaryStage.show();
                    } else {
                        licenseMessages(license.get());
                    }
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

    private static void licenseMessages(LicenceVal licenceVal) {
        performMessage.showCustomModalDialogDragWin11(
                "The license not valid!",
                "License Status:",
                "<span style='color: #000080; font-weight: bold;'>" + licenceVal.toString() + "</span>",
                "Please contact support to renew your license.",
                "Expiration :<span style='color: #000080; font-weight: bold;'>"
                        + arPropertyManager.getProperty(ARPropertyEnum.EXPIRATION) + "</span>",
                true,
                "OK",
                null,
                0);
    }
}
