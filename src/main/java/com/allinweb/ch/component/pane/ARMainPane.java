package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Slf4j
public class ARMainPane extends ARPane {

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    //    private static final ARSharedResources dbResource;
    private static final ARPropertyManager arPropertyManager;
    private static final PerformLists performLists;
    private static final PerformDBEngine performDBEngine;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final AROrganizationManagerScene arOrganizationManagerScene;
    private static final ARWebDriver arWebDriver;
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARMainPane instance;

    // Static block to initialize
    static {
        //        //        dbResource = PerformDataBase.;
        arPropertyManager = ARPropertyManager.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();
        performLists = PerformLists.getInstance();
        performDBEngine = PerformDBEngine.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
        arOrganizationManagerScene = AROrganizationManagerScene.getInstance();
        arWebDriver = ARWebDriver.getInstance();
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed
    // UI components
    Button organizationsButton;
    Button newBotJobButton;
    Button cloneBotJobButton;
    // Button viewBotJobButton;
    Button configureButton;
    Button infoButton;
    Button openBotJobButton;
    Button launchBotJobButton;
    Button exitButton;
    Button aiButton;
    TextArea aiTextArea;
    HBox buttonPane;
    VBox panelPane;
    GridPane header = new GridPane();

    @Getter
    public ListView<BotJobLoadDTO> viewBotJobListView = new ListView<>();

    private boolean isEnabledLicence;
    private ObservableList<BotJobLoadDTO> botJobList = FXCollections.observableArrayList();

    @Getter
    private ObservableList<WebDriver> webDriverList;

    private Properties properties = new Properties();
    private BotJobLoadDTO selecBotJobDTO;

    // Private constructor to prevent instantiation
    private ARMainPane() {

        super();
    }

    public static ARMainPane getInstance() {
        if (instance == null) {
            synchronized (ARMainPane.class) {
                if (instance == null) {
                    instance = new ARMainPane();
                }
            }
        }
        return instance;
    }

    private static int getMajorJavaVersion(String version) {
        // For Java 9 and above, the version string starts with the major version (e.g., "17.0.1")
        // For Java 8 and below, it starts with "1." (e.g., "1.8.0_311")
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3)); // e.g., "1.8" -> 8
        } else {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]); // e.g., "17.0.1" -> 17
        }
    }

    public void initialize(ObservableList<WebDriver> webDriverList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.webDriverList = webDriverList;

        botJobList.clear();
        ErrorMessage errorMessage = performDataBase.loadQuickBotJobs();
        if (!performDataBase.dbFailed && errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
        botJobList.addAll(performLists.getQuickBotJobs());
    }

    @Override
    public void initUIComponents() {
        double smallHeight = ARConstants.SPACE_S; // or just a smaller double like 20.0
        double smallIconSize = ARConstants.SPACE_S; // smaller icon size
        Insets smallPadding = new Insets(4, 6, 4, 6);

        String smallFont = "-fx-font-size: 11px;";

        organizationsButton =
                builder.buildButton("Organizations", smallHeight, ARConstants.ICON_ORGS, smallIconSize, smallPadding);
        organizationsButton.setStyle(
                "-fx-background-color: #FFD580; -fx-text-fill: #333; -fx-font-weight: bold; -fx-background-radius: 4; "
                        + smallFont);

        newBotJobButton =
                builder.buildButton("New Bot Job", smallHeight, ARConstants.ICON_NEW, smallIconSize, smallPadding);
        cloneBotJobButton =
                builder.buildButton("Clone Job", smallHeight, ARConstants.ICON_SAVE, smallIconSize, smallPadding);
        configureButton =
                builder.buildButton("Config", smallHeight, ARConstants.ICON_CONFIG, smallIconSize, smallPadding);
        infoButton = builder.buildButton("Info", smallHeight, ARConstants.ICON_INFO, smallIconSize, smallPadding);
        openBotJobButton =
                builder.buildButton("Open Job", smallHeight, ARConstants.ICON_EDIT, smallIconSize, smallPadding);
        launchBotJobButton =
                builder.buildButton("Launch", smallHeight, ARConstants.ICON_PLAY, smallIconSize, smallPadding);

        launchBotJobButton.setDisable(true);
        launchBotJobButton.setTooltip(new Tooltip("Mobile Bot Jobs can only be executed from AR Mobile"));

        exitButton = builder.buildButton("Exit", smallHeight, ARConstants.ICON_CROSS, smallIconSize, smallPadding);

        // 🔹 AI Button and TextArea
        aiButton = builder.buildButton(
                "AI", ARConstants.SPACE_L, ARConstants.ICON_AI, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        aiButton.setVisible(false);

        aiTextArea = new TextArea();
        aiTextArea.setPromptText("AI Tool: Upgrade your version to access this premium feature.");
        aiTextArea.setEditable(false);
        aiTextArea.setWrapText(true);
        aiTextArea.setVisible(false);
        aiTextArea.setManaged(false); // ensures space is not reserved when hidden
        aiTextArea.setPrefRowCount(4);

        newBotJobButton.setStyle(smallFont);
        cloneBotJobButton.setStyle(smallFont);
        configureButton.setStyle(smallFont);
        infoButton.setStyle(smallFont);
        launchBotJobButton.setStyle(smallFont);
        openBotJobButton.setStyle(smallFont);
        exitButton.setStyle(smallFont);

        int buttonWidth = 112;

        aiButton.setPrefWidth(buttonWidth);
        organizationsButton.setPrefWidth(buttonWidth);
        newBotJobButton.setPrefWidth(buttonWidth);
        cloneBotJobButton.setPrefWidth(buttonWidth);
        configureButton.setPrefWidth(buttonWidth);
        infoButton.setPrefWidth(buttonWidth);
        launchBotJobButton.setPrefWidth(buttonWidth);
        openBotJobButton.setPrefWidth(buttonWidth);
        exitButton.setPrefWidth(buttonWidth);

        buttonPane = new HBox(
                //                aiButton,
                organizationsButton,
                newBotJobButton,
                cloneBotJobButton,
                configureButton,
                infoButton,
                launchBotJobButton,
                openBotJobButton,
                exitButton);

        buttonPane.setAlignment(Pos.CENTER);
        buttonPane.setSpacing(5);
        buttonPane.setPadding(new Insets(0, 5, 0, 5));

        AnchorPane.setTopAnchor(buttonPane, ARConstants.SPACE_ZERO);
        //        AnchorPane.setLeftAnchor(buttonPane, 5.0);
        //        AnchorPane.setRightAnchor(buttonPane, 5.0);

        initHeader();

        viewBotJobListView.setItems(botJobList);
        viewBotJobListView.setCellFactory(
                new ARCellFactory<>(
                        BotJobListCell.class,
                        arWebDriver,
                        botJobList,
                        webDriverList,
                        isEnabledLicence)::call);

        arConfigurationScene.initialize(viewBotJobListView, isEnabledLicence);
        arNewBotJobScene.initialize(arWebDriver, webDriverList, isEnabledLicence);
        arWebDriver.initialize(webDriverList);

        // 🔹 Wrap buttonPane + aiTextArea
        VBox topSection = new VBox(buttonPane, aiTextArea);
        VBox.setMargin(aiTextArea, new Insets(5, 10, 5, 10));

        // 🔹 Panel with everything
        panelPane = new VBox(topSection, header, viewBotJobListView);
        VBox.setMargin(viewBotJobListView, new Insets(0, 10D, 10D, 10D));
        VBox.setVgrow(viewBotJobListView, Priority.ALWAYS);
        HBox.setHgrow(viewBotJobListView, Priority.ALWAYS);

        AnchorPane.setTopAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(panelPane, ARConstants.SPACE_ZERO);
    }

    @Override
    public void initUIBehaviour() {
        viewBotJobListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection == null) {
                launchBotJobButton.setDisable(true);
                return;
            }

            boolean isMobile = (!newSelection.getPriority().equalsIgnoreCase("Web App")
                    && !newSelection.getPriority().equalsIgnoreCase("Rest Api"));

            launchBotJobButton.setDisable(isMobile);
        });

        aiButton.setOnAction(e -> {
            boolean visible = aiTextArea.isVisible();
            aiTextArea.setVisible(!visible);
            aiTextArea.setManaged(!visible);
        });

        organizationsButton.setOnMouseClicked(e -> {
            ErrorMessage errorMessage = performDataBase.loadAllDataUsers();
            if (errorMessage == null) {
                errorMessage = performDBEngine.loadHomeBanking(null);
            }
            if (errorMessage == null) {
                errorMessage = performDBEngine.loadHomeUrls(null);
            }
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
                return;
            }

            Stage currentStage = (Stage) organizationsButton.getScene().getWindow();
            arOrganizationManagerScene.showModal(currentStage);
        });

        newBotJobButton.setOnMouseClicked(e -> {
            if (performLists.getListHomeUrl().isEmpty()) {
                performDBEngine.loadHomeUrls(null);
            }

            if (!performLists.getListHomeUrl().isEmpty()) {

                arNewBotJobScene.initialize(arWebDriver, webDriverList, isEnabledLicence);

                Stage currentStage = (Stage) cloneBotJobButton.getScene().getWindow();
                arNewBotJobScene.showModal(currentStage);

                refreshBotJob();
            } else {
                performMessage.showCustomModalDialogDragWin11(
                        "Environments Are Empty",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Please add at least one Organization Environment.</span>",
                        "<span style='font-style: italic;'>Go to the Configuration and add Organizations.</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Select an Organization and add an </span><span style='font-weight: bold;'>Environment</span>.",
                        null,
                        false,
                        "OK",
                        null,
                        0);
            }
        });

        cloneBotJobButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !com.allinweb.ch.facade.LicenseService.getInstance().isActive()) {
                return;
            }

            String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

            try {
                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.error(dataBaseType + " Database connected!");
                } else {
                    log.error(dataBaseType + " Database NOT connected!");
                }
            } catch (Exception error) {
                log.error(dataBaseType + " Database error!" + error.getMessage());
            }

            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
            if (selecBotJobDTO != null) {
                if (performDataBase.isConnDBWorks()) {
                    ARMainDashboardPane.getInstance().openCloneBotJob(selecBotJobDTO);
                }
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                return;
            }
        });

        configureButton.setOnMouseClicked(e -> {
            arConfigurationScene.initialize(viewBotJobListView, isEnabledLicence);
            arConfigurationScene.showModal();

            //            try {
            //                performDataBase.changeDbConnection();
            //            } catch (Exception error) {
            //                throw new RuntimeException(error);
            //            }

            String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
            try {

                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.info(dataBaseType + " Database connected!");
                }
            } catch (Exception error) {
                log.error(dataBaseType + " Database Connection failed : " + error.getMessage());
            }

            if (performDataBase.isConnDBWorks()) {
                try {
                    if (performLists.getQuickBotJobs().isEmpty()) {
                        performDataBase.loadQuickBotJobs();
                        //                    ObservableList<BotJobLoadDTO> botJobList =
                        //                            FXCollections.observableArrayList(performLists.getQuickBotJobs());

                        viewBotJobListView.setItems(FXCollections.observableArrayList(performLists.getQuickBotJobs()));
                    }
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }
        });
        infoButton.setOnMouseClicked(e -> {
            ARMainDashboardPane.getInstance().openInfo();
        });
        exitButton.setOnMouseClicked(e -> {
            //            Platform.exit();
            closeWebDrivers();
        });

        openBotJobButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !com.allinweb.ch.facade.LicenseService.getInstance().isActive()) {
                return;
            }

            selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();

            if (selecBotJobDTO != null) {
                try {

                    reloadList();

                    Platform.runLater(() -> {
                        ARMainDashboardPane.getInstance().openBotJob(selecBotJobDTO);

                        // new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName()).show();
                    });

                } catch (Exception e2) {
                    new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName() + "  " + e2.getMessage())
                            .show(); // TODO: handle exception
                }
                // new ARMoveBlockScene(selecBotJobDTO.getBlocks().get(0));
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                return;
            }
        });

        launchBotJobButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !com.allinweb.ch.facade.LicenseService.getInstance().isActive()) {
                return;
            }

            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
            if (selecBotJobDTO != null) {

                if (!selecBotJobDTO.getPriority().equalsIgnoreCase("Web App")
                        && !selecBotJobDTO.getPriority().equalsIgnoreCase("Rest Api")) {
                    performMessage.errorMessage(
                            "Mobile Bot Job Selected",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Mobile Bot Jobs can only be executed from AR Mobile!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>Please run \"AR Mobile\" to launch the Bot Job tests.</span>",
                            null,
                            null,
                            0);
                    return;
                }

                String enginePath =
                        arPropertyManager.getProperty(ARPropertyEnum.PATH_ENGINE); // + "\\AR_Web_Engine.jar";
                String excelPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
                excelPath = excelPath + "\\" + selecBotJobDTO.getName() + ".xlsx";
                if (!new File(excelPath).exists()) {
                    performMessage.errorMessage(
                            "Action Required: Prepare Excel Data",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Crucial Step: Prepare Excel Data Before Launch!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>To successfully initiate the bot job, the Excel data file must be generated and compiled *first*.</span>",
                            "<span style='font-style: italic;'>Ensure this preparation is complete before attempting to launch the automation process.</span>",
                            null,
                            0);

                    return;
                }

                String version = System.getProperty("java.version");
                log.info("Detected Java Version: " + version);

                int majorVersion = getMajorJavaVersion(version);
                if (majorVersion >= 17) {
                    log.info("✅ Java 17 or higher is installed.");
                } else {
                    performMessage.errorMessage(
                            "Compatibility Issue: Incompatible Java Version",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: Your Java version is lower than the required 17!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>Attempting to execute the Engine with this older version may lead to unexpected behavior or failures.</span>",
                            "<span style='font-style: italic;'>Please upgrade your Java installation to version 17 or higher for optimal performance and stability.</span>",
                            null,
                            0);
                }
                String webDriverPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_WEBDRIVER);
                if (!(new File(webDriverPath)).exists()) {
                    performMessage.errorMessage(
                            "Action Required: Missing WebDriver",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: The WebDriver file is missing!</span>",
                            "<span style='color: #2E7D32; font-weight: bold;'>To execute automated browser interactions, the WebDriver is absolutely essential.</span>",
                            "<span style='font-style: italic;'>Please download the correct WebDriver for your browser and ensure it is accessible by the application.</span>",
                            null,
                            0);
                    return;
                }

                //    ".\\java\\bin\\java.exe",
                String[] command = new String[] {
                    "cmd.exe",
                    "/c",
                    "java.exe",
                    "-jar",
                    "\"" + enginePath + "\"",
                    "execute/j",
                    String.valueOf(
                            selecBotJobDTO.getHomeBankingLoadDTO() != null
                                    ? selecBotJobDTO.getHomeBankingLoadDTO().getId()
                                    : selecBotJobDTO.getHomeBankingId()),
                    String.valueOf(selecBotJobDTO.getId()),
                    String.valueOf(1), // block execution
                    "\"" + excelPath + "\"",
                    "-c",
                    arPropertyManager.getConfigurationFileName()
                };
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(new File(ARConstants.USER_PATH));
                String logPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG);
                File output = new File(logPath + "\\engine_debug_log_output.log");
                File error = new File(logPath + "\\engine_debug_log_error.log");
                File input = new File(logPath + "\\engine_debug_log_input.log");
                List<File> files = new ArrayList<>();
                files.add(output);
                files.add(error);
                files.add(input);
                for (File file : files) {
                    if (!file.exists()) {
                        try {
                            file.createNewFile();
                        } catch (IOException ex) {
                            log.info("Error : " + ex);
                        }
                    }
                }
                processBuilder.redirectOutput(output);
                processBuilder.redirectError(error);
                processBuilder.redirectInput(input);
                try {
                    processBuilder.start();
                } catch (IOException ex) {
                    log.info("Error : " + ex);
                }
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
            }
        });
    }

    // Method to close all WebDriver instances
    private void closeWebDrivers() {
        for (WebDriver driver : arWebDriver.getWebDriverList()) {
            try {
                Platform.runLater(() -> arWebDriver.getWebDriverList().remove(driver));
                Platform.runLater(driver::quit);
                log.info("WebDriver closed.");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: " + e.getMessage());
            }
        }
        Platform.runLater(() -> {
            arWebDriver.getWebDriverList().clear();
            System.exit(0);
        });
    }

    private void initHeader() {
        header.setMaxHeight(ARConstants.SPACE_M);

        // Create an HBox for the header and set spacing
        HBox headerHBox = new HBox(10);
        headerHBox.setPadding(new Insets(5D, 10D, 5D, 20D)); // Added padding to the left (20D)

        // Column widths — these MUST match the per-row widths in
        // BotJobListCell.java so the header aligns with every cell below.
        final double W_NAME = 220;
        final double W_DESCRIPTION = 320;
        final double W_ORGANIZATION = 140;
        final double W_STATUS = 70;
        final double W_ACTIONS = 40;

        // Create labels for each column header
        Label nameLabel = new Label("Name");
        nameLabel.setMinWidth(W_NAME);
        nameLabel.setMaxWidth(W_NAME);
        nameLabel.setWrapText(true);

        Label descriptionLabel = new Label("Description");
        descriptionLabel.setMinWidth(W_DESCRIPTION);
        descriptionLabel.setMaxWidth(W_DESCRIPTION);
        descriptionLabel.setWrapText(true);

        Label environmentLabel = new Label("Organization");
        environmentLabel.setMinWidth(W_ORGANIZATION);
        environmentLabel.setMaxWidth(W_ORGANIZATION);
        environmentLabel.setWrapText(true);

        Label statusLabel = new Label("Status");
        statusLabel.setMinWidth(W_STATUS);
        statusLabel.setMaxWidth(W_STATUS);
        statusLabel.setWrapText(true);

        Label actionsLabel = new Label("Actions");
        actionsLabel.setMinWidth(W_ACTIONS);
        actionsLabel.setMaxWidth(W_ACTIONS);
        actionsLabel.setWrapText(true);

        // Create spacers
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        Region spacerStatus = new Region();
        spacerStatus.setMinWidth(15);
        HBox.setHgrow(spacerStatus, Priority.ALWAYS);
        Region spacerAction = new Region();
        spacerAction.setMinWidth(5);
        HBox.setHgrow(spacerAction, Priority.ALWAYS);
        Region spacer5 = new Region();
        HBox.setHgrow(spacer5, Priority.ALWAYS);

        // Add labels and spacers to the HBox
        headerHBox
                .getChildren()
                .addAll(
                        nameLabel, spacer1,
                        descriptionLabel, spacer2,
                        environmentLabel, spacerStatus,
                        statusLabel, spacerAction,
                        actionsLabel, spacer5);

        // Set HBox as the header
        header.getChildren().clear(); // Clear any existing children (if any)
        header.getChildren().add(headerHBox); // Add the new header with labels and spacers

        // Set margins for the header
        VBox.setMargin(headerHBox, new Insets(5D, 10D, 5D, 10D));
    }

    public void setProperty(String propertyName, String value) {
        this.properties.setProperty(propertyName, value);
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(panelPane);
    }

    private boolean checkLicense() {
        try {
            String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
            if (Strings.isNullOrEmpty(licensePath)) {
                licensePath = System.getProperty("user.dir");
            }

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
        } catch (Exception error) {

            log.error("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }

    private void reloadList() {

        // IN CASE the ADDED New Bot Job tomREfresh Main List as Observable
        if (performLists.getListHomeUrl().isEmpty()) {
            performDBEngine.loadHomeUrls(null);
        }

        if (performLists.getQuickBotJobs().isEmpty()) {
            performDataBase.loadQuickBotJobs();
        }

        ErrorMessage errorMessage =
                performDataBase.loadBlocks(selecBotJobDTO.getId(), selecBotJobDTO.getName(), "block");
        if (errorMessage == null) {
            performDataBase.loadBlocks(selecBotJobDTO.getHomeBankingId(), selecBotJobDTO.getName(), "component_block");
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        //        this.botLoadJobs = performDataBase.loadBotJobWithBlock(this.botJobId);

        BotJobLoadDTO botJobLoad = performLists.getQuickBotJobById(selecBotJobDTO.getId());

        //         performDBEngine.loadHomeBanking(selecBotJobDTO.getHomeBankingId());

        if (botJobLoad != null && botJobLoad.getBlockLoadDTOList() == null) {
            botJobLoad.setBlockLoadDTOList(performLists.getListBlock());
        }
        // It Prevents Start without blocks
        if (performLists.getListBlock().isEmpty()) {

            errorMessage = performDataBase.initiateNewBlock(
                    "block", selecBotJobDTO.getId(), "Default Block", "Default Block", 1, false);

            if (errorMessage == null) {

                log.info(String.format("A new Block was created for bot job Id %d", selecBotJobDTO.getId()));
            } else {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }
    }

    public void refreshBotJob() {
        botJobList.clear();
        performDataBase.loadQuickBotJobs();
        botJobList.addAll(performLists.getQuickBotJobs());

        viewBotJobListView.setItems(botJobList);
    }
}
