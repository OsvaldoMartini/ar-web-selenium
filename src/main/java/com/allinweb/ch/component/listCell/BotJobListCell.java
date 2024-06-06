package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRViewBotJobScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import java.util.*;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class BotJobListCell extends ListCell<BotJobDTO> {

    public BotJobListCell() {}

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
        Queue<BlockDTO> blocks = new LinkedList<>(ABRSharedResources.getInstance()
                .getEntityList(BlockDTO.class, block -> block.getBotJob().getId() == botJob.getId()));
        ABRSharedResources.getInstance().removeAllEntity(blocks, BlockDTO.class, () -> ABRSharedResources.getInstance()
                .removeEntity(
                        botJob,
                        BotJobDTO.class,
                        () -> new ABRAlertScene(
                                Alert.AlertType.INFORMATION,
                                "Bot Job deleted",
                                "The bot job has been deleted successfully",
                                ButtonType.OK)));
        Set<Integer> blockIds = new HashSet<>();
        blocks.forEach(block -> blockIds.add(block.getId()));

        Queue<BlockLoopInstructionDTO> instructions = new LinkedList<>(ABRSharedResources.getInstance()
                .getEntityList(
                        BlockLoopInstructionDTO.class,
                        instr -> blockIds.contains(instr.getBlock().getId())));

        Set<Integer> instructionIds = new HashSet<>();
        instructions.forEach(instr -> instructionIds.add(instr.getId()));

        Queue<InstructionReferenceDTO> references = new LinkedList<>(ABRSharedResources.getInstance()
                .getEntityList(
                        InstructionReferenceDTO.class,
                        ref -> instructionIds.contains(
                                ref.getBlockLoopInstructionDTO().getId())));

        ABRSharedResources.getInstance()
                .removeAllEntity(references, InstructionReferenceDTO.class, () -> ABRSharedResources.getInstance()
                        .removeAllEntity(
                                instructions, BlockLoopInstructionDTO.class, () -> ABRSharedResources.getInstance()
                                        .removeAllEntity(blocks, BlockDTO.class, () -> ABRSharedResources.getInstance()
                                                .removeEntity(
                                                        botJob,
                                                        BotJobDTO.class,
                                                        () -> new ABRAlertScene(
                                                                Alert.AlertType.INFORMATION,
                                                                "Bot Job deleted",
                                                                "The bot job has been deleted successfully",
                                                                ButtonType.OK)))));
    }
}
