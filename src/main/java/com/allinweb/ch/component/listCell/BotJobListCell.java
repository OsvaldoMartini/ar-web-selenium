package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import java.util.*;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

public class BotJobListCell extends ListCell<BotJobDTO> {

    public BotJobListCell() {}

    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
    }

    @Override
    protected void updateItem(BotJobDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;
        if (!empty && item != null && item.getHomeBanking() != null) {
            ABRComponentBuilder builder = new ABRComponentBuilder();
            Label botJobName = new Label(item.getName());
            Label botJobDescription = new Label(item.getDescription());
            Label homeBankingUrl = new Label(item.getHomeBanking().getName());
            Button deleteBotJobButton = builder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_CROSS, ABRConstants.SPACE_M, Insets.EMPTY);
            deleteBotJobButton.setOnMouseClicked(e -> {
                Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Are you sure you want to delete the bot job selected?",
                        ButtonType.YES,
                        ButtonType.NO);
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    deleteBotJob(item);
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

    private void deleteBotJob(BotJobDTO botJob) {
        List<BlockOrderDetailDTO> blockDetails = performDataBase.selectAllBlocks(botJob.getId());

        boolean botJobDeletion = false;
        for (BlockOrderDetailDTO block : blockDetails) {
            DeleteBlockDTO deleteBlock = new DeleteBlockDTO();
            deleteBlock.setBotJobId(block.getBotJobId());
            deleteBlock.setBlockId(block.getBlockId());
            botJobDeletion = performDataBase.deleteBlock(deleteBlock);
            if (!botJobDeletion) {
                break;
            }
        }
        if (botJobDeletion) {
            botJobDeletion = performDataBase.deleteBotJob(botJob.getId());
        }

        ABRSharedResources.getInstance().changeDbConnection();
        Text variableText1Styled = new Text(String.format("Bot Job \"%s\" Deleted!", botJob.getName()));
        variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        if (!botJobDeletion) {
            variableText1Styled = new Text(String.format("Bot Job \"%s\" NOT Deleted!", botJob.getName()));
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
        }

        VBox combinedTextContainer = new VBox();
        combinedTextContainer.setSpacing(5); // Add some sp

        combinedTextContainer.getChildren().add(variableText1Styled);

        performDataBase.showAlertCombinedVBOX(
                botJobDeletion ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                "Delete Bot-Job",
                botJobDeletion ? "Bot-Job deleted successfully!" : "Bot-Job NOT deleted!\"",
                null,
                combinedTextContainer);
    }
}
