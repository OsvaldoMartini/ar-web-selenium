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
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
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
    private ToggleButton tbOnlineRequest;
    private ToggleButton tbDirectoryRequest;
    private Button btnPingStatus;
    private Label lblPingHint;
    private Label lblActionHint;
    private TextField tfOrganization;
    private TextField tfLicenseOwner;
    private TextField tfEmail;
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

        // ── Toggle buttons: Online vs Directory ──
        ToggleGroup requestModeGroup = new ToggleGroup();

        // Ping status indicator (rounded circle)
        btnPingStatus = new Button();
        btnPingStatus.setPrefSize(18, 18);
        btnPingStatus.setMinSize(18, 18);
        btnPingStatus.setMaxSize(18, 18);
        btnPingStatus.setStyle(
                "-fx-background-color: #9e9e9e; -fx-background-radius: 9; -fx-border-radius: 9; -fx-cursor: hand;");
        btnPingStatus.setTooltip(new Tooltip("API status: unknown"));

        tbOnlineRequest = new ToggleButton("On Line Request");
        tbOnlineRequest.setToggleGroup(requestModeGroup);
        tbOnlineRequest.setPrefWidth(200);
        tbOnlineRequest.setStyle("-fx-background-color: #e0e0e0; -fx-font-weight: bold; -fx-background-radius: 5;");

        tbDirectoryRequest = new ToggleButton("Request Target Directory");
        tbDirectoryRequest.setToggleGroup(requestModeGroup);
        tbDirectoryRequest.setSelected(true);
        tbDirectoryRequest.setPrefWidth(200);
        tbDirectoryRequest.setStyle(
                "-fx-background-color: linear-gradient(#29abe2, #007bff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");

        HBox toggleBox = new HBox(10, btnPingStatus, tbOnlineRequest, tbDirectoryRequest);
        toggleBox.setAlignment(Pos.CENTER_LEFT);

        // Hint label that fades away after a few seconds
        lblPingHint = new Label();
        lblPingHint.setStyle("-fx-font-size: 11px; -fx-padding: 2 0 0 28;");
        lblPingHint.setVisible(false);
        lblPingHint.setManaged(false);

        filePathField = new TextField();
        HBox.setHgrow(filePathField, Priority.ALWAYS);

        uploadButton = new Button("Change Destination Folder (Desktop)");
        uploadButton.setPrefWidth(300);
        uploadButton.setStyle(
                "-fx-background-color: linear-gradient(#29abe2, #007bff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");

        VBox radioButtonsVBox = new VBox(10, rbRequestLicense, rbActivateLicense, rbUseExistentLicense);
        radioButtonsVBox.setPadding(new Insets(10));
        radioButtonsVBox.setAlignment(Pos.TOP_LEFT);

        HBox filePathBox = new HBox(10, filePathField);

        HBox uploadButtonBox = new HBox(10, uploadButton);

        VBox vertButton = new VBox(10, toggleBox, lblPingHint, uploadButtonBox, filePathBox);
        vertButton.setPadding(new Insets(10));
        vertButton.setAlignment(Pos.TOP_LEFT);

        HBox.setHgrow(filePathBox, Priority.ALWAYS);
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

        tfOrganization = new TextField();
        tfOrganization.setPromptText("Organization name (mandatory)");

        tfLicenseOwner = new TextField();
        tfLicenseOwner.setPromptText("User Name");

        tfEmail = new TextField();
        tfEmail.setPromptText("Email address (for receiving license response)");

        // Checkbox to agree
        cbAgree = new CheckBox("Agree");
        cbAgree.setPadding(new Insets(10));

        // Button to proceed
        btnProceed = builder.buildButton("Proceed");
        btnProceed.setDisable(true);

        btnClose = builder.buildButton("Close");

        HBox actionButtonsBox = new HBox(10, btnProceed, btnClose);
        actionButtonsBox.setPadding(new Insets(10));

        // Action hint label (fade-away, below buttons)
        lblActionHint = new Label();
        lblActionHint.setStyle("-fx-font-size: 11px; -fx-padding: 0 0 0 10;");
        lblActionHint.setVisible(false);
        lblActionHint.setManaged(false);
        lblActionHint.setWrapText(true);

        VBox mainLayout = new VBox(
                10,
                headerContainer,
                radioAndUploadBox,
                taLicenseAgreement,
                tfOrganization,
                tfLicenseOwner,
                tfEmail,
                cbAgree,
                actionButtonsBox,
                lblActionHint);

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
        // ── Toggle button styling and behavior ──
        Runnable updateToggleStyles = () -> {
            if (tbOnlineRequest.isSelected()) {
                tbOnlineRequest.setStyle(
                        "-fx-background-color: linear-gradient(#29abe2, #007bff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
                tbDirectoryRequest.setStyle(
                        "-fx-background-color: #e0e0e0; -fx-font-weight: bold; -fx-background-radius: 5;");
                uploadButton.setDisable(true);
                filePathField.setDisable(true);
            } else if (tbDirectoryRequest.isSelected()) {
                tbDirectoryRequest.setStyle(
                        "-fx-background-color: linear-gradient(#29abe2, #007bff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
                tbOnlineRequest.setStyle(
                        "-fx-background-color: #e0e0e0; -fx-font-weight: bold; -fx-background-radius: 5;");
                uploadButton.setDisable(false);
                filePathField.setDisable(false);
            }
        };

        // Ping helper — updates the status indicator + shows fade-away hint
        Runnable doPing = () -> {
            btnPingStatus.setStyle(
                    "-fx-background-color: #ff9800; -fx-background-radius: 9; -fx-border-radius: 9; -fx-cursor: hand;");
            btnPingStatus.getTooltip().setText("Checking...");
            lblPingHint.setOpacity(1.0);
            lblPingHint.setText("Connecting to server...");
            lblPingHint.setStyle("-fx-font-size: 11px; -fx-padding: 2 0 0 28; -fx-text-fill: #ff9800;");
            lblPingHint.setVisible(true);
            lblPingHint.setManaged(true);
            new Thread(() -> {
                        boolean reachable = LicenseManager.pingApi();
                        Platform.runLater(() -> {
                            if (reachable) {
                                log.info("API ping successful");
                                btnPingStatus.setStyle(
                                        "-fx-background-color: #4caf50; -fx-background-radius: 9; -fx-border-radius: 9; -fx-cursor: hand;");
                                btnPingStatus.getTooltip().setText("Server online");
                                filePathField.setText("");
                                lblPingHint.setText("Connected to server");
                                lblPingHint.setStyle(
                                        "-fx-font-size: 11px; -fx-padding: 2 0 0 28; -fx-text-fill: #4caf50; -fx-font-weight: bold;");
                            } else {
                                log.warn("API ping failed: {}", LicenseManager.API_URL);
                                btnPingStatus.setStyle(
                                        "-fx-background-color: #f44336; -fx-background-radius: 9; -fx-border-radius: 9; -fx-cursor: hand;");
                                btnPingStatus.getTooltip().setText("API unreachable");
                                filePathField.setText("");
                                lblPingHint.setText("Server unreachable - check your connection");
                                lblPingHint.setStyle(
                                        "-fx-font-size: 11px; -fx-padding: 2 0 0 28; -fx-text-fill: #f44336; -fx-font-weight: bold;");
                                tbDirectoryRequest.setSelected(true);
                                updateToggleStyles.run();
                            }
                            // Fade out the hint after 4 seconds
                            PauseTransition pause = new PauseTransition(Duration.seconds(4));
                            pause.setOnFinished(ev -> {
                                FadeTransition fade = new FadeTransition(Duration.seconds(1.5), lblPingHint);
                                fade.setFromValue(1.0);
                                fade.setToValue(0.0);
                                fade.setOnFinished(fe -> {
                                    lblPingHint.setVisible(false);
                                    lblPingHint.setManaged(false);
                                });
                                fade.play();
                            });
                            pause.play();
                        });
                    })
                    .start();
        };

        // Click the indicator to re-ping
        btnPingStatus.setOnAction(event -> doPing.run());

        tbOnlineRequest.setOnAction(event -> {
            if (!tbOnlineRequest.isSelected()) tbOnlineRequest.setSelected(true);
            updateToggleStyles.run();
            doPing.run();
        });
        tbDirectoryRequest.setOnAction(event -> {
            if (!tbDirectoryRequest.isSelected()) tbDirectoryRequest.setSelected(true);
            updateToggleStyles.run();
            btnPingStatus.setStyle(
                    "-fx-background-color: #9e9e9e; -fx-background-radius: 9; -fx-border-radius: 9; -fx-cursor: hand;");
            btnPingStatus.getTooltip().setText("API status: unknown");
        });

        // Add event handlers to log the state change to the console
        rbRequestLicense.setOnAction(event -> {
            if (rbRequestLicense.isSelected()) {
                uploadButton.setText("Change Destination Folder (Desktop)");
                filePathField.setText("");
                tfLicenseOwner.setDisable(false);
                tfEmail.setDisable(false);
                tfOrganization.setDisable(false);
                tbOnlineRequest.setDisable(false);
                tbDirectoryRequest.setDisable(false);
                updateToggleStyles.run();
                defineDesktopFolder();
            }
        });

        rbActivateLicense.setOnAction(event -> {
            if (rbActivateLicense.isSelected()) {
                uploadButton.setText("Locate Response File (Desktop)");
                filePathField.setText("");
                tfLicenseOwner.setDisable(true);
                tfEmail.setDisable(true);
                tfOrganization.setDisable(true);
                tbOnlineRequest.setDisable(true);
                tbDirectoryRequest.setDisable(true);
                uploadButton.setDisable(false);
                filePathField.setDisable(false);
                defineDesktopFolder();
            }
        });

        rbUseExistentLicense.setOnAction(event -> {
            if (rbUseExistentLicense.isSelected()) {
                uploadButton.setText("Locate Existing License");
                filePathField.setText("");
                tfLicenseOwner.setDisable(true);
                tfEmail.setDisable(true);
                tfOrganization.setDisable(true);
                tbOnlineRequest.setDisable(true);
                tbDirectoryRequest.setDisable(true);
                uploadButton.setDisable(false);
                filePathField.setDisable(false);
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
                            showActionHint("Invalid file - must be a .response file", "#f44336", 4);
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
                showActionHint("Please agree to the license terms first", "#f44336", 4);
            } else
                try {
                    if (rbRequestLicense.isSelected()
                            && tfOrganization.getText().trim().isEmpty()) {
                        showActionHint("Please fill in the \"Organization\" field", "#f44336", 4);
                    } else if (rbRequestLicense.isSelected()
                            && !tfEmail.getText().trim().isEmpty()
                            && !LicenseManager.isEmail(tfEmail.getText().trim())) {
                        showActionHint("Invalid email format", "#f44336", 4);
                    } else {
                        if (rbRequestLicense.isSelected()) {
                            String organization = tfOrganization.getText().trim();
                            String owner = tfLicenseOwner.getText().trim();
                            String email = tfEmail.getText().trim();

                            if (tbOnlineRequest.isSelected()) {
                                // ── Online request: send to API ──
                                String result = LicenseManager.sendRequestOnline(organization, owner, email);
                                if ("SUCCESS".equals(result)) {
                                    log.info("Online request sent for org={}", organization);
                                    showActionHint("Request sent successfully (" + organization + ")", "#4caf50", 5);
                                } else {
                                    log.warn("Online request failed: {}", result);
                                    showActionHint("Request failed - check connection", "#f44336", 5);
                                }
                            } else {
                                // ── Directory request: save to file ──
                                if (!Strings.isNullOrEmpty(
                                        filePathField.getText().trim())) {
                                    fileFolder = filePathField.getText().trim();
                                }
                                LicenseManager.generateRequestFile(fileFolder, organization, owner, email);
                                log.info("Request file saved to {}", fileFolder);
                                showActionHint("Request file saved to " + fileFolder, "#4caf50", 5);
                            }

                        } else if (rbActivateLicense.isSelected()) {

                            if (!Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                fileFolder = filePathField.getText().trim();
                            } else {
                                fileFolder += "/ARWeb 1.1.0.response";
                            }

                            if (LicenseManager.importResponseFile(fileFolder)) {
                                String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
                                if (Strings.isNullOrEmpty(licensePath)) {
                                    licensePath = System.getProperty("user.dir");
                                }
                                if (checkLicense(licensePath)) {
                                    log.info("License activated: {}", licensePath);
                                    showActionHint("License activated successfully", "#4caf50", 5);
                                }
                            } else {
                                log.warn("Activation failed: response file not found at {}", fileFolder);
                                showActionHint("Activation failed - response file not found", "#f44336", 5);
                            }
                        } else if (rbUseExistentLicense.isSelected()) {
                            if (Strings.isNullOrEmpty(filePathField.getText().trim())) {
                                showActionHint("Please select a license file location", "#f44336", 4);
                            } else {
                                String licensePath = filePathField.getText().trim();
                                licensePath = licensePath.substring(0, licensePath.lastIndexOf("\\"));

                                if (checkLicense(licensePath)) {
                                    log.info("License located: {}", licensePath);
                                    showActionHint("License located and valid", "#4caf50", 5);
                                    arPropertyManager.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), licensePath);
                                }
                            }
                        }
                    }
                } catch (Exception error) {
                    log.error("License Activation Error: {} ->  {}", fileFolder, error.getMessage());
                    showActionHint("Error: " + error.getMessage(), "#f44336", 6);
                }
        });
    }

    private boolean checkLicense(String licensePath) throws Exception {
        LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);
        if (!licenseStatus.equals(LicenceVal.VALID)) {
            log.warn("License check failed: {}", licenseStatus.getStaus());
            showActionHint("License invalid: " + licenseStatus.getStaus(), "#f44336", 5);
            return false;
        }
        return true;
    }

    /**
     * Shows a short fade-away hint below the action buttons.
     * @param message short text
     * @param color   CSS color (#4caf50 for success, #f44336 for error, #ff9800 for warning)
     * @param seconds how long to display before fading
     */
    private void showActionHint(String message, String color, double seconds) {
        lblActionHint.setText(message);
        lblActionHint.setStyle(
                "-fx-font-size: 11px; -fx-padding: 0 0 0 10; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
        lblActionHint.setOpacity(1.0);
        lblActionHint.setVisible(true);
        lblActionHint.setManaged(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
        pause.setOnFinished(ev -> {
            FadeTransition fade = new FadeTransition(Duration.seconds(1.5), lblActionHint);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(fe -> {
                lblActionHint.setVisible(false);
                lblActionHint.setManaged(false);
            });
            fade.play();
        });
        pause.play();
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
                log.warn("Error reading/writing to the file! -> Desktop Folder");
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
            log.warn("Error reading/writing to the file: " + fileFolder);
            //            performMessage.errorMessage(
            //                    "Error reading/writing to the file!",
            //                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please verify that
            // you have the necessary permissions to read and write to the specified directory.</span>",
            //                    "<span style='color: #E65100; font-weight: bold;'>Attempted to access the following
            // location:</span> <span style='font-weight: bold;'>Desktop</span>",
            //                    "<span style='color: #E65100; font-style: italic; font-weight: bold;'>The request for
            // the License file path was defined at:</span>",
            //                    "<span style='color: #1A237E; font-style: italic; font-weight: bold; font-size:
            // 1.05em;'>"
            //                            + fileFolder + "</span>",
            //                    0);
        }
    }
}
