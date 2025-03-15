package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
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
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.util.Duration;
import javafx.util.StringConverter;

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
    private ChoiceBox<HomeBankingLoadDTO> homeBankingChoiceBox;

    private final ObservableList<BotJobLoadDTO> botJobList;
    //    private final ListView<BotJobLoadDTO> viewBotJobListView;

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private Alert alertToShow;

    private PerformDataBase performDataBase;
    private PerformMessage performMessage;
    private ARViewBotJobScene arViewBotJobScene;

    public ARNewBotJobPane(
            ARViewBotJobScene arViewBotJobScene,
            PerformDataBase performDataBase,
            PerformMessage performMessage,
            ObservableList<BotJobLoadDTO> botJobList) {
        //        this.viewBotJobListView = viewBotJobListView;
        this.botJobList = botJobList; // FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
        this.arViewBotJobScene = arViewBotJobScene;
        this.performDataBase = performDataBase;
        this.performMessage = performMessage;
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
        //                ARSharedResources.getInstance().getEntityList(HomeBankingDTO.class);

        homeBankingList.clear();
        homeBankingList.addAll(PerformDataBase.loadAllHomeBanking());
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

                Text variableText1Styled = new Text("Name: ");
                variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                Text variableText2Styled = new Text(botJobName.getText().trim());
                variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                VBox combinedTextContainer = new VBox();
                combinedTextContainer.setSpacing(5);

                combinedTextContainer.getChildren().addAll(variableText1Styled, variableText2Styled);

                performMessage.showAlertCombinedVBOX(
                        Alert.AlertType.WARNING,
                        "Duplicate Name",
                        "The name already exists!",
                        null,
                        combinedTextContainer);
                //                viewBotJobListView.refresh(); // Refresh the ListView to update any UI changes
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

                arViewBotJobScene.initialize(createdBotJob, botJobList);
                arViewBotJobScene.show();

                // Close the current window
                //                Stage currentStage = (Stage) createBotJobButton.getScene().getWindow();
                //                if (currentStage != null) {
                //                    currentStage.close();
                //                }
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
