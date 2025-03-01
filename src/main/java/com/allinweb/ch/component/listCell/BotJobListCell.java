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

    public BotJobListCell() {}

    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    @Override
    protected void updateItem(BotJobLoadDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getHomeBankingLoadDTO() != null) {
            ARComponentBuilder builder = new ARComponentBuilder();
            Label botJobName = new Label(item.getName());
            Label botJobDescription = new Label(item.getDescription());
            Label homeBankingUrl = new Label(item.getHomeBankingLoadDTO().getName());

            // Create status label
            Label statusLabel = new Label(item.isActive() ? "Active" : "Inactive");
            if (!item.isActive()) {
                statusLabel.setTextFill(Color.GREY); // Grey out inactive items
            } else {
                statusLabel.setTextFill(Color.BLACK); // Reset to black for active items
            }

            Button deleteBotJobButton = builder.buildButton(
                    "", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, Insets.EMPTY);
            deleteBotJobButton.setOnMouseClicked(e -> {
                VBox combinedTextContainer = new VBox();
                combinedTextContainer.setSpacing(5);

                Text variableText1Styled = new Text("Are you sure you want to delete the bot job selected?");
                variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                Text variableText2Styled = new Text(String.format("Bot Job: \"(%s)%s\"", item.getId(), item.getName()));
                variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                Text variableText3Styled = new Text("THIS ACTION IS GOING TO REMOVE JOB ALL DATA!!!");
                variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                Text variableText4Styled = new Text("INCLUDING SAVED COMPONENTS FOR THIS JOB!!!");
                variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                VBox allMsgVer = new VBox();
                allMsgVer
                        .getChildren()
                        .addAll(variableText1Styled, variableText2Styled, variableText3Styled, variableText4Styled);

                combinedTextContainer.getChildren().addAll(allMsgVer);

                boolean alertResponse = performMessage.showAlertCombinedVBOX(
                        Alert.AlertType.CONFIRMATION,
                        "Bot Job Deletion",
                        "Remove All Details Bot Job",
                        null,
                        combinedTextContainer);

                if (alertResponse) {
                    deleteBotJob(item); // Call your delete method here

                    // After deleting, reset the graphic or refresh the UI
                    Platform.runLater(() -> {
                        // Here we can update the layout or remove the item from the list.
                        // For example, if you want to remove the deleted BotJob from the ListView:
                        ListView<BotJobLoadDTO> listView = getListView(); // Retrieve the ListView reference
                        listView.getItems().remove(item); // Remove the item from the list
                    });
                }
            });

            GridPane uiBotJob = new GridPane();
            ColumnConstraints con = new ColumnConstraints();
            con.setPercentWidth(20); // Adjust percentage width
            con.setHalignment(HPos.LEFT);
            uiBotJob.getColumnConstraints().add(con);
            uiBotJob.getColumnConstraints().add(con);
            uiBotJob.getColumnConstraints().add(con);

            ColumnConstraints con2 = new ColumnConstraints();
            con2.setPercentWidth(20); // Adjust percentage width
            con2.setHalignment(HPos.CENTER);
            uiBotJob.getColumnConstraints().add(con2);

            ColumnConstraints con3 = new ColumnConstraints();
            con3.setPercentWidth(20); // Adjust percentage width
            con3.setHalignment(HPos.CENTER);
            uiBotJob.getColumnConstraints().add(con3);

            AnchorPane.setTopAnchor(uiBotJob, ARConstants.SPACE_ZERO);
            AnchorPane.setBottomAnchor(uiBotJob, ARConstants.SPACE_ZERO);
            AnchorPane.setLeftAnchor(uiBotJob, ARConstants.SPACE_ZERO);
            AnchorPane.setRightAnchor(uiBotJob, ARConstants.SPACE_ZERO);

            uiBotJob.add(botJobName, 0, 0);
            uiBotJob.add(botJobDescription, 1, 0);
            uiBotJob.add(homeBankingUrl, 2, 0);
            uiBotJob.add(statusLabel, 3, 0); // Add status label
            uiBotJob.add(deleteBotJobButton, 4, 0); // Shift delete button

            AnchorPane row = new AnchorPane(uiBotJob);
            row.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getClickCount() == 2) {
                    new ARViewBotJobScene(item).show();
                }
            });
            graphic = row;
        }
        Node finalGraphic = graphic;
        Platform.runLater(() -> setGraphic(finalGraphic));
    }

    private void deleteBotJob(BotJobLoadDTO botJob) {
        int rowsAffected = performDataBase.deleteBotJob(botJob.getId());

        if (rowsAffected == 0) {
            performMessage.showCustomModalDialog(
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
                    "I cannot delete the BojJob Now",
                    "This Bot Job was Flagged as Inactive!",
                    "Some Access ROW still in use",
                    null,
                    null,
                    0);

            performDataBase.updateStatusBotJob(botJob.getId(), 0);
        }
    }
}
