package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
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
import javafx.util.StringConverter;

public class ABRElementValuePane extends ABRPane {

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

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    public ABRElementValuePane(ListView<BotJobDTO> viewBotJobListView) {
        this.viewBotJobListView = viewBotJobListView;
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(container);
    }

    @Override
    public void initUIComponents() {
        // Create a single-threaded executor service
        executorService = Executors.newSingleThreadExecutor();

        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));
        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Countdown Alert");
        alertToShow.setHeaderText("Count Down");
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

        if (existName) {
            alertToShow.setTitle("Duplicate Name");
            alertToShow.setHeaderText(String.format("The name already exist! %s", botJobName.getText()));

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

            executorService.execute(() -> {
                timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
                timeline.play(); // Start the timeline

                // Show the alert on the JavaFX Application Thread
                javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
            });
        }

        if (executorService != null) {
            executorService.shutdown();
        }
        if (!existName && !Strings.isNullOrEmpty(homeBankingChoiceBox.getValue().toString())) {
            BotJobDTO createdBotJob = new BotJobDTO();
            createdBotJob.setName(botJobName.getText());
            createdBotJob.setDescription(botJobDescription.getText());
            createdBotJob.setHomeBanking(homeBankingChoiceBox.getValue());
            ABRSharedResources.getInstance()
                    .addEntity(createdBotJob, BotJobDTO.class, () -> createBotJobBlock(createdBotJob));
        }
    }

    private void createBotJobBlock(BotJobDTO createdBotJob) {
        BlockDTO defaultBlock = new BlockDTO();
        defaultBlock.setName(createdBotJob.getName() + " default block");
        defaultBlock.setDescription(createdBotJob.getName() + " block description");
        defaultBlock.setTypeId(1);
        defaultBlock.setBotJob(createdBotJob);
        ABRSharedResources.getInstance().addEntity(defaultBlock, BlockDTO.class, () -> {
            Platform.runLater(() -> {
                new ABRViewBotJobScene(createdBotJob.getId()).show();
            });
        });
    }
}
