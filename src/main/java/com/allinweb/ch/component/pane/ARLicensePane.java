package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.ptr.PointerByReference;
import java.io.File;
import java.io.IOException;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARLicensePane extends ARPane {

    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARLicensePane instance;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    private Button btnLicense; // Declare the License button
    private Pane mainPane;
    private CheckBox cbAgree;
    private Button btnProceed;
    private Button btnClose;
    private RadioButton rbRequestLicense;
    private RadioButton rbActivateLicense;
    private RadioButton rbUseExistentLicense;
    private TextField tfLicenseOwner;
    private Button uploadButton;
    private TextField filePathField;
    private String fileFolder;

    // Private constructor to prevent instantiation
    private ARLicensePane() {

        super();
    }

    public static ARLicensePane getInstance() {
        if (instance == null) {
            synchronized (ARLicensePane.class) {
                if (instance == null) {
                    instance = new ARLicensePane();
                }
            }
        }
        return instance;
    }

    public void initialize() {
        // Set initial directory to Desktop
        defineDesktopFolder();
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Initialize the License button
        //        LicenseManager.showAlert(
        //                Alert.AlertType.INFORMATION, LicenseManager.checkLicenseFile().getStaus() + "\n\nPress OK to
        // proceed.");

        // Header label for the application
        // TextArea for the License Agreement
        TextArea taLicenseAgreement = new TextArea(
                """
                        SOFTWARE LICENSE AGREEMENT

                        Important - Read Carefully: This License Agreement ("Agreement") is a legal contract between you
                        (an individual or a legal entity) and Allinweb SA. ("Licensor") for the software that accompanies
                        this agreement, which includes associated software and media material, whether printed, electronic, or online ("Software").

                        1. License Grant:
                           Subject to the terms of this Agreement, the Licensor grants you a non-exclusive,
                           non-transferable license to use the Software for internal purposes according to the following limitations
                           and in compliance with the provided documentation.

                        2. Restrictions:
                           You are not authorized to:
                           - Modify, translate, adapt, or create derivative works from the Software.
                           - Reverse engineer, decompile, disassemble, or otherwise attempt to discover the Software's source code.
                           - Resell, rent, sublicense, distribute, or otherwise transfer the Software without prior written consent from the Licensor.
                           - Remove any copyright notices, trademarks, or other proprietary notices included in the Software.

                        3. Ownership of the Software:
                           The Software is protected by copyright laws and international treaties, as well as other intellectual
                           property laws and treaties. The Software is licensed, not sold.

                        4. Limited Warranty:
                           The Licensor warrants that the Software will operate substantially in accordance with the documentation
                           for a period of ninety (90) days from the date of your purchase. Any replacement Software will be
                           warranted for the remainder of the original warranty period or for thirty (30) days, whichever is longer.

                        5. Limitation of Liability:
                           In no event shall the Licensor be liable for special, incidental, indirect, or consequential damages
                           resulting from the use or inability to use the Software, even if the Licensor has been advised of
                           the possibility of such damages. In no event shall the Licensor's liability for damages exceed
                           the amount paid to purchase the Software.

                        6. Termination:
                           This Agreement remains in effect until terminated. This Agreement will automatically terminate without notice
                           from the Licensor if you fail to comply with any term or condition of this Agreement.

                        7. Miscellaneous:
                           This Agreement constitutes the entire agreement between you and the Licensor and supersedes all prior
                           communications, proposals, or agreements, whether verbal or written, regarding the Software.
                        """);

        Label headerLabel = new Label("AR Web Activation software required");
        headerLabel.setStyle("-fx-text-fill: white; " + // Keep text color white
                "-fx-font-size: 14px; "
                + "-fx-padding: 10;");

        HBox headerContainer = new HBox(headerLabel);
        headerContainer.setStyle("-fx-background-color: #0078d7;"); // Blue background
        headerContainer.setPadding(new Insets(10));
        headerContainer.setAlignment(Pos.CENTER_LEFT); // Align text to the left

        HBox.setHgrow(headerLabel, Priority.ALWAYS);
        HBox.setHgrow(headerContainer, Priority.ALWAYS);

        // ToggleGroup for exclusive RadioButton selection
        ToggleGroup toggleGroup = new ToggleGroup();

        rbRequestLicense = new RadioButton("Request New License");
        rbRequestLicense.setToggleGroup(toggleGroup);
        rbRequestLicense.setSelected(true);

        rbActivateLicense = new RadioButton("Activate Response File");
        rbActivateLicense.setToggleGroup(toggleGroup);

        rbUseExistentLicense = new RadioButton("Use Existing License");
        rbUseExistentLicense.setToggleGroup(toggleGroup);

        filePathField = new TextField();
        HBox.setHgrow(filePathField, Priority.ALWAYS);

        uploadButton = new Button("Request target Directory");
        uploadButton.setPrefWidth(300);
        uploadButton.setStyle(
                "-fx-background-color: linear-gradient(#29abe2, #007bff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");

        VBox radioButtonsVBox = new VBox(10, rbRequestLicense, rbActivateLicense, rbUseExistentLicense);
        radioButtonsVBox.setPadding(new Insets(10));
        radioButtonsVBox.setAlignment(Pos.TOP_LEFT);

        HBox filePathBox = new HBox(10, filePathField);
        //        filePathBox.setPadding(new Insets(10));
        //        filePathBox.setAlignment(Pos.TOP_LEFT);

        HBox uploadButtonBox = new HBox(10, uploadButton);
        //        uploadButtonBox.setPadding(new Insets(10));
        //        uploadButtonBox.setAlignment(Pos.TOP_LEFT);

        VBox vertButton = new VBox(10, uploadButtonBox, filePathBox);
        vertButton.setPadding(new Insets(10));
        vertButton.setAlignment(Pos.TOP_LEFT);

        HBox.setHgrow(filePathBox, Priority.ALWAYS); // Make the text field grow
        HBox.setHgrow(vertButton, Priority.ALWAYS);

        // HBox to hold both VBoxes side by side
        HBox radioAndUploadBox = new HBox(30, radioButtonsVBox, vertButton);
        radioAndUploadBox.setPadding(new Insets(10));
        radioAndUploadBox.setAlignment(Pos.TOP_LEFT);

        //        HBox radioButtonsBox = new HBox(10, rbRequestLicense, rbActivateLicense, rbUseExistentLicense,
        // uploadButton, filePathField);
        //        radioButtonsBox.setPadding(new Insets(10));

        taLicenseAgreement.setWrapText(true);
        taLicenseAgreement.setEditable(false);

        tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("Licensed to (Owner of the license, min 6 chars)");

        // Checkbox to agree
        cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        btnProceed = builder.buildButton("Proceed");
        btnProceed.setDisable(true);

        btnClose = builder.buildButton("Close");

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));
        VBox mainLayout = new VBox(
                10, headerContainer, radioAndUploadBox, taLicenseAgreement, tfLicenseOwner, cbAgree, actionButtonsBox);

        mainLayout.setPadding(new Insets(10));
        mainLayout.setFillWidth(true); // Ensure components stretch horizontally

        VBox.setVgrow(taLicenseAgreement, Priority.ALWAYS);

        AnchorPane.setTopAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(mainLayout, ARConstants.SPACE_M);

        mainPane = new AnchorPane(mainLayout);
    }

    @Override
    public void initUIBehaviour() {
        // Add event handlers to log the state change to the console
        rbRequestLicense.setOnAction(event -> {
            if (rbRequestLicense.isSelected()) {
                uploadButton.setText("Change Destination Folder (Desktop)");
                filePathField.setText("");
                tfLicenseOwner.setDisable(false);
                defineDesktopFolder();
            }
        });

        rbActivateLicense.setOnAction(event -> {
            if (rbActivateLicense.isSelected()) {
                uploadButton.setText("Locate Response File (Desktop)");
                filePathField.setText("");
                tfLicenseOwner.setDisable(false);
                defineDesktopFolder();
            }
        });

        rbUseExistentLicense.setOnAction(event -> {
            if (rbUseExistentLicense.isSelected()) {
                uploadButton.setText("Locate Existing License");
                filePathField.setText("");
                tfLicenseOwner.setDisable(true);
                defineDesktopFolder();
            }
        });

        uploadButton.setOnAction(e -> {
            Stage stage = (Stage) uploadButton.getScene().getWindow();
            File startingPoint = new File(fileFolder);
            String chosenPath;
            if (rbRequestLicense.isSelected()) {
                chosenPath = openDirectoryChooserFor(startingPoint, stage);
                filePathField.setText(chosenPath);
            } else {

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Open Request AR Web File");
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

                if (startingPoint.exists() && startingPoint.isDirectory()) {
                    fileChooser.setInitialDirectory(startingPoint);
                }

                File file = fileChooser.showOpenDialog(stage);

                if (file != null) {
                    filePathField.setText(file.getAbsolutePath());
                }

                if (file != null) {
                    if (rbActivateLicense.isSelected()) {
                        if (file.getName().endsWith(".response")) {
                            filePathField.setText(file.getAbsolutePath());
                        } else {
                            performMessage.errorMessage(
                                    "Invalid file selected!",
                                    "Must have a '.response' extension.",
                                    "File selected:",
                                    file.getName(),
                                    null,
                                    0);
                        }
                    } else {
                        filePathField.setText(file.getAbsolutePath());
                    }
                }
            }
        });
        // Enable the proceed button only if the checkbox is checked
        cbAgree.setOnAction(event -> btnProceed.setDisable(!cbAgree.isSelected()));

        btnClose.setOnAction(event -> {
            log.info("ARLicensePane close()");
            Platform.runLater(() -> {
                Stage stage = (Stage) btnClose.getScene().getWindow();
                stage.close();
            });
        });

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
                    if (tfLicenseOwner.getText().isEmpty() && (!rbUseExistentLicense.isSelected())) {
                        performMessage.errorMessage(
                                "Mandatory field is missing!",
                                "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please provide the \"User License\" field information!</span>",
                                null,
                                null,
                                null,
                                0);
                    } else {
                        if (rbRequestLicense.isSelected()) {
                            if (!Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                fileFolder = filePathField.getText().trim();
                            }
                            LicenseManager.generateRequestFile(
                                    fileFolder, tfLicenseOwner.getText().trim());
                            performMessage.showCustomModalDialogDragWin11(
                                    "Request File Generated Successfully!",
                                    "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>The request file for license generation has been successfully created.</span>",
                                    "<span style='color: #0277BD; font-weight: bold;'>Please send this request file to your provider to receive the User License.</span>",
                                    "<span style='font-style: italic;'>This request file contains encrypted system information required for license activation.</span>",
                                    "<span style='color: #E65100; font-weight: bold;'>Request file path:</span> <span style='font-weight: bold;'>"
                                            + fileFolder + "</span>",
                                    false,
                                    "OK",
                                    null,
                                    0);

                        } else if (rbActivateLicense.isSelected()) {

                            if (!Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                fileFolder = filePathField.getText().trim();
                            } else {
                                fileFolder += "\\ARWeb 1.1.0.response";
                            }

                            if (LicenseManager.importResponseFile(fileFolder)) {

                                String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
                                if (Strings.isNullOrEmpty(licensePath)) {
                                    licensePath = System.getProperty("user.dir");
                                }

                                if (checkLicense(licensePath)) {
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
                                }

                            } else {

                                performMessage.errorMessage(
                                        "License Activation Failed!",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Response file not found or could not be processed.</span>",
                                        "<span style='color: #0277BD; font-weight: bold;'>Please make sure the response file is available and try again.</span>",
                                        "<span style='font-style: italic;'>Ensure the file was received from your provider and has not been modified.</span>",
                                        "<span style='color: #E65100; font-weight: bold;'>Expected license path:</span> <span style='font-weight: bold;'>"
                                                + fileFolder + "</span>",
                                        0);
                            }
                        } else if (rbUseExistentLicense.isSelected()) {
                            if (Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                performMessage.errorMessage(
                                        "Mandatory field is missing!",
                                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please provide the \"License Location path\" field information!</span>",
                                        null,
                                        null,
                                        null,
                                        0);
                            } else {
                                String licensePath = filePathField.getText().trim();
                                licensePath = licensePath.substring(0, licensePath.lastIndexOf("\\"));

                                if (checkLicense(licensePath)) {
                                    performMessage.showCustomModalDialogDragWin11(
                                            "The License has been located!",
                                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Your license has been successfully located.</span>",
                                            "<span style='color: #0277BD; font-weight: bold;'>You may now use the application without restrictions.</span>",
                                            "<span style='font-style: italic;'>You can close this message and continue.</span>",
                                            "<span style='color: #E65100; font-weight: bold;'>License path:</span> <span style='font-weight: bold;'>"
                                                    + filePathField.getText().trim() + "</span>",
                                            false,
                                            "OK",
                                            null,
                                            0);

                                    arPropertyManager.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), licensePath);
                                }
                            }
                        }
                    }
                } catch (Exception error) {
                    performMessage.errorMessage(
                            "License Activation Error",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the license activation or verification process.</span>",
                            "<span style='font-weight: bold;'>" + fileFolder + "</span>.",
                            "<span style='color: #E65100; font-weight: bold;'>Please ensure the response file is valid and accessible, and that the application has the required permissions.</span>",
                            "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                            0);
                }
        });
    }

    private boolean checkLicense(String licensePath) throws Exception {
        LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

        String msgValid = "The license file is valid and the application is authorized for use.";
        String msgNextStep = "You can now proceed with normal application usage.";

        String msgColor = "#0277BD";
        if (!licenseStatus.equals(LicenceVal.VALID)) {
            msgValid = "The license file is not valid and the application is not authorized for use.";
            msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
            msgColor = "#C62828"; // Soft, elegant red tone

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
            return false;
        }
        return true;
    }

    private String openDirectoryChooserFor(File startingDirectory, Stage ownerStage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);

        // Make sure the dialog is shown in front of the provided stage
        File chosenPath = chooser.showDialog(ownerStage);
        return chosenPath != null ? chosenPath.getAbsolutePath() : null;
    }

    private void defineDesktopFolder() {
        try {
            // Use SHGetKnownFolderPath to get Desktop path
            PointerByReference ppszPath = new PointerByReference();
            if (Shell32.INSTANCE
                            .SHGetKnownFolderPath(KnownFolders.FOLDERID_Desktop, 0, null, ppszPath)
                            .intValue()
                    != 0) {
                //                performMessage.errorMessage(
                //                        "Error reading/writing to the file!",
                //                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please
                // verify that you have the necessary permissions to read and write to the specified directory.</span>",
                //                        "<span style='color: #E65100; font-weight: bold;'>Attempted to access the
                // following location:</span> <span style='font-weight: bold;'>Desktop</span>",
                //                        "<span style='color: #E65100; font-style: italic; font-weight: bold;'>The
                // request for the License file path was defined at:</span>",
                //                        "<span style='color: #1A237E; font-style: italic; font-weight: bold;
                // font-size: 1.05em;'>Desktop Folder</span>",
                //                        0);

                throw new IOException("Failed to get desktop directory.");
            }

            // Convert pointer to string
            String desktopPath = ppszPath.getValue().getWideString(0);
            Native.free(Pointer.nativeValue(ppszPath.getValue()));

            File desktopDir = new File(desktopPath);
            if (desktopDir.exists() && desktopDir.isDirectory()) {
                fileFolder = desktopDir.getAbsolutePath();
            }
        } catch (Exception ex) {
            if (Strings.isNullOrEmpty(fileFolder)) {
                fileFolder = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
                if (Strings.isNullOrEmpty(fileFolder)) {
                    fileFolder = System.getProperty("user.dir");
                }
            }

            performMessage.errorMessage(
                    "Error reading/writing to the file!",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please verify that you have the necessary permissions to read and write to the specified directory.</span>",
                    "<span style='color: #E65100; font-weight: bold;'>Attempted to access the following location:</span> <span style='font-weight: bold;'>Desktop</span>",
                    "<span style='color: #E65100; font-style: italic; font-weight: bold;'>The request for the License file path was defined at:</span>",
                    "<span style='color: #1A237E; font-style: italic; font-weight: bold; font-size: 1.05em;'>"
                            + fileFolder + "</span>",
                    0);
        }
    }
}
