package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARConfigurationScene;
import com.allinweb.ch.component.scene.ARInfoScene;
import com.allinweb.ch.component.scene.ARNewBotJobScene;
import com.allinweb.ch.component.scene.ARSaveCloneScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.*;
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
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import lombok.Getter;
import org.openqa.selenium.WebDriver;

public class ARMainPane extends ARPane {

    protected static volatile ARMainPane instance;

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

    public void initialize(ObservableList<WebDriver> webDriverList, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.webDriverList = webDriverList;

        botJobList.clear();
        ErrorMessage errorMessage = performDataBase.loadQuickBotJobs();
        if (errorMessage != null) {
            performMessage.errorMessage(
                    errorMessage.getErrorTitle(),
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                    "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                            + errorMessage.getErrorHeader(),
                    "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                    null,
                    0);
        }
        botJobList.addAll(performLists.getQuickBotJobs());
    }

    private boolean isEnabledLicence;

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    //    private static final ARSharedResources dbResource;
    private static final ARInfoScene arInfoScene;
    private static final ARPropertyManager arPropertyManager;
    private static final PerformLists performLists;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    private static final ARSaveCloneScene arSaveCloneScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    private static final ARWebDriver arWebDriver;

    private ObservableList<BotJobLoadDTO> botJobList = FXCollections.observableArrayList();

    @Getter
    private ObservableList<WebDriver> webDriverList;

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed

    // Static block to initialize
    static {
        arInfoScene = ARInfoScene.getInstance();
        //        //        dbResource = PerformDataBase.;
        arPropertyManager = ARPropertyManager.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();
        performLists = PerformLists.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
        arViewBotJobScene = ARViewBotJobScene.getInstance();
        arSaveCloneScene = ARSaveCloneScene.getInstance();
        arWebDriver = ARWebDriver.getInstance();
    }

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private Properties properties = new Properties();

    // UI components
    Button newBotJobButton;
    Button cloneBotJobButton;
    // Button viewBotJobButton;
    Button configureButton;
    Button infoButton;
    Button editBotJobButton;
    Button launchBotJobButton;
    Button exitButton;
    Button aiButton;

    TextArea aiTextArea;

    HBox buttonPane;
    VBox panelPane;

    GridPane header = new GridPane();

    ListView<BotJobLoadDTO> viewBotJobListView = new ListView<>();

    private BotJobLoadDTO selecBotJobDTO;

    @Override
    public void initUIComponents() {
        double smallHeight = ARConstants.SPACE_S; // or just a smaller double like 20.0
        double smallIconSize = ARConstants.SPACE_S; // smaller icon size
        Insets smallPadding = new Insets(4, 6, 4, 6);

        newBotJobButton = builder.buildButton("New", smallHeight, ARConstants.ICON_NEW, smallIconSize, smallPadding);
        cloneBotJobButton =
                builder.buildButton("Clone Job", smallHeight, ARConstants.ICON_SAVE, smallIconSize, smallPadding);
        configureButton =
                builder.buildButton("Config", smallHeight, ARConstants.ICON_CONFIG, smallIconSize, smallPadding);
        infoButton = builder.buildButton("Info", smallHeight, ARConstants.ICON_INFO, smallIconSize, smallPadding);
        editBotJobButton =
                builder.buildButton("Open Job", smallHeight, ARConstants.ICON_EDIT, smallIconSize, smallPadding);
        launchBotJobButton =
                builder.buildButton("Launch", smallHeight, ARConstants.ICON_PLAY, smallIconSize, smallPadding);
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

        int buttonWidth = 100;

        aiButton.setPrefWidth(buttonWidth);
        newBotJobButton.setPrefWidth(buttonWidth);
        cloneBotJobButton.setPrefWidth(buttonWidth);
        configureButton.setPrefWidth(buttonWidth);
        infoButton.setPrefWidth(buttonWidth);
        launchBotJobButton.setPrefWidth(buttonWidth);
        editBotJobButton.setPrefWidth(buttonWidth);
        exitButton.setPrefWidth(buttonWidth);

        buttonPane = new HBox(
                //                aiButton,
                newBotJobButton,
                cloneBotJobButton,
                configureButton,
                infoButton,
                launchBotJobButton,
                editBotJobButton,
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
                        arViewBotJobScene,
                        arWebDriver,
                        botJobList,
                        webDriverList,
                        isEnabledLicence)::call);

        arConfigurationScene.initialize(viewBotJobListView, isEnabledLicence);
        arNewBotJobScene.initialize(arViewBotJobScene, arWebDriver, webDriverList, isEnabledLicence);
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
        aiButton.setOnAction(e -> {
            boolean visible = aiTextArea.isVisible();
            aiTextArea.setVisible(!visible);
            aiTextArea.setManaged(!visible);
        });

