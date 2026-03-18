package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARNewBotJobPane extends ARPane {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    protected static volatile ARNewBotJobPane instance;
    private Label labelBotJobName;
    private Label descriptionLabel;
    private Label labelHomeBanking;
    private TextField botJobName;
    private TextField botJobDescription;
    private Button createBotJobButton;
    private Button refreshEnvsButton;
    private Button insertSitesdButton;
    private ChoiceBox<HomeUrlDTO> homeURLChoiceBox;

    private ToggleGroup appTypeGroup;
    private RadioButton rbWeb;
    private RadioButton rbAndroid;
    private RadioButton rbIos;
    private RadioButton rbRestAppi;
    private HBox groupOptions;

    private Pane mainPane;
    private boolean isEnabledLicence;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;

    private ARNewBotJobPane() {
        super();
    }

    public static ARNewBotJobPane getInstance() {
        if (instance == null) {
            synchronized (ARNewBotJobPane.class) {
                if (instance == null) {
                    instance = new ARNewBotJobPane();
                }
            }
        }
        return instance;
    }

    // You mentioned this is the button creator, adapted here
    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_REFRESH, ARConstants.SPACE_M, new Insets(3D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        String labelStyle = "-fx-text-fill: blue; -fx-font-weight: bold; -fx-font-size: 14;";

        // Top description label for the pane
        Label paneTitleLabel = new Label("Create New Bot Job");
        paneTitleLabel.setStyle("-fx-text-fill: blue; -fx-font-weight: bold; -fx-font-size: 16;");
        paneTitleLabel.setMaxWidth(Double.MAX_VALUE);
        paneTitleLabel.setAlignment(Pos.CENTER);

        labelBotJobName = new Label("Name:");
        labelBotJobName.setStyle(labelStyle);

        botJobName = new TextField();
        botJobName.setPromptText("Enter Bot Job Name");

        descriptionLabel = new Label("Description:");
        descriptionLabel.setStyle(labelStyle);

        botJobDescription = new TextField();
        botJobDescription.setPromptText("Enter Description (optional)");

        labelHomeBanking = new Label("Select the URL / Environment:");
        labelHomeBanking.setStyle(
                "-fx-font-size: 1.2em; -fx-font-weight: bold; -fx-text-fill: #1565C0; -fx-padding: 0 0 10 0;");
        labelHomeBanking.setMaxWidth(Double.MAX_VALUE);
        labelHomeBanking.setAlignment(Pos.CENTER);

        // ChoiceBox + Button
        homeURLChoiceBox = new ChoiceBox<>();
        // Remove fixed width
        // homeURLChoiceBox.setPrefWidth(300);
        // homeURLChoiceBox.setMaxWidth(300);
        // homeURLChoiceBox.setMinWidth(300);

        // Apply CSS style for font size, padding, background, and text color
        homeURLChoiceBox.setStyle("-fx-font-size: 1.1em;" + "-fx-padding: 4 8 4 8;"
                + "-fx-background-radius: 5;"
                + "-fx-border-radius: 5;"
                + "-fx-text-fill: white;");

        //        homeURLChoiceBox.setDefaultButton(true);

        populateHomeUrlChoiceBox();
        Tooltip tooltip = new Tooltip("Select the target URL / environment for the Bot Job");
        homeURLChoiceBox.setTooltip(tooltip);

        refreshEnvsButton = createPathButton();

        // Side-by-side: ChoiceBox and refresh button
        HBox choiceAndRefreshBox = new HBox(5, homeURLChoiceBox, refreshEnvsButton);
        choiceAndRefreshBox.setAlignment(Pos.CENTER); // Center horizontally
        choiceAndRefreshBox.setPadding(new Insets(0, 0, 10, 0));

        // Allow ChoiceBox to grow horizontally
        HBox.setHgrow(homeURLChoiceBox, Priority.ALWAYS);
        homeURLChoiceBox.setMaxWidth(Double.MAX_VALUE);

        createBotJobButton = new Button("Create Bot Job");
        createBotJobButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        insertSitesdButton = new Button("Orgs / Environments");
        insertSitesdButton.setDefaultButton(true);
        HBox.setMargin(insertSitesdButton, new Insets(0, 0, 0, 50)); // 50px gap to the left

        HBox buttonsBox = new HBox(createBotJobButton, insertSitesdButton);
        buttonsBox.setAlignment(Pos.CENTER);

        labelBotJobName.setLabelFor(botJobName);
        descriptionLabel.setLabelFor(botJobDescription);
        labelHomeBanking.setLabelFor(homeURLChoiceBox);

        // URL details container (everything related to URL + buttons)
        VBox homeUrlDetailsContainer = new VBox(10);
        homeUrlDetailsContainer.setPadding(new Insets(10, 10, 10, 10));
        homeUrlDetailsContainer.setStyle(
                "-fx-background-color: #E8F5E9; -fx-border-color: #ccc; -fx-border-width: 1px; -fx-border-radius: 5px;");
        homeUrlDetailsContainer.getChildren().addAll(labelHomeBanking, choiceAndRefreshBox, buttonsBox);

        // App type options
        appTypeGroup = new ToggleGroup();

        rbWeb = new RadioButton("Web Apps");
        rbWeb.setToggleGroup(appTypeGroup);
        rbWeb.setSelected(true); // default

        rbAndroid = new RadioButton("Android Apps");
        rbAndroid.setToggleGroup(appTypeGroup);

        rbIos = new RadioButton("iOS Apps");
        rbIos.setToggleGroup(appTypeGroup);

        rbRestAppi = new RadioButton("Rest Api");
        rbRestAppi.setToggleGroup(appTypeGroup);

        // Optional styling
        rbWeb.setStyle("-fx-font-size: 13;");
        rbAndroid.setStyle("-fx-font-size: 13;");
        rbIos.setStyle("-fx-font-size: 13;");
        rbRestAppi.setStyle("-fx-font-size: 13;");

        groupOptions = new HBox(15, rbWeb, rbAndroid, rbIos, rbRestAppi);
        //        groupOptions = new HBox(15, rbWeb);
        groupOptions.setAlignment(Pos.CENTER);
        groupOptions.setPadding(new Insets(0, 0, 10, 0));

        VBox mainLayout = new VBox(
                12,
                paneTitleLabel,
                groupOptions,
                labelBotJobName,
                botJobName,
                descriptionLabel,
                botJobDescription,
                homeUrlDetailsContainer);
        mainLayout.setPadding(new Insets(15));
        mainLayout.setFillWidth(true);

        AnchorPane.setTopAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(mainLayout, ARConstants.SPACE_M);

        mainPane = new AnchorPane(mainLayout);
    }

    private void populateHomeUrlChoiceBox() {
        // Clear old items
        homeURLChoiceBox.getItems().clear();

        // Add "Select Environment" first
        HomeUrlDTO selectEnv = new HomeUrlDTO(-2, null, -2, "Select the Environment");
        homeURLChoiceBox.getItems().add(selectEnv);

        // Add all real environments from performLists
        homeURLChoiceBox.getItems().addAll(performLists.getListHomeUrl());

        // If list is empty (no real envs), add "No Environment Defined"
        if (performLists.getListHomeUrl().isEmpty()) {
            HomeUrlDTO noEnv = new HomeUrlDTO(-1, null, -1, "No Environment Defined");
            homeURLChoiceBox.getItems().add(noEnv);
            homeURLChoiceBox.setDisable(true);
        } else {
            homeURLChoiceBox.setDisable(false);
        }

        // Select first item ("Select Environment")
        homeURLChoiceBox.getSelectionModel().selectFirst();
    }

    @Override
    public void initUIBehaviour() {
        insertSitesdButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);
            Stage currentStage = (Stage) insertSitesdButton.getScene().getWindow();
            arNewHomeBankingScene.showModal(currentStage);
            // If homeURLChoiceBox was initialized, refresh its items
            if (homeURLChoiceBox != null) {
                populateHomeUrlChoiceBox();
            }
        });

        homeURLChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(HomeUrlDTO object) {
                if (object != null) {
                    if (object.getUrl() != null) {
                        return object.getOrgName() + " | " + object.getUrl();
                    } else {
                        return object.getOrgName();
                    }
                }
                return "";
            }

            @Override
            public HomeUrlDTO fromString(String string) {
                return null;
            }
        });

        createBotJobButton.setOnAction(e -> launchBotJobCreation());

        refreshEnvsButton.setOnAction(e -> {
            // Reload from performLists after reloading from DB
            performDBEngine.loadHomeUrls(null);

            // If homeURLChoiceBox was initialized, refresh its items
            if (homeURLChoiceBox != null) {
                populateHomeUrlChoiceBox();
            }
        });
    }

    // Initialize references for Scene and WebDriver
    public void initialize(ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;

        ErrorMessage errorMessage = performDBEngine.loadHomeBanking(null);
        if (errorMessage == null) {
            errorMessage = performDBEngine.loadHomeUrls(null);
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }
    }

    private void launchBotJobCreation() {
        Task<Void> botJobCreationTask = new Task<>() {
            @Override
            protected Void call() {
                createBotJob();
                return null;
            }
        };
        new Thread(botJobCreationTask).start();
    }

    private void createBotJob() {
        Platform.runLater(() -> {
            // Resolve selected app type
            String appType = getSelectedAppType(); // "Web", "Android", or "iOS"

            // Normalize/prepare name with required prefix for Android/iOS
            String rawName =
                    botJobName.getText() == null ? "" : botJobName.getText().trim();
            String projectType = "Web App";
            if ("Android".equalsIgnoreCase(appType)) projectType = "Android";
            else if ("iOS".equalsIgnoreCase(appType)) projectType = "iOS";
            else if ("Rest Api".equalsIgnoreCase(appType)) projectType = "Rest Api";

            // Validation: simply check if rawName is empty or null
            boolean isMeaningfulEmpty = Strings.isNullOrEmpty(rawName);

            if (isMeaningfulEmpty) {
                performMessage.errorMessage(
                        "Missing Bot Job Name",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Bot Job Name cannot be empty.</span>",
                        "<span style='color: #000080; font-weight: bold;'>Please enter a name for the Bot Job to proceed.</span>",
                        null,
                        null,
                        0);
                return;
            }

            // From here on, use finalName instead of botJobName.getText().trim()

            if (Strings.isNullOrEmpty(rawName)) {
                performMessage.errorMessage(
                        "Missing Bot Job Name",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Bot Job Name cannot be empty.</span>",
                        "<span style='color: #000080; font-weight: bold;'>Please enter a name for the Bot Job to proceed.</span>",
                        null,
                        null,
                        0);
                return;
            }

            String finalRawName = rawName;
            boolean existName = performLists.getListBotJob().stream()
                    .anyMatch(f -> f.getName().equalsIgnoreCase(finalRawName));

            if (existName) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The name you have entered is already in use.</span>",
                        "<span style='color: #000080; font-weight: bold;'>" + rawName + "</span>",
                        null,
                        null,
                        0);

                return;
            }

            if (homeURLChoiceBox.getValue() == null
                    || Strings.isNullOrEmpty(homeURLChoiceBox.getValue().getOrgName())
                    || homeURLChoiceBox.getValue().getId() < 0 // -1 or -2 means not valid selection
                    || homeURLChoiceBox.getValue().getId() == -2) {
                performMessage.errorMessage(
                        "Missing Website",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Website cannot be empty or undefined.</span>",
                        "<span style='color: #000080; font-weight: bold;'>Please select a valid Website for the Bot Job to proceed.</span>",
                        null,
                        null,
                        0);
                return;
            }

            HomeUrlDTO homeUrlDTO = performLists.getHomeUrlByBankId(
                    homeURLChoiceBox.getValue().getHomeBankingId(),
                    homeURLChoiceBox.getValue().getId());
            if (homeUrlDTO == null) {
                performMessage.errorMessage(
                        "Missing or Removed Environment",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Environment Not Found ❌</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Possible cause:</span> It may have been deleted or is no longer available.",
                        "<span style='font-style: italic;'>Action:</span> Select a valid Website / Environment.",
                        "<span style='font-style: italic;'>Tip:</span> Click 'Refresh' to reload the list.",
                        0);
                return;
            }

            rawName = nameFileOnWindows(rawName);

            BotJobLoadDTO createdBotJob = new BotJobLoadDTO();
            createdBotJob.setName(rawName);
            createdBotJob.setPriority(projectType);
            createdBotJob.setDescription(botJobDescription.getText().trim());
            createdBotJob.setHomeBankingId(homeURLChoiceBox.getValue().getHomeBankingId());
            createdBotJob.setHomeUrlId(homeURLChoiceBox.getValue().getId());

            ErrorMessage errorMessage = performDataBase.createNewBotJob(createdBotJob);

            int newBotJobId = performDataBase.getNewBotJobId();
            if (errorMessage == null && newBotJobId > -1) {
                createdBotJob.setId(newBotJobId);

                if (performDataBase.isConnDBWorks()) {
                    performDataBase.loadQuickBotJobs();
                }

                arViewBotJobScene.initialize(arWebDriver, createdBotJob, isEnabledLicence);
                arViewBotJobScene.showModal();

                log.info("Success creating new Bot Job ID: " + newBotJobId);
                Platform.runLater(() -> {
                    Stage currentStage = (Stage) createBotJobButton.getScene().getWindow();
                    if (currentStage != null) {
                        currentStage.close();
                    }
                });
            } else {

                log.error("Error creating BotJobDTO. Check the Block Creation!");
                performMessage.errorMessage(
                        "Create New Bot Job Failed ❌",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                + errorMessage.getErrorTitle() + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>" + errorMessage.getErrorHeader() + "</span>",
                        "Verify  [INSERT] or [UPDATE] or [SELECT]",
                        "<span style='font-style: italic;'>" + errorMessage.getErrorMessage() + "</span>",
                        0);
            }
        });
    }

    private String nameFileOnWindows(String rawName) {
        String safeFileName = rawName.replaceAll("[\\\\/:*?\"<>|]", "");

        safeFileName = safeFileName.replaceAll("[\\p{Cntrl}]", "").trim();

        if (safeFileName.isEmpty()) {
            safeFileName = "default_name";
        }

        if (safeFileName.length() > 100) {
            safeFileName = safeFileName.substring(0, 100);
        }
        return safeFileName;
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

    private String getSelectedAppType() {
        if (appTypeGroup == null) return "Web"; // fallback

        Toggle t = appTypeGroup.getSelectedToggle();

        if (t == rbWeb) return "Web App";
        if (t == rbAndroid) return "Android";
        if (t == rbIos) return "iOS";
        if (t == rbRestAppi) return "Rest API";

        return "Web App"; // default fallback
    }
}
