package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeUrlDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
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
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class ARNewBotJobPane extends ARPane {

    protected static volatile ARNewBotJobPane instance;

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

    private Label labelBotJobName;
    private Label labelBotJobDescription;
    private Label labelHomeBanking;

    private TextField botJobName;
    private TextField botJobDescription;

    private Button createBotJobButton;
    private Button refreshEnvsButton;
    private Button insertSitesdButton;

    private ChoiceBox<HomeUrlDTO> homeURLChoiceBox;

    private Pane mainPane;

    private boolean isEnabledLicence;
    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    private static final ARNewHomeBankingScene arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();

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

        labelBotJobName = new Label("Name:");
        labelBotJobName.setStyle(labelStyle);

        botJobName = new TextField();
        botJobName.setPromptText("Enter Bot Job Name");

        labelBotJobDescription = new Label("Description:");
        labelBotJobDescription.setStyle(labelStyle);

        botJobDescription = new TextField();
        botJobDescription.setPromptText("Enter Description (optional)");

        labelHomeBanking = new Label("URL / Environment:");
        labelHomeBanking.setStyle(labelStyle);
        labelHomeBanking.setAlignment(Pos.CENTER); // Works if label is in an HBox/VBox
        labelHomeBanking.setMaxWidth(Double.MAX_VALUE); // Allow stretching so CENTER alignment works
        VBox.setVgrow(labelHomeBanking, Priority.NEVER);

        // Load home URLs before creating the ChoiceBox
        if (performLists.getListHomeUrl().isEmpty()) {
            performDataBase.loadHomeUrls(null);
        }

        homeURLChoiceBox = new ChoiceBox<>();
        homeURLChoiceBox.setPrefWidth(250);
        homeURLChoiceBox.setMaxWidth(250);
        homeURLChoiceBox.setMinWidth(250);

        refreshEnvsButton = createPathButton();

        // Center the ChoiceBox + Button together
        HBox homeURLBox = new HBox(5, homeURLChoiceBox, refreshEnvsButton);
        homeURLBox.setAlignment(Pos.CENTER); // Center them horizontally
        homeURLBox.setPadding(new Insets(0, 0, 10, 0));
        homeURLBox.setFillHeight(true);

        populateHomeUrlChoiceBox();

        Tooltip tooltip = new Tooltip("Select the target URL / environment for the Bot Job");
        homeURLChoiceBox.setTooltip(tooltip);

        createBotJobButton = new Button("Create Bot Job");
        createBotJobButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        insertSitesdButton = new Button("Orgs / Environments");
        insertSitesdButton.setDefaultButton(true);
        HBox.setMargin(insertSitesdButton, new Insets(0, 0, 0, 50)); // 50px gap to the left

        HBox buttonsBox = new HBox(createBotJobButton, insertSitesdButton);
        buttonsBox.setAlignment(Pos.CENTER);

        labelBotJobName.setLabelFor(botJobName);
        labelBotJobDescription.setLabelFor(botJobDescription);
        labelHomeBanking.setLabelFor(homeURLChoiceBox);

        VBox mainLayout = new VBox(
                12,
                labelBotJobName,
                botJobName,
                labelBotJobDescription,
                botJobDescription,
                labelHomeBanking,
                homeURLBox,
                buttonsBox);

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
            if (performLists.getListHomeBanking().isEmpty()) {
                performDataBase.loadHomeBanking(null);
            }
            if (performLists.getListHomeUrl().isEmpty()) {
                performDataBase.loadHomeUrls(null);
            }

            arNewHomeBankingScene.initialize();
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
            performDataBase.loadHomeUrls(null);

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
            if (Strings.isNullOrEmpty(botJobName.getText().trim())) {
                performMessage.errorMessage(
                        "Missing Bot Job Name",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Bot Job Name cannot be empty.</span>",
                        "<span style='color: #000080; font-weight: bold;'>Please enter a name for the Bot Job to proceed.</span>",
                        null,
                        null,
                        0);
                return;
            }

            boolean existName = performLists.getListBotJob().stream().anyMatch(f -> f.getName()
                    .equalsIgnoreCase(botJobName.getText().trim()));

            if (existName) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The name you have entered is already in use.</span>",
                        "<span style='color: #000080; font-weight: bold;'>"
                                + botJobName.getText().trim() + "</span>",
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

            BotJobLoadDTO createdBotJob = new BotJobLoadDTO();
            createdBotJob.setName(botJobName.getText().trim());
            createdBotJob.setDescription(botJobDescription.getText().trim());
            createdBotJob.setHomeBankingId(homeURLChoiceBox.getValue().getHomeBankingId());
            createdBotJob.setHomeUrlId(homeURLChoiceBox.getValue().getId());

            ErrorMessage errorMessage = performDataBase.createNewBotJob(createdBotJob);

            int newBotJobId = performDataBase.getNewBotJobId();
            if (errorMessage == null && newBotJobId > -1) {
                createdBotJob.setId(newBotJobId);

                if (performDataBase.getConn() != null) {
                    performDataBase.loadQuickBotJobs();
                }

                arViewBotJobScene.initialize(arWebDriver, createdBotJob);
                arViewBotJobScene.showModal();

                ARLogger.getInstance(ARNewBotJobPane.class).info("Success creating new Bot Job ID: " + newBotJobId);
                Platform.runLater(() -> {
                    Stage currentStage = (Stage) createBotJobButton.getScene().getWindow();
                    if (currentStage != null) {
                        currentStage.close();
                    }
                });
            } else {
                ARLogger.getInstance(ARNewBotJobPane.class)
                        .severe("Error creating BotJobDTO. Check the Block Creation!");
                performMessage.errorMessage(
                        "Access Database error",
                        errorMessage.getErrorTitle(),
                        errorMessage.getErrorHeader(),
                        "Verify  [INSERT] or [UPDATE] or [SELECT]",
                        null,
                        0);
            }
        });
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
            ARLogger.getInstance(ARConfigurationPane.class)
                    .severe("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }
}
