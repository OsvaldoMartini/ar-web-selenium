package com.allinweb.ch.component.listCell;

import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.persistence.SavedBlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ARConstants;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

public class SavedBlockLoopInstructionListCell extends ListCell<SavedBlockLoopInstructionDTO> {
    private final ARComponentBuilder builder = new ARComponentBuilder();

    @Override
    protected void updateItem(SavedBlockLoopInstructionDTO item, boolean empty) {
        super.updateItem(item, empty);
        Node graphic = null;

        if (!empty && item != null && item.getBlock() != null) {
            ImageView clickImage;
            ImageView insertImage;
            Label nameLabel;
            HBox elementPanel;
            HBox actionPanel;

            clickImage = builder.buildImageView(ARConstants.ICON_CLICK, ARConstants.SPACE_M);
            insertImage = builder.buildImageView(ARConstants.ICON_INSERT, ARConstants.SPACE_M);

            nameLabel = new Label(item.getName());
            nameLabel.setMaxHeight(ARConstants.SPACE_L);

            StackPane actionGroup = new StackPane(clickImage, insertImage);
            elementPanel = new HBox(actionGroup, nameLabel);
            elementPanel.setSpacing(ARConstants.SPACE_XS);

            AnchorPane.setLeftAnchor(elementPanel, ARConstants.SPACE_XS);
            AnchorPane.setTopAnchor(elementPanel, ARConstants.SPACE_XS);
            AnchorPane.setBottomAnchor(elementPanel, ARConstants.SPACE_XS);

            Button moveUpButton = builder.buildButton(
                    "", ARConstants.SPACE_L, ARConstants.ICON_UP, ARConstants.SPACE_M, Insets.EMPTY);
            Button moveDownButton = builder.buildButton(
                    "", ARConstants.SPACE_L, ARConstants.ICON_DOWN, ARConstants.SPACE_M, Insets.EMPTY);

            actionPanel = new HBox(moveUpButton, moveDownButton);
            actionPanel.setSpacing(ARConstants.SPACE_XS);
            actionPanel.setAlignment(Pos.CENTER_RIGHT);

            AnchorPane.setTopAnchor(actionPanel, ARConstants.SPACE_XS);
            AnchorPane.setBottomAnchor(actionPanel, ARConstants.SPACE_XS);
            AnchorPane.setRightAnchor(actionPanel, ARConstants.SPACE_XS);

            moveUpButton.setOnAction(e -> switchInstruction(-1, item));

            moveDownButton.setOnAction(e -> switchInstruction(1, item));
            // graphic.setUserData(getScene().getRoot().getUserData());
            graphic = new AnchorPane(elementPanel, actionPanel);
        }
        Node finalGraphic = graphic;
        String css = getClass().getResource("/listView.css").toExternalForm();

        Platform.runLater(() -> {
            getStylesheets().add(css);
            setGraphic(finalGraphic);
        });
    }

    private void switchInstruction(int directionQuantity, SavedBlockLoopInstructionDTO instruction) {
        SavedBlockLoopInstructionDTO currentInstruction =
                ARSharedResources.getInstance().getEntityById(SavedBlockLoopInstructionDTO.class, instruction.getId());
        int order = currentInstruction.getInstructionOrderNumber();

        SavedBlocksDTO block = ARSharedResources.getInstance()
                .getEntityById(
                        SavedBlocksDTO.class, currentInstruction.getBlock().getId());
        List<SavedBlockLoopInstructionDTO> instructionList = block.getSavedBlockLoopInstructions();
        SavedBlockLoopInstructionDTO instructionToChange = instructionList.stream()
                .filter(i -> i.getInstructionOrderNumber() == order + directionQuantity)
                .findFirst()
                .orElseThrow();
        currentInstruction.setInstructionOrderNumber(order + directionQuantity);
        instructionToChange.setInstructionOrderNumber(order);
        ARSharedResources.getInstance()
                .updateEntity(
                        currentInstruction, SavedBlockLoopInstructionDTO.class, () -> ARSharedResources.getInstance()
                                .updateEntity(instructionToChange, SavedBlockLoopInstructionDTO.class));
    }
}
