package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ARConstants;
import java.util.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

public class BotJobListCell extends ListCell<BotJobLoadDTO> {

    private PerformMessage performMessage;
    private PerformDataBase performDataBase;
    private ARViewBotJobScene arViewBotJobScene;

    public BotJobListCell(
            PerformMessage performMessage, PerformDataBase performDataBase, ARViewBotJobScene arViewBotJobScene) {
        this.performMessage = performMessage;
        this.performDataBase = performDataBase;
        this.arViewBotJobScene = arViewBotJobScene;
    }

    public BotJobListCell() {
        // Default constructor
    }

    @Override
    protected void updateItem(BotJobLoadDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getHomeBankingLoadDTO() != null) {
            ARComponentBuilder builder = new ARComponentBuilder();
            Label botJobName = new Label(item.getName());
            Label botJobDescription = new Label(item.getDescription());
            Label homeBankingName = new Label(item.getHomeBankingLoadDTO().getName());

            // Create status label
            Label statusLabel = new Label(item.isActive() ? "Active" : "Inactive");
            statusLabel.setTextFill(item.isActive() ? Color.BLACK : Color.GREY);

            Button deleteBotJobButton = builder.buildButton(
                    "", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, Insets.EMPTY);
            deleteBotJobButton.setOnMouseClicked(e -> handleDelete(item));

            GridPane uiBotJob = new GridPane();
            uiBotJob.setPadding(new Insets(5));
            uiBotJob.setHgap(10);

            // Define column constraints with specific percentages
            ColumnConstraints col1 = new ColumnConstraints();
            col1.setPercentWidth(30); // botJobName

            ColumnConstraints col2 = new ColumnConstraints();
            col2.setPercentWidth(30); // botJobDescription

            ColumnConstraints col3 = new ColumnConstraints();
            col3.setPercentWidth(30); // homeBankingUrl

            ColumnConstraints col4 = new ColumnConstraints();
            col4.setPercentWidth(5); // statusLabel

            ColumnConstraints col5 = new ColumnConstraints();
            col5.setPercentWidth(5); // deleteBotJobButton

            // Align text properly in each column
            col1.setHalignment(HPos.LEFT);
            col2.setHalignment(HPos.LEFT);
            col3.setHalignment(HPos.LEFT);
            col4.setHalignment(HPos.CENTER);
            col5.setHalignment(HPos.RIGHT);

            // Apply constraints to the GridPane
            uiBotJob.getColumnConstraints().addAll(col1, col2, col3, col4, col5);

            // Add elements to the GridPane with proper column indexes
            uiBotJob.add(botJobName, 0, 0);
            uiBotJob.add(botJobDescription, 1, 0);
            uiBotJob.add(homeBankingName, 2, 0);
            uiBotJob.add(statusLabel, 3, 0);
            uiBotJob.add(deleteBotJobButton, 4, 0);

            AnchorPane row = new AnchorPane(uiBotJob);
            row.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getClickCount() == 2) {
                    arViewBotJobScene.initialize(item);
                    arViewBotJobScene.show();
                }
            });

            graphic = row;
        }
        Node finalGraphic = graphic;
        Platform.runLater(() -> setGraphic(finalGraphic));
    }

    private void handleDelete(BotJobLoadDTO item) {
        VBox confirmationBox = new VBox(
                5,
                createStyledText("Are you sure you want to delete the bot job selected?", "blue"),
                createStyledText(String.format("Bot Job: \"(%s)%s\"", item.getId(), item.getName()), "blue"),
                createStyledText("THIS ACTION IS GOING TO REMOVE ALL JOB DATA!!!", "red"),
                createStyledText("INCLUDING SAVED COMPONENTS FOR THIS JOB!!!", "red"));

        boolean confirmed = performMessage.showAlertCombinedVBOX(
                Alert.AlertType.CONFIRMATION, "Bot Job Deletion", "Remove All Details Bot Job", null, confirmationBox);

        if (confirmed) {
            deleteBotJob(item);
            Platform.runLater(() -> getListView().getItems().remove(item));
        }
    }

    private Text createStyledText(String content, String color) {
        Text text = new Text(content);
        text.setStyle(String.format("-fx-font-size: 18px; -fx-fill: %s;", color));
        return text;
    }

    private void deleteBotJob(BotJobLoadDTO botJob) {
        int rowsAffected = performDataBase.deleteBotJob(botJob.getId());

        if (rowsAffected == 0) {
            performMessage.showCustomModalDialogDragWin11(
                    "Action Delete Bot-Job",
                    "Bot-Job deleted successfully!",
                    String.format("The Bot Job \"%s\" was Deleted!", botJob.getName()),
                    null,
                    null,
                    false,
                    "Close",
                    null,
                    0);
        } else if (rowsAffected < 0) {
            performMessage.errorMessage(
                    "I cannot delete the BotJob Now",
                    "This Bot Job was Flagged as Inactive!",
                    "Some Access ROW still in use",
                    null,
                    null,
                    0);
            performDataBase.updateStatusBotJob(botJob.getId(), 0);
        }
    }
}
