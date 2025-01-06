package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import java.util.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

public class BotJobListCell extends ListCell<BotJobLoadDTO> {

    public BotJobListCell() {}

    private static final PerformDataBase performDataBase;
    private static final PerformActions performAction;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        performAction = PerformActions.getInstance();
    }

    @Override
    protected void updateItem(BotJobLoadDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getHomeBankingLoadDTO() != null) {
            ABRComponentBuilder builder = new ABRComponentBuilder();
            Label botJobName = new Label(item.getName());
            Label botJobDescription = new Label(item.getDescription());
            Label homeBankingUrl = new Label(item.getHomeBankingLoadDTO().getName());
            Button deleteBotJobButton = builder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_CROSS, ABRConstants.SPACE_M, Insets.EMPTY);
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

                boolean alertResponse = performDataBase.showAlertCombinedVBOX(
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
            con.setPercentWidth(25);
            con.setHalignment(HPos.LEFT);
            uiBotJob.getColumnConstraints().add(con);
            uiBotJob.getColumnConstraints().add(con);
            uiBotJob.getColumnConstraints().add(con);
            ColumnConstraints con2 = new ColumnConstraints();
            con2.setPercentWidth(25);
            con2.setHalignment(HPos.CENTER);
            uiBotJob.getColumnConstraints().add(con2);
            AnchorPane.setTopAnchor(uiBotJob, ABRConstants.SPACE_ZERO);
            AnchorPane.setBottomAnchor(uiBotJob, ABRConstants.SPACE_ZERO);
            AnchorPane.setLeftAnchor(uiBotJob, ABRConstants.SPACE_ZERO);
            AnchorPane.setRightAnchor(uiBotJob, ABRConstants.SPACE_ZERO);
            uiBotJob.add(botJobName, 0, 0);
            uiBotJob.add(botJobDescription, 1, 0);
            uiBotJob.add(homeBankingUrl, 2, 0);
            uiBotJob.add(deleteBotJobButton, 3, 0);
            AnchorPane row = new AnchorPane(uiBotJob);
            row.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getClickCount() == 2) {
                    new ABRViewBotJobScene(item.getId()).show();
                }
            });
            graphic = row;
        }
        Node finalGraphic = graphic;
        Platform.runLater(() -> setGraphic(finalGraphic));
    }

    private void deleteBotJob(BotJobLoadDTO botJob) {
        List<BlockOrderDetailDTO> blockDetails = performDataBase.selectAllBlocks(botJob.getId());

        //        boolean botJobDeletion = false;
        //        for (BlockOrderDetailDTO block : blockDetails) {
        //            DeleteBlockDTO deleteBlock = new DeleteBlockDTO();
        //            deleteBlock.setBotJobId(block.getBotJobId());
        //            deleteBlock.setBlockId(block.getBlockId());
        //            botJobDeletion = performDataBase.deleteBlock(deleteBlock);
        //            if (!botJobDeletion) {
        //                break;
        //            }
        //        }
        int rowsAffected = performDataBase.deleteBotJob(botJob.getId());

        Text variableText1Styled = new Text(String.format("Bot Job \"%s\" Deleted!", botJob.getName()));
        variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        Text variableText2Styled = new Text(String.format("Rows Affected: \"%s\"", rowsAffected));
        variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        if (rowsAffected == 0) {
            variableText1Styled = new Text(String.format("Bot Job \"%s\" NOT Deleted!", botJob.getName()));
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
        }

        VBox combinedTextContainer = new VBox();
        combinedTextContainer.setSpacing(5); // Add some sp

        combinedTextContainer.getChildren().addAll(variableText1Styled, variableText2Styled);

        performDataBase.showAlertCombinedVBOX(
                rowsAffected > 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                "Delete Bot-Job",
                rowsAffected > 0 ? "Bot-Job deleted successfully!" : "Bot-Job NOT deleted!\"",
                null,
                combinedTextContainer);
    }
}
