package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import java.util.LinkedList;
import java.util.Queue;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ABRSaveBlockPane extends ABRPane {
    private final ABRComponentBuilder builder = new ABRComponentBuilder();
    private SavedBlocksDTO savedBlocksDTO;
    private BlockDTO blockDTO;

    TextField nameTextField;
    TextArea descriptionTextField;

    Label warningLabel;

    private Button saveBlockButton;
    Button closeButton;

    private AnchorPane mainPane;

    public ABRSaveBlockPane(SavedBlocksDTO savedBlocksDTO, BlockDTO blockDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
        this.blockDTO = blockDTO;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        saveBlockButton = builder.buildButton(" Save Block ", ABRConstants.SPACE_L);
        closeButton = builder.buildButton(" Close ", ABRConstants.SPACE_L);

        HBox actionPanel = new HBox(saveBlockButton, closeButton);
        actionPanel.setSpacing(ABRConstants.SPACE_SM);
        actionPanel.setAlignment(Pos.CENTER);

        Label nameLabel = new Label("Name :         ");

        nameTextField = new TextField(savedBlocksDTO.getName());
        HBox nameHBox = new HBox(nameLabel, nameTextField);
        HBox.setHgrow(nameTextField, Priority.ALWAYS);
        HBox.setMargin(nameLabel, new Insets(ABRConstants.SPACE_XS));

        Label descriptionLabel = new Label("Description : ");
        descriptionTextField = new TextArea(savedBlocksDTO.getDescription());

        HBox descriptionHBox = new HBox(descriptionLabel, descriptionTextField);
        HBox.setHgrow(descriptionTextField, Priority.ALWAYS);
        HBox.setMargin(descriptionLabel, new Insets(ABRConstants.SPACE_XS));

        nameTextField.setMaxHeight(ABRConstants.SPACE_XL);
        nameTextField.setPrefHeight(ABRConstants.SPACE_XL);

        descriptionTextField.setMaxHeight(100);
        descriptionTextField.setPrefHeight(100);
        descriptionLabel.setMaxWidth(Double.MAX_VALUE);

        warningLabel = new Label();
        warningLabel.setMaxWidth(Double.MAX_VALUE);
        warningLabel.setTextFill(Color.RED);
        warningLabel.setAlignment(Pos.CENTER);

        VBox separtorPanel = new VBox(nameHBox, descriptionHBox, warningLabel, actionPanel);
        VBox.setVgrow(descriptionHBox, Priority.ALWAYS);
        separtorPanel.setMaxWidth(Double.MAX_VALUE);
        separtorPanel.setSpacing(ABRConstants.SPACE_SM);

        // separtorPanel.setPrefWidth(400);

        AnchorPane.setTopAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(separtorPanel, ABRConstants.SPACE_M);

        mainPane = new AnchorPane(separtorPanel);
    }

    @Override
    public void initUIBehaviour() {

        saveBlockButton.setOnMouseClicked(e -> {
            if (nameTextField.getText() != null
                    && !nameTextField.getText().trim().isEmpty()
                    && descriptionTextField.getText() != null
                    && !descriptionTextField.getText().trim().isEmpty()) {
                warningLabel.setText("");
                savedBlocksDTO.setName(nameTextField.getText());
                savedBlocksDTO.setDescription(descriptionTextField.getText());
                Queue<SavedBlockLoopInstructionDTO> savedBlockLoopInstructionList =
                        SavedBlocksDTO.createSavedBlockLoopInstructionsFromBlocksDTO(blockDTO, savedBlocksDTO);

                Queue<SavedInstructionReferenceDTO> savedReferenceQueue = new LinkedList<>();
                savedBlockLoopInstructionList.forEach(savedInstruction -> {
                    savedReferenceQueue.addAll(savedInstruction.getSavedInstructionReferenceDTOList());
                    savedInstruction.setSavedInstructionReferenceDTOList(null);
                });
                ABRSharedResources.getInstance()
                        .addEntity(savedBlocksDTO, SavedBlocksDTO.class, () -> ABRSharedResources.getInstance()
                                .addAllEntity(
                                        savedBlockLoopInstructionList,
                                        SavedBlockLoopInstructionDTO.class,
                                        () -> ABRSharedResources.getInstance()
                                                .addAllEntity(
                                                        savedReferenceQueue,
                                                        SavedInstructionReferenceDTO.class,
                                                        () -> new ABRAlertScene(
                                                                Alert.AlertType.INFORMATION,
                                                                "Block Saved",
                                                                "The block has been saved successfully",
                                                                ButtonType.OK))));
                Close();
            } else {

                warningLabel.setText("Warning: give the correct name and description");
            }
        });

        closeButton.setOnAction(e -> Close());
    }

    private void Close() {
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }
}
