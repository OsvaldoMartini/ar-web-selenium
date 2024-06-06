package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ABRCellFactory;
import com.allinweb.ch.component.listCell.SavedBlockLoopInstructionListCell;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.SavedBlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRConstants;
import java.util.Comparator;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ABRComponentDetailsPane extends ABRPane {

    private final ABRComponentBuilder builder = new ABRComponentBuilder();
    private SavedBlocksDTO savedBlocksDTO;

    private Button saveBlockButton;
    Button closeButton;

    private AnchorPane mainPane;

    public ABRComponentDetailsPane(SavedBlocksDTO savedBlocksDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
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

        TextField nameTextField = new TextField(savedBlocksDTO.getName());
        nameTextField.setMaxWidth(Double.MAX_VALUE);

        VBox nameVBox = new VBox(nameLabel, nameTextField);
        nameVBox.setSpacing(ABRConstants.SPACE_XS);

        Label descriptionLabel = new Label("Description : ");
        TextArea descriptionTextField = new TextArea(savedBlocksDTO.getDescription());

        descriptionTextField.setMaxWidth(Double.MAX_VALUE);

        VBox descriptionVBox = new VBox(descriptionLabel, descriptionTextField);
        descriptionVBox.setSpacing(ABRConstants.SPACE_XS);

        nameTextField.setMaxHeight(ABRConstants.SPACE_XL);
        nameTextField.setPrefHeight(ABRConstants.SPACE_XL);

        descriptionTextField.setMaxHeight(100);
        descriptionTextField.setPrefHeight(100);
        VBox.setVgrow(descriptionTextField, Priority.ALWAYS);

        ObservableList<SavedBlockLoopInstructionDTO> instructionObservableList = ABRSharedResources.getInstance()
                .getEntityList(
                        SavedBlockLoopInstructionDTO.class,
                        Comparator.comparingInt(SavedBlockLoopInstructionDTO::getInstructionOrderNumber),
                        (instruction) -> instruction.getBlock().getId() == savedBlocksDTO.getId());

        ListView<SavedBlockLoopInstructionDTO> instructionList = new ListView<>(instructionObservableList);
        instructionList.setFixedCellSize(ABRConstants.SPACE_L);
        instructionList.setCellFactory(new ABRCellFactory<>(SavedBlockLoopInstructionListCell.class)::call);
        instructionList.setBackground(null);
        instructionList.setBorder(null);

        Label headerLabel = new Label("Steps");
        headerLabel.setTextFill(Color.WHITE);
        headerLabel.setBackground(Background.fill(Color.ROYALBLUE));
        headerLabel.setMaxWidth(Double.MAX_VALUE);
        headerLabel.setPadding(new Insets(ABRConstants.SPACE_XXS));

        VBox headeVBox = new VBox(headerLabel, instructionList);

        VBox separtorPanel = new VBox(nameVBox, descriptionVBox, headeVBox, actionPanel);
        VBox.setVgrow(descriptionVBox, Priority.ALWAYS);
        separtorPanel.setMaxWidth(Double.MAX_VALUE);
        separtorPanel.setSpacing(ABRConstants.SPACE_SM);

        // separtorPanel.setPrefWidth(400);

        AnchorPane.setTopAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(separtorPanel, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(separtorPanel, ABRConstants.SPACE_M);

        saveBlockButton.setOnAction(e -> {
            if (nameTextField.getText() != "" && descriptionTextField.getText() != "") {
                savedBlocksDTO.setName(nameTextField.getText());
                savedBlocksDTO.setDescription(descriptionTextField.getText());

                ABRSharedResources.getInstance().updateEntity(savedBlocksDTO, SavedBlocksDTO.class, () -> {
                    Close();
                });
            }
        });

        mainPane = new AnchorPane(separtorPanel);
    }

    @Override
    public void initUIBehaviour() {

        closeButton.setOnMouseClicked(e -> Close());
    }

    private void Close() {
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }
}
