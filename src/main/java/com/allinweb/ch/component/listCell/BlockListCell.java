// package com.allinweb.ch.component.listCell;
//
// import com.allinweb.ch.component.scene.ARMoveBlockScene;
// import com.allinweb.ch.component.scene.ARSaveComponentScene;
// import com.allinweb.ch.control.ARComponentBuilder;
// import com.allinweb.ch.core.ARSharedResources;
// import com.allinweb.ch.facade.PerformActions;
// import com.allinweb.ch.persistence.BlockDTO;
// import com.allinweb.ch.persistence.ComponentBlockDTO;
// import com.allinweb.ch.persistence.InstructionLoadDTO;
// import com.allinweb.ch.util.ARConstants;
// import com.allinweb.ch.util.ARLogger;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.Comparator;
// import java.util.List;
// import javafx.application.Platform;
// import javafx.beans.property.BooleanProperty;
// import javafx.beans.property.SimpleBooleanProperty;
// import javafx.collections.ObservableList;
// import javafx.collections.transformation.FilteredList;
// import javafx.geometry.Insets;
// import javafx.geometry.Pos;
// import javafx.scene.Node;
// import javafx.scene.control.Button;
// import javafx.scene.control.Label;
// import javafx.scene.control.ListCell;
// import javafx.scene.control.ListView;
// import javafx.scene.control.TextField;
// import javafx.scene.layout.AnchorPane;
// import javafx.scene.layout.Background;
// import javafx.scene.layout.Border;
// import javafx.scene.layout.HBox;
// import javafx.scene.layout.Priority;
// import javafx.scene.layout.StackPane;
// import javafx.scene.layout.VBox;
// import javafx.scene.paint.Color;
//
// public class BlockListCell extends ListCell<BlockDTO> {
//
//    private final ARComponentBuilder componentBuilder = new ARComponentBuilder();
//
//    private static final PerformActions performAction;
////    private static final PerformDBSavedBlock performDBSavedBlock;
//    // Static block to initialize
//    static {
//        performAction = PerformActions.getInstance();
////        performDBSavedBlock = PerformDBSavedBlock.getInstance();
//    }
//
//    @Override
//    protected void updateItem(BlockDTO item, boolean empty) {
//        super.updateItem(item, empty);
//
//        Node graphic = null;
//        if (!empty && item != null && item.getBotJobId() != null) {
//            try {
//
//                BooleanProperty editingElement = new SimpleBooleanProperty(false);
//                Button saveButton = componentBuilder.buildButton("  Save  ", ARConstants.SPACE_M, Insets.EMPTY);
//
//                saveButton.setAlignment(Pos.CENTER_LEFT);
//                TextField nameField = new TextField(item.getName());
//                Label nameLabel = new Label();
//                nameLabel.setTextFill(Color.WHITE);
//                nameLabel.visibleProperty().bind(editingElement.not());
//                nameField.visibleProperty().bind(editingElement);
//                saveButton.visibleProperty().bind(editingElement);
//
//                StackPane nameGroup = new StackPane(new Node[] {nameLabel, nameField});
//                nameGroup.setPadding(new Insets(1.0D));
//                StackPane.setAlignment(nameLabel, Pos.CENTER_LEFT);
//                StackPane.setMargin(nameLabel, new Insets(0.0D, 5.0D, 0.0D, 5.0D));
//                HBox nameFieldsGroup = new HBox(new Node[] {nameGroup, saveButton});
//                HBox.setMargin(saveButton, new Insets(ARConstants.SPACE_XXS));
//                nameFieldsGroup.setSpacing(ARConstants.SPACE_XXS);
//                Button moveBlockButton = this.componentBuilder.buildButton(
//                        "", ARConstants.SPACE_ZERO, ARConstants.ICON_MOVE, ARConstants.SPACE_M, Insets.EMPTY);
//                Button deleteBlockButton = this.componentBuilder.buildButton(
//                        "", ARConstants.SPACE_ZERO, ARConstants.ICON_BIN, ARConstants.SPACE_M, Insets.EMPTY);
//                Button saveBlockButton = this.componentBuilder.buildButton(
//                        "", ARConstants.SPACE_ZERO, ARConstants.ICON_SAVE, ARConstants.SPACE_M, Insets.EMPTY);
//                Button editBlockButton = this.componentBuilder.buildButton(
//                        "", ARConstants.SPACE_ZERO, ARConstants.ICON_EDIT, ARConstants.SPACE_M, Insets.EMPTY);
//                Button removeBlockButton = this.componentBuilder.buildButton(
//                        "", ARConstants.SPACE_ZERO, ARConstants.ICON_CROSS, ARConstants.SPACE_M, Insets.EMPTY);
//
//                HBox actionPanel;
//                if (item.getId() != getListView().getItems().get(0).getId()) {
//                    int currentPosition = this.getListView().getItems().indexOf(item) + 1;
//                    if (item.getName() != null) {
//                        String var10001 = Integer.toString(currentPosition);
//                        nameLabel.setText("#" + var10001 + " " + item.getName());
//                    } else {
//                        nameLabel.setText("#" + Integer.toString(currentPosition) + " ");
//                    }
//
//                    actionPanel = new HBox(new Node[] {
//                        moveBlockButton, deleteBlockButton, saveBlockButton, editBlockButton, removeBlockButton
//                    });
//                    AnchorPane.setRightAnchor(actionPanel, ARConstants.SPACE_M);
//                } else {
//                    nameLabel.setText("#" + item.getName());
//                    actionPanel = new HBox(new Node[] {saveBlockButton});
//                    AnchorPane.setRightAnchor(actionPanel, ARConstants.SPACE_M + 100.0D);
//                }
//
//                actionPanel.setSpacing(ARConstants.SPACE_XS);
//                AnchorPane.setTopAnchor(actionPanel, ARConstants.SPACE_XXS);
//                AnchorPane.setBottomAnchor(actionPanel, ARConstants.SPACE_XXS);
//                AnchorPane graphicRepresentation = new AnchorPane(new Node[] {nameFieldsGroup, actionPanel});
//                graphicRepresentation.setBackground(Background.fill(Color.ROYALBLUE));
//                ObservableList<InstructionLoadDTO> instructionObservableList = ARSharedResources.getInstance()
//                        .getEntityList(
//                                InstructionLoadDTO.class,
//                                Comparator.comparingInt(InstructionLoadDTO::getInstructionOrderNumber),
//                                instruction -> instruction.getBlockId().equals(item.getId()));
//                ListView<InstructionLoadDTO> instructionList = new ListView<>(instructionObservableList);
//                instructionList.setFixedCellSize(ARConstants.SPACE_L);
//
//                instructionList.setCellFactory(new ARCellFactory<>(InstructionListCell.class)::call);
//                instructionList.setBackground((Background) null);
//                instructionList.setBorder((Border) null);
//                instructionList.setMaxHeight(Double.MAX_VALUE);
//
//                VBox uiBlock = new VBox(new Node[] {graphicRepresentation, instructionList});
//                uiBlock.setFillWidth(true);
//                uiBlock.setMaxHeight(Double.MAX_VALUE);
//                uiBlock.setAlignment(Pos.TOP_CENTER);
//
//                VBox.setVgrow(instructionList, Priority.ALWAYS);
//                int size = item.getBlockLoopInstructionLoadDTOS().size();
//
//                setMaxHeight(Double.MAX_VALUE);
//                this.setPrefHeight(ARConstants.SPACE_L * (double) size + 35.0D);
//                deleteBlockButton.setOnAction((e) -> {
//                    ARSharedResources.getInstance().removeEntity(item, BlockDTO.class);
//                });
//                editBlockButton.setOnAction((e) -> {
//                    editingElement.setValue(!editingElement.getValue());
//                });
//                moveBlockButton.setOnAction((e) -> {
//                    (new ARMoveBlockScene(item)).show();
//                });
//                saveButton.setOnAction((e) -> {
//                    editingElement.setValue(false);
//                    nameLabel.setText(nameField.getText());
//                    ARLogger.getInstance(BlockListCell.class).info("saving block with id: " + item.getId());
//                    if (item != null && item.getId() != 0) {
//                        item.setName(nameLabel.getText());
//                        ARSharedResources.getInstance().updateEntity(item, BlockDTO.class);
//                        getListView().refresh();
//                    }
//                });
//                saveBlockButton.setOnAction((e) -> {
//                    ComponentBlockDTO componentBlockDTO = performDBSavedBlock.createSavedBlocksDTOFromBlocksDTO(item);
//                    //                    (new ARSaveBlockScene(savedBlocksDTO, item)).show();
//                    // Ensure JavaFX UI updates are done on the JavaFX Application Thread
//                    Platform.runLater(() -> {
//                        ARSaveComponentScene newSaveBlockScene =
//                                new ARSaveComponentScene(componentBlockDTO, item, null);
//                        newSaveBlockScene.showModal();
//                    });
//                });
//                removeBlockButton.setOnAction((e) -> {
//                    FilteredList<BlockDTO> list = ARSharedResources.getInstance()
//                            .getEntityList(BlockDTO.class)
//                            .filtered((blockx) -> {
//                                return blockx.getBotJobDTO().getId()
//                                                == item.getBotJobDTO().getId()
//                                        && blockx.getBlockOrderNumber() <= item.getBlockOrderNumber()
//                                        && blockx.getId() != item.getId();
//                            });
//                    if (list.size() != 0) {
//                        BlockDTO block = (BlockDTO)
//                                Collections.max(list, Comparator.comparingInt(BlockDTO::getBlockOrderNumber));
//                        if (block != null) {
//                            List<InstructionLoadDTO> blockLoopInstructions = new ArrayList<InstructionLoadDTO>();
//                            blockLoopInstructions.addAll(block.getBlockLoopInstructionLoadDTOS());
//                            blockLoopInstructions.addAll(item.getBlockLoopInstructionLoadDTOS());
//                            block.setBlockLoopInstructionLoadDTOS(blockLoopInstructions);
//                            ARSharedResources.getInstance().updateEntity(block, BlockDTO.class, () -> {
//                                ARSharedResources.getInstance().refreshEntity(item, BlockDTO.class, () -> {
//                                    ARSharedResources.getInstance().removeEntity(item, BlockDTO.class, () -> {
//                                        ARSharedResources.getInstance().refreshEntity(block, BlockDTO.class);
//                                    });
//                                });
//                            });
//                        }
//                    }
//                });
//
//                VBox.setVgrow(uiBlock, Priority.ALWAYS);
//                graphic = uiBlock;
//            } catch (Exception e) {
//                System.out.println(e.getMessage());
//            }
//        }
//        Node finalGraphicNode = graphic;
//        Platform.runLater(() -> {
//            this.setGraphic(finalGraphicNode);
//        });
//    }
// }
