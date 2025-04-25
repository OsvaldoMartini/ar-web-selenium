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
import javafx.stage.Stage;

public class ARControlPanel extends Application {

    private static final PerformMessage performMessage;
    private static final ARPropertyManager arPropertyManager;
    private static String defaultConfigurationFileName = ARConstants.CURRENT_PATH + ARConstants.FILE_NAME_CONFIGURATION;

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
            try {
                System.setProperty("ARWebConfig", configurationValue);
            } catch (Exception ignore) {

            }
            // Prevention if  System.setProperty(...) has no permission access
            arPropertyManager.setConfigurationFileName(configurationValue);
            arPropertyManager.loadProperties();
            ARLogger.getInstance(ARControlPanel.class).fine("Configuration file path: " + configurationValue);
        } else {
            try {
                System.setProperty("ARWebConfig", defaultConfigurationFileName);
            } catch (Exception ignore) {

            }
            ;
            arPropertyManager.setConfigurationFileName(defaultConfigurationFileName);
            arPropertyManager.loadProperties();
            ARLogger.getInstance(ARControlPanel.class).fine("Configuration file path: " + defaultConfigurationFileName);
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
            arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(54525));
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
            } catch (Exception error) {
                String pathDB = arPropertyManager.getProperty(ARPropertyEnum.FOLDER_PATH_DB);
                performMessage.errorMessage(
                        "Access Database Error",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Failed to open or access the database!</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Database path:</span> <span style='font-weight: bold;'>"
                                + pathDB + "</span>",
                        "<span style='font-style: italic;'>Please ensure the file exists and the application has the necessary read/write permissions.</span>",
                        "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                        0);

                ARLogger.getInstance(ARControlPanel.class).fine(error.getMessage());
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
