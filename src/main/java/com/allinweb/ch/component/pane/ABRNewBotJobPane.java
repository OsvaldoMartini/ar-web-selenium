package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(container);
    }

    @Override
    public void initUIComponents() {
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
        BotJobDTO createdBotJob = new BotJobDTO();
        createdBotJob.setName(botJobName.getText());
        createdBotJob.setDescription(botJobDescription.getText());
        createdBotJob.setHomeBanking(homeBankingChoiceBox.getValue());
        ABRSharedResources.getInstance()
                .addEntity(createdBotJob, BotJobDTO.class, () -> createBotJobBlock(createdBotJob));
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
