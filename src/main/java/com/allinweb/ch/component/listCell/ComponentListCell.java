package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.pane.ABRViewBotJobPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import java.util.LinkedList;
import java.util.Queue;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

public class ComponentListCell extends ListCell<SavedBlocksDTO> {
    private static final ABRComponentBuilder builder = new ABRComponentBuilder();

    protected void updateItem(SavedBlocksDTO savedBlocksDTO, boolean empty) {
        super.updateItem(savedBlocksDTO, empty);
        // System.out.println(savedBlocksDTO.getName());
        Node graphic = null;
        if (!empty && savedBlocksDTO != null) {
            Label nameLabel = new Label(savedBlocksDTO.getName());
            nameLabel.setFont(Font.font(null, FontWeight.BOLD, FontPosture.REGULAR, ABRConstants.SPACE_SM + 2));
            nameLabel.setWrapText(true);

            Label nameLabel1 = new Label(savedBlocksDTO.getDescription());
            nameLabel1.setPrefWidth(150);
            nameLabel1.setWrapText(true);

            VBox nameVBox = new VBox(nameLabel, nameLabel1);
            nameVBox.setMaxWidth(Double.MAX_VALUE);
            nameVBox.setSpacing(ABRConstants.SPACE_XS);

            Button appendButton = builder.buildButton(
                    "",
                    ABRConstants.SPACE_XXS,
                    ABRConstants.ICON_ARROWLEFT,
                    ABRConstants.SPACE_M,
                    Insets.EMPTY,
                    Background.fill(Color.TRANSPARENT));
            Button detailButton = builder.buildButton(
                    "",
                    ABRConstants.SPACE_XXS,
                    ABRConstants.ICON_DOCS,
                    ABRConstants.SPACE_M,
                    Insets.EMPTY,
                    Background.fill(Color.TRANSPARENT));
            Button deleteButton = builder.buildButton(
                    "",
                    ABRConstants.SPACE_XXS,
                    ABRConstants.ICON_BIN,
                    ABRConstants.SPACE_M,
                    Insets.EMPTY,
                    Background.fill(Color.TRANSPARENT));

            HBox actionPaneBox = new HBox(appendButton, detailButton, deleteButton);
            actionPaneBox.setAlignment(Pos.TOP_RIGHT);
            actionPaneBox.setSpacing(ABRConstants.SPACE_XXS);
            HBox.setHgrow(nameVBox, Priority.ALWAYS);

            StackPane itemPaneBox = new StackPane(nameVBox, actionPaneBox);
            // itemPaneBox.setBackground(Background.fill(Color.RED));
            itemPaneBox.setMaxWidth(Double.MAX_VALUE);
            StackPane.setAlignment(nameVBox, Pos.CENTER_LEFT);
            StackPane.setAlignment(actionPaneBox, Pos.TOP_RIGHT);

            AnchorPane itemPane = new AnchorPane(itemPaneBox);
            // itemPaneBox.setBackground(Background.fill(Color.BLUE));

            //  AnchorPane.setBottomAnchor(actionPaneBox, ABRConstants.SPACE_XS);
            // AnchorPane.setTopAnchor(actionPaneBox, ABRConstants.SPACE_XS);
            // AnchorPane.setRightAnchor(actionPaneBox, ABRConstants.SPACE_XS);

            AnchorPane.setBottomAnchor(itemPaneBox, ABRConstants.SPACE_ZERO);
            AnchorPane.setTopAnchor(itemPaneBox, ABRConstants.SPACE_ZERO);
            AnchorPane.setLeftAnchor(itemPaneBox, ABRConstants.SPACE_ZERO);
            AnchorPane.setRightAnchor(itemPaneBox, ABRConstants.SPACE_ZERO);
            setPadding(new Insets(ABRConstants.SPACE_XS));
            setBorder(new Border(new BorderStroke(
                    Color.LIGHTGREY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1))));

            detailButton.setOnAction(e -> {
                new ABRComponentDetailsScene(savedBlocksDTO).show();
            });

            deleteButton.setOnAction(e -> {
                ABRSharedResources.getInstance().removeEntity(savedBlocksDTO, SavedBlocksDTO.class);
                getListView().refresh();
            });

            appendButton.setOnMouseClicked(e -> {
                ABRViewBotJobPane currentPane =
                        (ABRViewBotJobPane) getListView().getUserData();

                if (currentPane != null) {
                    BlockDTO blockDTO =
                            BlockDTO.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, currentPane.getBotJobDTO());
                    BotJobDTO botJob = ABRSharedResources.getInstance()
                            .getEntityById(
                                    BotJobDTO.class, currentPane.getBotJobDTO().getId());
                    blockDTO.setBotJob(botJob);
                    blockDTO.setBlockOrderNumber(botJob.getBlocks().size() + 1);

                    Queue<BlockLoopInstructionDTO> blockLoopInstructionDTOs =
                            BlockDTO.createBlockLoopInstructionsFromSavedBlocksDTO(savedBlocksDTO, blockDTO);
                    System.out.println(
                            savedBlocksDTO.getSavedBlockLoopInstructions().size() + " block size");
                    Queue<InstructionReferenceDTO> referenceQueue = new LinkedList<>();
                    blockLoopInstructionDTOs.forEach(instruction -> {
                        referenceQueue.addAll(instruction.getInstructionReferenceDTOList());
                        instruction.setInstructionReferenceDTOList(null);
                    });
                    ABRSharedResources.getInstance()
                            .addEntity(blockDTO, BlockDTO.class, () -> ABRSharedResources.getInstance()
                                    .addAllEntity(
                                            blockLoopInstructionDTOs,
                                            BlockLoopInstructionDTO.class,
                                            () -> ABRSharedResources.getInstance()
                                                    .addAllEntity(
                                                            referenceQueue,
                                                            InstructionReferenceDTO.class,
                                                            () -> new ABRAlertScene(
                                                                    Alert.AlertType.INFORMATION,
                                                                    "Block Added",
                                                                    "The block has been added to the botjob successfully",
                                                                    ButtonType.OK))));

                    ABRSharedResources.getInstance().addEntity(blockDTO, BlockDTO.class);
                }
            });

            // itemPane.setBorder(getBorder());
            graphic = itemPane;
            // System.out.println("graphics");
        }

        Node finalGraphic = graphic;
        Platform.runLater(() -> {
            setGraphic(finalGraphic);
        });
    }
}