        newBotJobButton.setOnMouseClicked(e -> {
            if (performLists.getListHomeUrl().isEmpty()) {
                performDataBase.loadHomeUrls(null);
            }

            if (!performLists.getListHomeUrl().isEmpty()) {

                arNewBotJobScene.initialize(arViewBotJobScene, arWebDriver, webDriverList, isEnabledLicence);

                Stage currentStage = (Stage) cloneBotJobButton.getScene().getWindow();
                arNewBotJobScene.showModal(currentStage);

                botJobList.clear();
                performDataBase.loadQuickBotJobs();
                botJobList.addAll(performLists.getQuickBotJobs());

                viewBotJobListView.setItems(botJobList);
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
            if (isEnabledLicence && !checkLicense()) {
                return;
            }

            String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

            try {
                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    ARLogger.getInstance(ARMainPane.class).severe(dataBaseType + " Database connected!");
                } else {
                    ARLogger.getInstance(ARMainPane.class).severe(dataBaseType + " Database NOT connected!");
                }
            } catch (Exception error) {
                ARLogger.getInstance(ARMainPane.class).severe(dataBaseType + " Database error!" + error.getMessage());
            }

            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
            if (selecBotJobDTO != null) {
                if (performDataBase.isConnDBWorks()) {
                    arSaveCloneScene.initialize(selecBotJobDTO, botJobList, isEnabledLicence);

                    Stage currentStage = (Stage) cloneBotJobButton.getScene().getWindow();
                    arSaveCloneScene.showModal(currentStage);

                    performDataBase.loadQuickBotJobs();
                    ObservableList<BotJobLoadDTO> botJobList =
                            FXCollections.observableArrayList(performLists.getQuickBotJobs());
                    viewBotJobListView.setItems(botJobList);
                    viewBotJobListView.refresh();
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
                    ARLogger.getInstance(ARMainPane.class).info(dataBaseType + " Database connected!");
                }
            } catch (Exception error) {
                ARLogger.getInstance(ARMainPane.class)
                        .severe(dataBaseType + " Database Connection failed : " + error.getMessage());
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
            arInfoScene.showModal();
        });
        exitButton.setOnMouseClicked(e -> {
            //            Platform.exit();
            closeWebDrivers();
        });

        editBotJobButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }

            selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();

            if (selecBotJobDTO != null) {
                try {

                    reloadList();

                    Platform.runLater(() -> {
                        // new ARViewBotJobScene(selecBotJobDTO).showModal();
                        arViewBotJobScene.initialize(arWebDriver, selecBotJobDTO, isEnabledLicence);
                        arViewBotJobScene.showModal();

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
            if (isEnabledLicence && !checkLicense()) {
                return;
            }

            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
            if (selecBotJobDTO != null) {
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
                System.out.println("Detected Java Version: " + version);

                int majorVersion = getMajorJavaVersion(version);
                if (majorVersion >= 17) {
                    System.out.println("✅ Java 17 or higher is installed.");
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
                    String.valueOf(selecBotJobDTO.getHomeBankingLoadDTO().getId()),
                    String.valueOf(selecBotJobDTO.getId()),
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
                            ARLogger.getInstance(ARMainPane.class).fine("Error : " + ex);
                        }
                    }
                }
                processBuilder.redirectOutput(output);
                processBuilder.redirectError(error);
                processBuilder.redirectInput(input);
                try {
                    processBuilder.start();
                } catch (IOException ex) {
                    ARLogger.getInstance(ARMainPane.class).fine("Error : " + ex);
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
                ARLogger.getInstance(ARMainPane.class).info("WebDriver closed.");
            } catch (Exception e) {
                ARLogger.getInstance(ARMainPane.class).warning("Error closing WebDriver: " + e.getMessage());
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

        // Create labels for each column header
        Label nameLabel = new Label("Name");
        nameLabel.setMinWidth(150); // Set minimum width
        nameLabel.setMaxWidth(150); // Set maximum width
        nameLabel.setWrapText(true);

        Label descriptionLabel = new Label("Description");
        descriptionLabel.setMinWidth(150); // Set minimum width
        descriptionLabel.setMaxWidth(150); // Set maximum width
        descriptionLabel.setWrapText(true);

        Label environmentLabel = new Label("Organization");
        environmentLabel.setMinWidth(100); // Set minimum width
        environmentLabel.setMaxWidth(100); // Set maximum width
        environmentLabel.setWrapText(true);

        Label statusLabel = new Label("Status");
        statusLabel.setMinWidth(50); // Set minimum width
        statusLabel.setMaxWidth(50); // Set maximum width
        statusLabel.setWrapText(true);

        Label actionsLabel = new Label("Actions");
        actionsLabel.setMinWidth(50); // Set minimum width
        actionsLabel.setMaxWidth(50); // Set maximum width
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
            ARLogger.getInstance(ARMainPane.class)
                    .severe("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }

    private void reloadList() {

        // IN CASE the ADDED New Bot Job tomREfresh Main List as Observable
        if (performLists.getListHomeUrl().isEmpty()) {
            performDataBase.loadHomeUrls(null);
        }

        if (performLists.getQuickBotJobs().isEmpty()) {
            performDataBase.loadQuickBotJobs();
        }

        performDataBase.loadBlocks(selecBotJobDTO.getId(), selecBotJobDTO.getName(), "block");
        performDataBase.loadBlocks(selecBotJobDTO.getHomeBankingId(), selecBotJobDTO.getName(), "component_block");
        //        this.botLoadJobs = performDataBase.loadBotJobWithBlock(this.botJobId);

        BotJobLoadDTO botJobLoad = performLists.getQuickBotJobById(selecBotJobDTO.getId());

        //        performDataBase.loadHomeBanking(selecBotJobDTO.getHomeBankingId());

        if (botJobLoad != null && botJobLoad.getBlockLoadDTOList() == null) {
            botJobLoad.setBlockLoadDTOList(performLists.getListBlock());
        }
        // It Prevents Start without blocks
        if (performLists.getListBlock().isEmpty()) {

            ErrorMessage errorMessage = performDataBase.initiateNewBlock(
                    "block", selecBotJobDTO.getId(), "Default Block", "Default Block", 1, false);

            if (errorMessage == null) {
                ARLogger.getInstance(Thread.class)
                        .info(String.format("A new Block was created for bot job Id %d", selecBotJobDTO.getId()));
            } else {
                performMessage.errorMessage(
                        errorMessage.getErrorTitle(),
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                + errorMessage.getErrorTitle(),
                        "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                        null,
                        0);
            }
        }
    }
}
