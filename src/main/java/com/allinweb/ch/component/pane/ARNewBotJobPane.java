package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeUrlDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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

    private ChoiceBox<HomeUrlDTO> homeURLChoiceBox;

    private Pane mainPane;

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;

    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();

    private static final ARComponentBuilder componentBuilder = new ARComponentBuilder();

    // You mentioned this is the button creator, adapted here
    private Button createPathButton() {
        Button button = componentBuilder.buildButton(
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
        labelBotJobName = new Label("Name:");
        botJobName = new TextField();
        botJobName.setPromptText("Enter Bot Job Name");

        labelBotJobDescription = new Label("Description:");
        botJobDescription = new TextField();
        botJobDescription.setPromptText("Enter Description (optional)");

        labelHomeBanking = new Label("Url / Environment:");

        // Load home URLs before creating the ChoiceBox
        performDataBase.loadHomeUrls(null);

        homeURLChoiceBox = new ChoiceBox<>();
        refreshEnvsButton = createPathButton();

        // Create HBox to hold choicebox + refresh button horizontally
        HBox homeURLBox = new HBox(5, homeURLChoiceBox, refreshEnvsButton);
        homeURLBox.setPadding(new Insets(0, 0, 10, 0));
        homeURLBox.setFillHeight(true);
        HBox.setHgrow(homeURLChoiceBox, Priority.ALWAYS);

        populateHomeUrlChoiceBox();

        Tooltip tooltip = new Tooltip("Select the target URL / environment for the Bot Job");
        homeURLChoiceBox.setTooltip(tooltip);

        createBotJobButton = new Button("Create Bot Job");
        createBotJobButton.setDefaultButton(true);

        // Associate labels with inputs (for accessibility)
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
                createBotJobButton);

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
    public void initialize(ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver) {
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

            int newJobId = performDataBase.createNewBotJob(createdBotJob);

            if (newJobId > 0) {
                createdBotJob.setId(newJobId);

                if (performDataBase.getConn() != null) {
                    performDataBase.loadQuickBotJobs();
                }

                arViewBotJobScene.initialize(arWebDriver, createdBotJob);
                arViewBotJobScene.showModal();

                ARLogger.getInstance(ARNewBotJobPane.class).finer("ARNewBotJobPane CurrentStage close()");
                Platform.runLater(() -> {
                    Stage currentStage = (Stage) createBotJobButton.getScene().getWindow();
                    if (currentStage != null) {
                        currentStage.close();
                    }
                });
            } else {
                ARLogger.getInstance(Thread.class).severe("Error creating BotJobDTO. Check the Block Creation!");
            }
        });
    }
}
