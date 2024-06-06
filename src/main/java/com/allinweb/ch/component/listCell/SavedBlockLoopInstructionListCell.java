package com.allinweb.ch.component.listCell;

import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.SavedBlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRConstants;
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
    private final ABRComponentBuilder builder = new ABRComponentBuilder();

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

            clickImage = builder.buildImageView(ABRConstants.ICON_CLICK, ABRConstants.SPACE_M);
            insertImage = builder.buildImageView(ABRConstants.ICON_INSERT, ABRConstants.SPACE_M);

            nameLabel = new Label(item.getName());
            nameLabel.setMaxHeight(ABRConstants.SPACE_L);

            StackPane actionGroup = new StackPane(clickImage, insertImage);
            elementPanel = new HBox(actionGroup, nameLabel);
            elementPanel.setSpacing(ABRConstants.SPACE_XS);

            AnchorPane.setLeftAnchor(elementPanel, ABRConstants.SPACE_XS);
            AnchorPane.setTopAnchor(elementPanel, ABRConstants.SPACE_XS);
            AnchorPane.setBottomAnchor(elementPanel, ABRConstants.SPACE_XS);

            Button moveUpButton = builder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_UP, ABRConstants.SPACE_M, Insets.EMPTY);
            Button moveDownButton = builder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_DOWN, ABRConstants.SPACE_M, Insets.EMPTY);

            actionPanel = new HBox(moveUpButton, moveDownButton);
            actionPanel.setSpacing(ABRConstants.SPACE_XS);
            actionPanel.setAlignment(Pos.CENTER_RIGHT);

            AnchorPane.setTopAnchor(actionPanel, ABRConstants.SPACE_XS);
            AnchorPane.setBottomAnchor(actionPanel, ABRConstants.SPACE_XS);
            AnchorPane.setRightAnchor(actionPanel, ABRConstants.SPACE_XS);

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
                ABRSharedResources.getInstance().getEntityById(SavedBlockLoopInstructionDTO.class, instruction.getId());
        int order = currentInstruction.getInstructionOrderNumber();

        SavedBlocksDTO block = ABRSharedResources.getInstance()
                .getEntityById(
                        SavedBlocksDTO.class, currentInstruction.getBlock().getId());
        List<SavedBlockLoopInstructionDTO> instructionList = block.getSavedBlockLoopInstructions();
        SavedBlockLoopInstructionDTO instructionToChange = instructionList.stream()
                .filter(i -> i.getInstructionOrderNumber() == order + directionQuantity)
                .findFirst()
                .orElseThrow();
        currentInstruction.setInstructionOrderNumber(order + directionQuantity);
        instructionToChange.setInstructionOrderNumber(order);
        ABRSharedResources.getInstance()
                .updateEntity(
                        currentInstruction, SavedBlockLoopInstructionDTO.class, () -> ABRSharedResources.getInstance()
                                .updateEntity(instructionToChange, SavedBlockLoopInstructionDTO.class));
    }
}
