package com.allinweb.ch.license;

import com.allinweb.ch.facade.PerformMessage;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LicenseActivationApp extends Application {

    private static final PerformMessage performMessage;

    static {
        performMessage = PerformMessage.getInstance();
    }

    private String fileFolder;

    @Override
    public void start(Stage primaryStage) throws Exception {
        String licensePath = System.getProperty("user.dir");
        LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

        String msgValid = "The license file is valid and the application is authorized for use.";
        String msgNextStep = "You can now proceed with normal application usage.";

        String msgColor = "#0277BD";
        if (!licenseStatus.equals(LicenceVal.VALID)) {
            msgValid = "The license file is not valid and the application is not authorized for use.";
            msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
            msgColor = "#C62828"; // Soft, elegant red tone
        }

        performMessage.showCustomModalDialogDragWin11(
                "License Status Verification",
                "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>License status has been successfully verified.</span>",
                "<span style='color: " + msgColor + "; font-weight: bold;'>" + msgValid + "</span>",
                "<span style='font-style: italic;'>" + msgNextStep + "</span>",
                "<span style='color: #E65100; font-weight: bold;'>Current license status:</span> <span style='font-weight: bold;'>"
                        + licenseStatus.getStaus() + "</span>",
                false,
                "OK",
                null,
                0);

        // Header label for the application
        Label headerLabel = new Label("AR Web Activation software required");
        headerLabel.setStyle(
                "-fx-background-color: #0078d7; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10;");
        headerLabel.setMinWidth(500);
        headerLabel.setMaxHeight(Double.MAX_VALUE); // Ensure the label stretches across the top

        // ToggleGroup for exclusive RadioButton selection
        ToggleGroup toggleGroup = new ToggleGroup();

        RadioButton rbRequestLicense = new RadioButton("Request License");
        rbRequestLicense.setToggleGroup(toggleGroup);
        rbRequestLicense.setSelected(true);

        RadioButton rbActivateLicense = new RadioButton("Activate with License");
        rbActivateLicense.setToggleGroup(toggleGroup);

        HBox radioButtonsBox = new HBox(10, rbRequestLicense, rbActivateLicense);
        radioButtonsBox.setPadding(new Insets(10));

        // TextArea for the License Agreement
        TextArea taLicenseAgreement = new TextArea(
                "SOFTWARE LICENSE AGREEMENT\n\n"
                        + "Important - Read Carefully: This License Agreement (\"Agreement\") is a legal contract between you (an individual or a legal entity) and Allinweb SA. (\"Licensor\") for the software that accompanies this agreement, which includes associated software and media material, whether printed, electronic, or online (\"Software\").\n\n"
                        + "1. License Grant: Subject to the terms of this Agreement, the Licensor grants you a non-exclusive, non-transferable license to use the Software for internal purposes according to the following limitations and in compliance with the provided documentation.\n\n"
                        + "2. Restrictions: You are not authorized to:\n"
                        + "   - Modify, translate, adapt, or create derivative works from the Software.\n"
                        + "   - Reverse engineer, decompile, disassemble, or otherwise attempt to discover the Software’s source code.\n"
                        + "   - Resell, rent, sublicense, distribute, or otherwise transfer the Software without prior written consent from the Licensor.\n"
                        + "   - Remove any copyright notices, trademarks, or other proprietary notices included in the Software.\n\n"
                        + "3. Ownership of the Software: The Software is protected by copyright laws and international treaties, as well as other intellectual property laws and treaties. The Software is licensed, not sold.\n\n"
                        + "4. Limited Warranty: The Licensor warrants that the Software will operate substantially in accordance with the documentation for a period of ninety (90) days from the date of your purchase. Any replacement Software will be warranted for the remainder of the original warranty period or for thirty (30) days, whichever is longer.\n\n"
                        + "5. Limitation of Liability: In no event shall the Licensor be liable for special, incidental, indirect, or consequential damages resulting from the use or inability to use the Software, even if the Licensor has been advised of the possibility of such damages. In no event shall the Licensor’s liability for damages exceed the amount paid to purchase the Software.\n\n"
                        + "6. Termination: This Agreement remains in effect until terminated. This Agreement will automatically terminate without notice from the Licensor if you fail to comply with any term or condition of this Agreement.\n\n"
                        + "7. Miscellaneous: This Agreement constitutes the entire agreement between you and the Licensor and supersedes all prior communications, proposals, or agreements, whether verbal or written, regarding the Software.\n");

        taLicenseAgreement.setWrapText(true);
        taLicenseAgreement.setEditable(false);
        ScrollPane scrollPane = new ScrollPane(taLicenseAgreement);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        // TextField for entering the license owner's name
        TextField tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("Licensed to (Owner of the license, min 6 chars)");

        // Checkbox to agree
        CheckBox cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        Button btnProceed = new Button("Proceed");
        btnProceed.setDisable(true); // Initially disabled

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        // Actions for Proceed button
        btnProceed.setOnAction(event -> {
            if (!cbAgree.isSelected()) {
                performMessage.errorMessage(
                        "License Aggreement!",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please read and agrred with our license terms!</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Acknowledge and accept the license agreement to proceed with the installation.</span>",
                        "<span style='font-style: italic;'>This software is governed by legal terms and conditions. Your use constitutes acceptance of these terms.</span>",
                        null,
                        0);
            } else
                try {
                    if (tfLicenseOwner.getText().isEmpty() && !LicenseManager.importResponseFile(licensePath)) {
                        performMessage.errorMessage(
                                "Mandatory field is missing!",
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please provide the \"User License\" field information!</span>",
                                "<span style='color: #E65100; font-weight: bold;'>You must accept the User License Agreement to continue with the installation.</span>",
                                "<span style='font-style: italic;'>By proceeding, you confirm that you understand and agree to the terms of the User License.</span>",
                                null,
                                0);
                    } else {
                        try {
                            if (rbRequestLicense.isSelected()) {
                                String desktopDir = getDesktopDir();

                                LicenseManager.generateRequestFile(
                                        desktopDir, tfLicenseOwner.getText().trim());
                                performMessage.showCustomModalDialogDragWin11(
                                        "Request File Generated Successfully!",
                                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>The request file for license generation has been successfully created.</span>",
                                        "<span style='color: #0277BD; font-weight: bold;'>Please send this request file to your provider to receive the User License.</span>",
                                        "<span style='font-style: italic;'>This request file contains encrypted system information required for license activation.</span>",
                                        "<span style='color: #E65100; font-weight: bold;'>License path:</span> <span style='font-weight: bold;'>"
                                                + licensePath + "</span>",
                                        false,
                                        "OK",
                                        null,
                                        0);
                            } else if (rbActivateLicense.isSelected()
                                    && LicenseManager.importResponseFile(licensePath)) {
                                performMessage.showCustomModalDialogDragWin11(
                                        "License Activated!",
                                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Your license has been successfully activated.</span>",
                                        "<span style='color: #0277BD; font-weight: bold;'>You may now use the application without restrictions.</span>",
                                        "<span style='font-style: italic;'>You can close this message and continue.</span>",
                                        "<span style='color: #E65100; font-weight: bold;'>License path:</span> <span style='font-weight: bold;'>"
                                                + licensePath + "</span>",
                                        false,
                                        "OK",
                                        null,
                                        0);
                            } else {

                                performMessage.errorMessage(
                                        "License Activation Failed!",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Response file not found or could not be processed.</span>",
                                        "<span style='color: #0277BD; font-weight: bold;'>Please make sure the response file is available and try again.</span>",
                                        "<span style='font-style: italic;'>Ensure the file was received from your provider and has not been modified.</span>",
                                        "<span style='color: #E65100; font-weight: bold;'>Expected license path:</span> <span style='font-weight: bold;'>"
                                                + licensePath + "</span>",
                                        0);
                            }
                        } catch (Exception error) {
                            performMessage.errorMessage(
                                    "License Activation Error",
                                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the license activation or verification process.</span>",
                                    "<span style='font-weight: bold;'>" + licensePath + "</span>.",
                                    "<span style='color: #E65100; font-weight: bold;'>Please ensure the response file is valid and accessible, and that the application has the required permissions.</span>",
                                    "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                                    0);
                        }
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        });

        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        // Button to close the application
        Button btnClose = new Button("Close");
        btnClose.setOnAction(event -> primaryStage.close());

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));

        // Main layout
        VBox mainLayout =
                new VBox(10, headerLabel, radioButtonsBox, scrollPane, tfLicenseOwner, cbAgree, actionButtonsBox);
        mainLayout.setPadding(new Insets(10));

        // Set up the scene
        Scene scene = new Scene(mainLayout, 600, 450); // Adjusted window size for better layout
        primaryStage.setTitle("Activation Software Required");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) throws Exception {
        String licensePath = System.getProperty("user.dir");
        if (args.length > 0) {
            licensePath = args[0];
        }

        if (!LicenseManager.checkLicenseFile(licensePath).isActive()) {
            launch(args);
        } else {
            System.out.println("AR Web agree licence terms are activate.\n\nPress OK to proceed.");
            //        Application.launch(LicenceResponseManagerApp.class, args); // Lancia questa
            // applicazione se la
            // condizione  falsa
        }
    }

    private static String getDesktopDir() throws IOException {
        PointerByReference ppszPath = new PointerByReference();
        if (Shell32.INSTANCE
                        .SHGetKnownFolderPath(KnownFolders.FOLDERID_Desktop, 0, null, ppszPath)
                        .intValue()
                != 0) {

            performMessage.errorMessage(
                    "Error reading/writing to the file!",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please verify that you have the necessary permissions to read and write to the specified directory.</span>",
                    "<span style='color: #E65100; font-weight: bold;'>Attempted to access the following location:</span> <span style='font-weight: bold;'>Desktop</span>",
                    "<span style='color: #E65100; font-style: italic; font-weight: bold;'>The request for the License file path was defined at:</span>",
                    "<span style='color: #1A237E; font-style: italic; font-weight: bold; font-size: 1.05em;'>Desktop Folder</span>",
                    0);
            return null;
            //            throw new IOException("Failed to get desktop directory.");
        }

        // Convert pointer to string
        String desktopPath = ppszPath.getValue().getWideString(0);
        Native.free(Pointer.nativeValue(ppszPath.getValue()));
        return desktopPath;
    }
}
