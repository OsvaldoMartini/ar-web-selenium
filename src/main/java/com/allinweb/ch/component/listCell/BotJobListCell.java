package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ARConstants;
import java.util.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.openqa.selenium.WebDriver;

public class BotJobListCell extends ListCell<BotJobLoadDTO> {

    private ARViewBotJobScene arViewBotJobScene;
    private ARWebDriver arWebDriver;
    private ObservableList<BotJobLoadDTO> botJobList;
    private ObservableList<WebDriver> webDriverList;

    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;

    static {
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    public BotJobListCell(
            ARViewBotJobScene arViewBotJobScene,
            ARWebDriver arWebDriver,
            ObservableList<BotJobLoadDTO> botJobList,
            ObservableList<WebDriver> webDriverList) {
        this.arViewBotJobScene = arViewBotJobScene;
        this.arWebDriver = arWebDriver;
        this.botJobList = botJobList;
        this.webDriverList = webDriverList;
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

            HBox uiBotJob = new HBox(10); // 10 pixels spacing
            uiBotJob.setPadding(new Insets(5));

            Label botJobName = new Label(item.getName());
            botJobName.setStyle("-fx-font-size: 14px;"); // Reduced font size
            botJobName.setMinWidth(150); // Set minimum width
            botJobName.setMaxWidth(150); // Set minimum width
            botJobName.setWrapText(true);

            Label botJobDescription = new Label(item.getDescription());
            botJobDescription.setStyle("-fx-font-size: 14px;"); // Reduced font size
            botJobDescription.setMinWidth(150); // Set minimum width
            botJobDescription.setMaxWidth(150); // Set minimum width
            botJobDescription.setWrapText(true);

            Label homeBankingName = new Label(item.getHomeBankingLoadDTO().getName());
            homeBankingName.setStyle("-fx-font-size: 14px;"); // Reduced font size
            homeBankingName.setMinWidth(100); // Set minimum width
            homeBankingName.setMaxWidth(100); // Set minimum width
            homeBankingName.setWrapText(true);

            Label statusLabel = new Label(item.isActive() ? "Active" : "Inactive");
            statusLabel.setTextFill(item.isActive() ? Color.BLACK : Color.GREY);
            statusLabel.setStyle("-fx-font-size: 14px;"); // Reduced font size
            statusLabel.setMinWidth(50); // Set minimum width
            statusLabel.setMaxWidth(50); // Set minimum width

            Button deleteBotJobButton = builder.buildButton(
                    "", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, Insets.EMPTY);
            deleteBotJobButton.setOnMouseClicked(e -> handleDelete(item));
            deleteBotJobButton.setMinWidth(20); // Set minimum width
            deleteBotJobButton.setMaxWidth(20); // Set minimum width
            deleteBotJobButton.setMinHeight(20); // Set minimum width
            deleteBotJobButton.setMaxHeight(20); // Set minimum width

            Region spacer1 = new Region();
            HBox.setHgrow(spacer1, Priority.ALWAYS);
            Region spacer2 = new Region();
            HBox.setHgrow(spacer2, Priority.ALWAYS);
            Region spacer3 = new Region();
            HBox.setHgrow(spacer3, Priority.ALWAYS);
            Region spacer4 = new Region();
            HBox.setHgrow(spacer4, Priority.ALWAYS);

            uiBotJob.getChildren()
                    .addAll(
                            botJobName,
                            spacer1,
                            botJobDescription,
                            spacer2,
                            homeBankingName,
                            spacer3,
                            statusLabel,
                            spacer4,
                            deleteBotJobButton);

            uiBotJob.getChildren().clear();

            Region spacer1_25 = new Region();
            HBox.setHgrow(spacer1_25, Priority.ALWAYS);

            Region spacer2_25 = new Region();
            HBox.setHgrow(spacer2_25, Priority.ALWAYS);

            Region spacer3_25 = new Region();
            HBox.setHgrow(spacer3_25, Priority.ALWAYS);

            Region spacer4_12_5 = new Region();
            HBox.setHgrow(spacer4_12_5, Priority.ALWAYS);
            Region spacer4_12_5_2 = new Region();
            HBox.setHgrow(spacer4_12_5_2, Priority.ALWAYS);

            Region spacer5_12_5 = new Region();
            HBox.setHgrow(spacer5_12_5, Priority.ALWAYS);
            Region spacer5_12_5_2 = new Region();
            HBox.setHgrow(spacer5_12_5_2, Priority.ALWAYS);

            uiBotJob.getChildren()
                    .addAll(
                            botJobName,
                            spacer1_25,
                            botJobDescription,
                            spacer2_25,
                            homeBankingName,
                            spacer3_25,
                            statusLabel,
                            spacer4_12_5,
                            spacer4_12_5_2,
                            deleteBotJobButton,
                            spacer5_12_5,
                            spacer5_12_5_2);

            // Align elements
            HBox.setHgrow(botJobName, Priority.NEVER);
            HBox.setHgrow(botJobDescription, Priority.NEVER);
            HBox.setHgrow(homeBankingName, Priority.NEVER);
            HBox.setHgrow(statusLabel, Priority.NEVER);
            HBox.setHgrow(deleteBotJobButton, Priority.NEVER);

            HBox.setMargin(statusLabel, new Insets(0, 0, 0, 10));
            HBox.setMargin(deleteBotJobButton, new Insets(0, 0, 0, 10));

            statusLabel.setAlignment(Pos.CENTER);
            deleteBotJobButton.setAlignment(Pos.CENTER_RIGHT);

            AnchorPane row = new AnchorPane(uiBotJob);

            row.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getClickCount() == 2) {
                    arViewBotJobScene.initialize(arWebDriver, item, botJobList);
                    arViewBotJobScene.showModal();
                }
            });

            graphic = row;
        }
        Node finalGraphic = graphic;
        Platform.runLater(() -> setGraphic(finalGraphic));
    }

    private void handleDelete(BotJobLoadDTO item) {
        //        VBox confirmationBox = new VBox(
        //                5,
        //                createStyledText("Are you sure you want to delete the bot job selected?", "blue"),
        //                createStyledText(String.format("Bot Job: \"(%s)%s\"", item.getId(), item.getName()), "blue"),
        //                createStyledText("THIS ACTION IS GOING TO REMOVE ALL JOB DATA!!!", "red"),
        //                createStyledText("INCLUDING SAVED COMPONENTS FOR THIS JOB!!!", "red"));
        //
        //        boolean confirmed = performMessage.showAlertCombinedVBOX(
        //                Alert.AlertType.CONFIRMATION, "Bot Job Deletion", "Remove All Details Bot Job", null,
        // confirmationBox);
        //
        //        if (confirmed) {
        //            deleteBotJob(item);
        //            Platform.runLater(() -> getListView().getItems().remove(item));
        //        }

        ARConstants.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                "Bot Job Deletion",
                "<span style='color: #000080; font-weight: bold; font-size: 14px;'>Are you sure you want to delete the bot job selected?</span>",
                "<span style='color: #000080; font-weight: bold;'>"
                        + String.format("Bot Job: \"(%s)%s\"", item.getId(), item.getName()) + "</span>",
                "<span style='color: red; font-weight: bold;'>THIS ACTION IS GOING TO REMOVE ALL JOB DATA!!!</span>",
                "<span style='color: red; font-weight: bold;'>INCLUDING SAVED COMPONENTS FOR THIS JOB!!!</span>",
                true,
                "OK",
                "Cancel",
                0);

        if (!respModal.equals(ARConstants.DialogModal.STOP)) {
            deleteBotJob(item);
            Platform.runLater(() -> getListView().getItems().remove(item));
        }
    }

    private Text createStyledText(String content, String color) {
        Text text = new Text(content);
        text.setStyle(String.format("-fx-font-size: 14px; -fx-fill: %s;", color));
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
