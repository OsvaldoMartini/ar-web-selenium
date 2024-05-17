package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.scene.ABRMoveBlockScene;
import com.allinweb.ch.component.scene.ABRSaveBlockScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class BlockListCell extends ListCell<BlockDTO> {

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    @Override
    protected void updateItem(BlockDTO item, boolean empty) {
        super.updateItem(item, empty);

        Node graphic = null;
        if (!empty && item != null && item.getBotJob() != null) {
            try {

                BooleanProperty editingElement = new SimpleBooleanProperty(false);
                Button saveButton = componentBuilder.buildButton("  Save  ", ABRConstants.SPACE_M, Insets.EMPTY);

                saveButton.setAlignment(Pos.CENTER_LEFT);
                TextField nameField = new TextField(item.getName());
                Label nameLabel = new Label();
                nameLabel.setTextFill(Color.WHITE);
                nameLabel.visibleProperty().bind(editingElement.not());
                nameField.visibleProperty().bind(editingElement);
                saveButton.visibleProperty().bind(editingElement);

                StackPane nameGroup = new StackPane(new Node[] {nameLabel, nameField});
                nameGroup.setPadding(new Insets(1.0D));
                StackPane.setAlignment(nameLabel, Pos.CENTER_LEFT);
                StackPane.setMargin(nameLabel, new Insets(0.0D, 5.0D, 0.0D, 5.0D));
                HBox nameFieldsGroup = new HBox(new Node[] {nameGroup, saveButton});
                HBox.setMargin(saveButton, new Insets(ABRConstants.SPACE_XXS));
                nameFieldsGroup.setSpacing(ABRConstants.SPACE_XXS);
                Button moveBlockButton = this.componentBuilder.buildButton(
                        "", ABRConstants.SPACE_ZERO, ABRConstants.ICON_MOVE, ABRConstants.SPACE_M, Insets.EMPTY);
                Button deleteBlockButton = this.componentBuilder.buildButton(
                        "", ABRConstants.SPACE_ZERO, ABRConstants.ICON_BIN, ABRConstants.SPACE_M, Insets.EMPTY);
                Button saveBlockButton = this.componentBuilder.buildButton(
                        "", ABRConstants.SPACE_ZERO, ABRConstants.ICON_SAVE, ABRConstants.SPACE_M, Insets.EMPTY);
                Button editBlockButton = this.componentBuilder.buildButton(
                        "", ABRConstants.SPACE_ZERO, ABRConstants.ICON_EDIT, ABRConstants.SPACE_M, Insets.EMPTY);
                Button removeBlockButton = this.componentBuilder.buildButton(
                        "", ABRConstants.SPACE_ZERO, ABRConstants.ICON_CROSS, ABRConstants.SPACE_M, Insets.EMPTY);

                HBox actionPanel;
                if (item.getId() != getListView().getItems().get(0).getId()) {
                    int currentPosition = this.getListView().getItems().indexOf(item) + 1;
                    if (item.getName() != null) {
                        String var10001 = Integer.toString(currentPosition);
                        nameLabel.setText("#" + var10001 + " " + item.getName());
                    } else {
                        nameLabel.setText("#" + Integer.toString(currentPosition) + " ");
                    }

                    actionPanel = new HBox(new Node[] {
                        moveBlockButton, deleteBlockButton, saveBlockButton, editBlockButton, removeBlockButton
                    });
                    AnchorPane.setRightAnchor(actionPanel, ABRConstants.SPACE_M);
                } else {
                    nameLabel.setText("#" + item.getName());
                    actionPanel = new HBox(new Node[] {saveBlockButton});
                    AnchorPane.setRightAnchor(actionPanel, ABRConstants.SPACE_M + 100.0D);
                }

                actionPanel.setSpacing(ABRConstants.SPACE_XS);
                AnchorPane.setTopAnchor(actionPanel, ABRConstants.SPACE_XXS);
                AnchorPane.setBottomAnchor(actionPanel, ABRConstants.SPACE_XXS);
                AnchorPane graphicRepresentation = new AnchorPane(new Node[] {nameFieldsGroup, actionPanel});
                graphicRepresentation.setBackground(Background.fill(Color.ROYALBLUE));
                ObservableList<BlockLoopInstructionDTO> instructionObservableList = ABRSharedResources.getInstance()
                        .getEntityList(
                                BlockLoopInstructionDTO.class,
                                Comparator.comparingInt(BlockLoopInstructionDTO::getInstructionOrderNumber),
                                instruction -> instruction.getBlock().getId() == item.getId());
                ListView<BlockLoopInstructionDTO> instructionList = new ListView<>(instructionObservableList);
                instructionList.setFixedCellSize(ABRConstants.SPACE_L);

                instructionList.setCellFactory(new ABRCellFactory<>(InstructionListCell.class)::call);
                instructionList.setBackground((Background) null);
                instructionList.setBorder((Border) null);
                instructionList.setMaxHeight(Double.MAX_VALUE);

                VBox uiBlock = new VBox(new Node[] {graphicRepresentation, instructionList});
                uiBlock.setFillWidth(true);
                uiBlock.setMaxHeight(Double.MAX_VALUE);
                uiBlock.setAlignment(Pos.TOP_CENTER);

                VBox.setVgrow(instructionList, Priority.ALWAYS);
                int size = item.getBlockLoopInstructions().size();

                setMaxHeight(Double.MAX_VALUE);
                this.setPrefHeight(ABRConstants.SPACE_L * (double) size + 35.0D);
                deleteBlockButton.setOnAction((e) -> {
                    ABRSharedResources.getInstance().removeEntity(item, BlockDTO.class);
                });
                editBlockButton.setOnAction((e) -> {
                    editingElement.setValue(!editingElement.getValue());
                });
                moveBlockButton.setOnAction((e) -> {
                    (new ABRMoveBlockScene(item)).show();
                });
                saveButton.setOnAction((e) -> {
                    editingElement.setValue(false);
                    nameLabel.setText(nameField.getText());
                    ABRLogger.getInstance(BlockListCell.class).info("saving block with id: " + item.getId());
                    if (item != null && item.getId() != 0) {
                        item.setName(nameLabel.getText());
                        ABRSharedResources.getInstance().updateEntity(item, BlockDTO.class);
                        getListView().refresh();
                    }
                });
                saveBlockButton.setOnAction((e) -> {
                    SavedBlocksDTO savedBlocksDTO = SavedBlocksDTO.createSavedBlocksDTOFromBlocksDTO(item);
                    (new ABRSaveBlockScene(savedBlocksDTO, item)).show();
                });
                removeBlockButton.setOnAction((e) -> {
                    FilteredList<BlockDTO> list = ABRSharedResources.getInstance()
                            .getEntityList(BlockDTO.class)
                            .filtered((blockx) -> {
                                return blockx.getBotJob().getId()
                                                == item.getBotJob().getId()
                                        && blockx.getBlockOrderNumber() <= item.getBlockOrderNumber()
                                        && blockx.getId() != item.getId();
                            });
                    if (list.size() != 0) {
                        BlockDTO block = (BlockDTO)
                                Collections.max(list, Comparator.comparingInt(BlockDTO::getBlockOrderNumber));
                        if (block != null) {
                            List<BlockLoopInstructionDTO> blockLoopInstructions =
                                    new ArrayList<BlockLoopInstructionDTO>();
                            blockLoopInstructions.addAll(block.getBlockLoopInstructions());
                            blockLoopInstructions.addAll(item.getBlockLoopInstructions());
                            block.setBlockLoopInstructions(blockLoopInstructions);
                            ABRSharedResources.getInstance().updateEntity(block, BlockDTO.class, () -> {
                                ABRSharedResources.getInstance().refreshEntity(item, BlockDTO.class, () -> {
                                    ABRSharedResources.getInstance().removeEntity(item, BlockDTO.class, () -> {
                                        ABRSharedResources.getInstance().refreshEntity(block, BlockDTO.class);
                                    });
                                });
                            });
                        }
                    }
                });

                VBox.setVgrow(uiBlock, Priority.ALWAYS);
                graphic = uiBlock;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        Node finalGraphicNode = graphic;
        Platform.runLater(() -> {
            this.setGraphic(finalGraphicNode);
        });
    }
}
