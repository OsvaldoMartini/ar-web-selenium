package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.google.common.base.Strings;
import java.awt.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class ARNewBotJobPane extends ARPane {

    protected static volatile ARNewBotJobPane instance;

    // Private constructor to prevent instantiation
    private ARNewBotJobPane() {
        // Initialize if necessary
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

    private ObservableList<HomeBankingLoadDTO> homeBankingList = FXCollections.observableArrayList();
    private ObservableList<BotJobLoadDTO> botJobList;
    //    private final ListView<BotJobLoadDTO> viewBotJobListView;
    public void initialize(
            ARViewBotJobScene arViewBotJobScene, ARWebDriver arWebDriver, ObservableList<BotJobLoadDTO> botJobList) {
        //        this.viewBotJobListView = viewBotJobListView;
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.botJobList = botJobList; // FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
        //        this.viewBotJobListView.setItems(botJobList);
    }

    // UI components
    private Label labelBotJobName;
    private Label labelBotJobDescription;
    private Label labelHomeBanking;

    private TextField botJobName;
    private TextField botJobDescription;

    private Button createBotJobButton;

    private Pane mainPane;

    private ChoiceBox<HomeBankingLoadDTO> homeBankingChoiceBox;

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;

    static {
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        labelBotJobName = new Label("Name:");
        botJobName = new TextField();
        labelBotJobDescription = new Label("Description:");
        botJobDescription = new TextField();
        createBotJobButton = new Button("Create Bot Job");
        labelHomeBanking = new Label("Url:");

        //        ObservableList<HomeBankingDTO> homeBankingUrlList =
        //                PerformDataBase..getEntityList(HomeBankingDTO.class);

        homeBankingList.clear();
        homeBankingList.addAll(performDataBase.loadHomeBanking(null));
        homeBankingChoiceBox = new ChoiceBox<>(homeBankingList);

        VBox mainLayout = new VBox(
                10,
                labelBotJobName,
                botJobName,
                labelBotJobDescription,
                botJobDescription,
                labelHomeBanking,
                homeBankingChoiceBox,
                createBotJobButton);

        mainLayout.setPadding(new Insets(10));
        mainLayout.setFillWidth(true); // Ensure components stretch horizontally

        AnchorPane.setTopAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(mainLayout, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(mainLayout, ARConstants.SPACE_M);

        mainPane = new AnchorPane(mainLayout);
    }

    @Override
    public void initUIBehaviour() {
        homeBankingChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(HomeBankingLoadDTO object) {
                if (object != null) {
                    return object.getName() + " | " + object.getUrl();
                }
                return null;
            }

            @Override
            public HomeBankingLoadDTO fromString(String string) {
                return null;
            }
        });

        createBotJobButton.setOnMouseClicked(e -> launchBotJobCreation());
    }

    private void launchBotJobCreation() {
        Task<Void> botJobCreationTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
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
                        "Missing Bot Job Name", // Clearer, more direct title
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Bot Job Name cannot be empty.</span>", // Stronger emphasis on the issue
                        "<span style='color: #000080; font-weight: bold;'>Please enter a name for the Bot Job to proceed.</span>", // Clear instruction
                        null, // No need for redundant messages
                        null,
                        0);
                return;
            }

            boolean existName = botJobList.stream().anyMatch(f -> f.getName()
                    .equalsIgnoreCase(botJobName.getText().trim()));

            if (existName) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists", // Clearer, more direct title
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The name you have entered is already in use.</span>", // Stronger emphasis on the issue
                        "<span style='color: #000080; font-weight: bold;'>"
                                + botJobName.getText().trim() + "</span>",
                        null, // No need for redundant messages
                        null,
                        0);

                return;
            }

            if (homeBankingChoiceBox.getValue() == null
                    || Strings.isNullOrEmpty(homeBankingChoiceBox.getValue().getName())) {
                performMessage.errorMessage(
                        "Missing Website", // Clearer, more direct title
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The Website cannot be empty.</span>", // Stronger emphasis on the issue
                        "<span style='color: #000080; font-weight: bold;'>Please select a Website for the Bot Job to proceed.</span>", // Clear instruction
                        null, // No need for redundant messages
                        null,
                        0);
                return;
            }

            BotJobLoadDTO createdBotJob = new BotJobLoadDTO();
            createdBotJob.setName(botJobName.getText().trim());
            createdBotJob.setDescription(botJobDescription.getText().trim());
            createdBotJob.setHomeBankingId(homeBankingChoiceBox.getValue().getId());
            createdBotJob.setHomeUrlId(
                    homeBankingChoiceBox.getValue().getHomeUrlDTOs().get(0).getId());

            int newJobId = performDataBase.createNewBotJob(createdBotJob);

            if (newJobId > 0) {
                createdBotJob.setId(newJobId);
                this.botJobList.add(createdBotJob); // Add the new bot job to the ObservableList

                // Refresh the ListView after adding the new bot job
                this.botJobList.clear();
                this.botJobList.addAll(performDataBase.loadAllBotJobs());
                //                viewBotJobListView.setItems(botJobList);
                //                viewBotJobListView.refresh(); // Explicitly refresh the ListView

                arViewBotJobScene.initialize(arWebDriver, createdBotJob, botJobList);
                arViewBotJobScene.showModal();

                // Close the current window
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
