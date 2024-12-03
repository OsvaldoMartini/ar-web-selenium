package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.google.common.base.Strings;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class ABRNewBotJobPane extends ABRPane {

    // UI components
    Label labelBotJobName;
    Label labelBotJobDescription;
    Label labelHomeBanking;

    TextField botJobName;
    TextField botJobDescription;

    Button createBotJobButton;

    ChoiceBox<HomeBankingDTO> homeBankingChoiceBox;

    VBox container;

    ListView<BotJobDTO> viewBotJobListView;

    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
    }

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    public ABRNewBotJobPane(ListView<BotJobDTO> viewBotJobListView) {
        this.viewBotJobListView = viewBotJobListView;
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
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
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
        ObservableList<HomeBankingDTO> homeBankingUrlList =
                ABRSharedResources.getInstance().getEntityList(HomeBankingDTO.class);
        homeBankingChoiceBox = new ChoiceBox<>(homeBankingUrlList);

        container = new VBox(
                labelBotJobName,
                botJobName,
                labelBotJobDescription,
                botJobDescription,
                labelHomeBanking,
                homeBankingChoiceBox,
                createBotJobButton);
        AnchorPane.setTopAnchor(container, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(container, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(container, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(container, ABRConstants.SPACE_M);
    }

    @Override
    public void initUIBehaviour() {
        homeBankingChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(HomeBankingDTO object) {
                if (object != null) {
                    return object.getName() + " | " + object.getUrl();
                }
                return null;
            }

            @Override
            public HomeBankingDTO fromString(String string) {
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
        // Looping through the ListView items
        List<BotJobDTO> items = viewBotJobListView.getItems();
        boolean existName = items.stream().anyMatch(f -> f.getName().equalsIgnoreCase(botJobName.getText()));
        executorService = Executors.newSingleThreadExecutor();

        if (existName) {
            alertToShow.setAlertType(Alert.AlertType.ERROR);
            alertToShow.setTitle("Duplicate Name");
            alertToShow.setHeaderText(String.format("The name already exist! %s", botJobName.getText()));
            alertToShow.setContentText("Main Message");
            executorService.execute(() -> {
                timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
                timeline.play(); // Start the timeline

                // Show the alert on the JavaFX Application Thread
                javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
            });
        }

        if (homeBankingChoiceBox.getValue() == null
                || Strings.isNullOrEmpty(homeBankingChoiceBox.getValue().getName())) {
            alertToShow.setTitle("WebSite is Empty!");
            alertToShow.setHeaderText("Select an web Site!");
            alertToShow.setContentText("Main Message");

            executorService.execute(() -> {
                timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
                timeline.play(); // Start the timeline

                // Show the alert on the JavaFX Application Thread
                javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
            });
        }

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
        try {
            // Check if name doesn't exist and the selected value from homeBankingChoiceBox is not null or empty
            if (!existName
                    && !Strings.isNullOrEmpty(homeBankingChoiceBox.getValue().toString())) {

                // Create a new BotJobDTO object and set its properties
                BotJobDTO createdBotJob = new BotJobDTO();
                createdBotJob.setName(botJobName.getText());
                createdBotJob.setDescription(botJobDescription.getText());
                createdBotJob.setHomeBanking(homeBankingChoiceBox.getValue());

                try {
                    // Ensure any UI-related updates are done on the JavaFX application thread
                    ABRSharedResources.getInstance().addEntity(createdBotJob, BotJobDTO.class, () -> {
                        try {
                            // Ensure any UI-related updates are done on the JavaFX application thread
                            Platform.runLater(() -> {
                                int blockId = createBlock(createdBotJob);
                                if (blockId > 0) {
                                    new ABRViewBotJobScene(createdBotJob.getId()).show();

                                    // Close the current window
                                    Platform.runLater(() -> {
                                        Stage currentStage = (Stage)
                                                createBotJobButton.getScene().getWindow();
                                        if (currentStage != null) {
                                            currentStage.close(); // Close the current stage
                                        }
                                    });

                                } else {

                                    ABRLogger.getInstance(Thread.class)
                                            .severe("Error creating BotJobDTO check the Block Creation!");
                                }
                            });
                        } catch (Exception e) {
                            ABRLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "Error in callback after creating BotJobDTO: \nError: %s", e.getMessage()));
                        }
                    });
                } catch (Exception e) {
                    ABRLogger.getInstance(Thread.class)
                            .severe(String.format(
                                    "Error in callback after creating BotJobDTO: \nError: %s", e.getMessage()));
                }
            }
        } catch (Exception e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error during BotJobDTO creation.\nError: %s", e.getMessage()));
        }
    }

    private int createBlock(BotJobDTO createdBotJob) {
        try {
            // Create a new BlockDTO object and set its properties
            BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
            newBlockDetails.setBlockName(createdBotJob.getName() + " default block");
            newBlockDetails.setBlockDescription(
                    !Strings.isNullOrEmpty(createdBotJob.getDescription())
                            ? createdBotJob.getDescription()
                            : createdBotJob.getName() + " block description");
            newBlockDetails.setTypeId(1);
            newBlockDetails.setBotJobId(createdBotJob.getId());

            return performDataBase.createNewBlock(newBlockDetails);

        } catch (Exception e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format(
                            "Error creating and saving BlockDTO for BotJob Name %S\nError: %s",
                            createdBotJob.getName(), e.getMessage()));
        }

        return -1;
    }
}
