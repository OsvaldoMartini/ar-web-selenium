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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.openqa.selenium.WebDriver;

public class ARNewBotJobPane extends ARPane {

    // UI components
    private Label labelBotJobName;
    private Label labelBotJobDescription;
    private Label labelHomeBanking;

    private TextField botJobName;
    private TextField botJobDescription;

    private Button createBotJobButton;

    private VBox container;

    private ObservableList<HomeBankingLoadDTO> homeBankingList = FXCollections.observableArrayList();
    private final ObservableList<BotJobLoadDTO> botJobList;
    private ObservableList<WebDriver> webDriverList;
    //    private final ListView<BotJobLoadDTO> viewBotJobListView;

    private ChoiceBox<HomeBankingLoadDTO> homeBankingChoiceBox;

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private Alert alertToShow;

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;

    static {
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    public ARNewBotJobPane(
            ARViewBotJobScene arViewBotJobScene,
            ARWebDriver arWebDriver,
            ObservableList<BotJobLoadDTO> botJobList,
            ObservableList<WebDriver> webDriverList) {
        //        this.viewBotJobListView = viewBotJobListView;
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.botJobList = botJobList; // FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
        this.webDriverList = webDriverList;
        //        this.viewBotJobListView.setItems(botJobList);
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(container);
    }

    @Override
    public void initUIComponents() {
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        countdownLabel.setVisible(false);

        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Title");
        alertToShow.setHeaderText("Header Message");
        alertToShow.setContentText("Main Message");
        alertToShow.initModality(Modality.APPLICATION_MODAL);
        alertToShow.getDialogPane().setContent(stackPane);

        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        labelBotJobName = new Label("Name:");
        botJobName = new TextField();
        labelBotJobDescription = new Label("Description:");
        botJobDescription = new TextField();
        createBotJobButton = new Button("Create Bot Job");
        labelHomeBanking = new Label("Url:");

        //        ObservableList<HomeBankingDTO> homeBankingUrlList =
        //                PerformDataBase..getEntityList(HomeBankingDTO.class);

        homeBankingList.clear();
        homeBankingList.addAll(performDataBase.loadAllHomeBanking());
        homeBankingChoiceBox = new ChoiceBox<>(homeBankingList);

        container = new VBox(
                labelBotJobName,
                botJobName,
                labelBotJobDescription,
                botJobDescription,
                labelHomeBanking,
                homeBankingChoiceBox,
                createBotJobButton);
        container.setSpacing(10);

        AnchorPane.setTopAnchor(container, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(container, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(container, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(container, ARConstants.SPACE_M);
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
            boolean existName = botJobList.stream().anyMatch(f -> f.getName()
                    .equalsIgnoreCase(botJobName.getText().trim()));

            if (existName) {
                performMessage.errorMessage(
                        "Duplicate Name",
                        "<span style='color: #000080; font-weight: bold; font-size: 14px;'>Bot Job Name already exists</span>",
                        "<span style='color: #000080; font-weight: bold;'>"
                                + botJobName.getText().trim() + "</span>",
                        null,
                        null,
                        0);

                return;
            }

            if (homeBankingChoiceBox.getValue() == null
                    || Strings.isNullOrEmpty(homeBankingChoiceBox.getValue().getName())) {
                showAlert("Website is Empty!", "Select a website!", Alert.AlertType.WARNING);
                return;
            }

            BotJobLoadDTO createdBotJob = new BotJobLoadDTO();
            createdBotJob.setName(botJobName.getText().trim());
            createdBotJob.setDescription(botJobDescription.getText().trim());
            createdBotJob.setHomeBankingId(homeBankingChoiceBox.getValue().getId());

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
                arViewBotJobScene.show();

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

    private void showAlert(String title, String message, Alert.AlertType alertType) {
        alertToShow.setAlertType(alertType);
        alertToShow.setTitle(title);
        alertToShow.setHeaderText(message);
        alertToShow.setContentText("Main Message");
        remainingSeconds = SECONDS;
        timeline.setCycleCount(SECONDS);
        timeline.play();
        alertToShow.showAndWait();
    }
}
