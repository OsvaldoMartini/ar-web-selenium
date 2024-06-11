package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.InstructionReferenceDTO;
import com.allinweb.ch.util.ABRConstants;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ABRSaveBotJobAsPane extends ABRPane {

    private int botJobId;

    // UI

    private Label nameLabel;
    private Label descriptionLabel;

    private TextField nameField;
    private TextField descriptionField;

    private Button saveButton;

    private Pane mainPane;

    public ABRSaveBotJobAsPane(int botJobId) {
        this.botJobId = botJobId;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        nameLabel = new Label("Name:");
        descriptionLabel = new Label("Description:");
        nameField = new TextField();
        descriptionField = new TextField();

        saveButton = new Button("Save  TO DB");

        VBox group = new VBox(nameLabel, nameField, descriptionLabel, descriptionField, saveButton);
        group.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(group, ABRConstants.SPACE_M);
        AnchorPane.setBottomAnchor(group, ABRConstants.SPACE_M);
        AnchorPane.setLeftAnchor(group, ABRConstants.SPACE_M);
        AnchorPane.setRightAnchor(group, ABRConstants.SPACE_M);

        mainPane = new AnchorPane(group);
    }

    @Override
    public void initUIBehaviour() {

        BotJobDTO botJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId);

        saveButton.setOnMouseClicked(e -> {
            List<BotJobDTO> botJobList = ABRSharedResources.getInstance()
                    .getEntityList(
                            BotJobDTO.class, botJobDTO -> botJobDTO.getName().equals(nameField.getText()));
            boolean executeCopy = true;
            if (botJobList.size() != 0) {
                Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Bot job already exists with that name. Overwrite it?",
                        ButtonType.YES,
                        ButtonType.NO);
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    clearBotJob(botJobList.get(0));
                } else {
                    executeCopy = false;
                }
            }
            if (executeCopy) {
                BotJobDTO newBotJob = new BotJobDTO(botJob.getHomeBanking());
                newBotJob.setName(nameField.getText());
                newBotJob.setDescription(descriptionField.getText());
                ABRSharedResources.getInstance()
                        .addEntity(newBotJob, BotJobDTO.class, () -> copyBotJob(botJob, newBotJob));
            }
            Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
            stage.close();
        });
    }

    private void clearBotJob(BotJobDTO botJob) {
        Queue<BlockDTO> blocks = new LinkedList<>(botJob.getBlocks());
        ABRSharedResources.getInstance().removeAllEntity(blocks, BlockDTO.class);
    }

    private void copyBotJob(BotJobDTO from, BotJobDTO to) {
        Queue<BlockDTO> blocks = new LinkedList<>();
        Queue<BlockLoopInstructionDTO> instructions = new LinkedList<>();
        Queue<InstructionReferenceDTO> references = new LinkedList<>();
        from.getBlocks().forEach(block -> {
            BlockDTO newBlock = new BlockDTO(to);
            newBlock.setName(block.getName());
            newBlock.setDescription(block.getDescription());
            block.getBlockLoopInstructions().forEach(instruction -> {
                BlockLoopInstructionDTO newInstruction = new BlockLoopInstructionDTO(newBlock);
                newInstruction.setActionCustomMaxWaitSec(instruction.getActionCustomMaxWaitSec());
                newInstruction.setActions(instruction.getActions());
                newInstruction.setDefaultValue(instruction.getDefaultValue());
                newInstruction.setDescription(instruction.getDescription());
                newInstruction.setEncrypted(instruction.isEncrypted());
                newInstruction.setExportToABR(instruction.getExportToABR());
                newInstruction.setInstructionOrderNumber(instruction.getInstructionOrderNumber());
                newInstruction.setName(instruction.getName());
                newInstruction.setOnHoldSeconds(instruction.getOnHoldSeconds());
                newInstruction.setOptional(instruction.isOptional());
                newInstruction.setPath(instruction.getPath());

                instruction.getInstructionReferenceDTOList().forEach(reference -> {
                    InstructionReferenceDTO newReference = new InstructionReferenceDTO(newInstruction);
                    newReference.setReferenceType(reference.getReferenceType());
                    newReference.setValue(reference.getValue());
                    references.add(newReference);
                });

                instructions.add(newInstruction);
            });
            blocks.add(newBlock);
        });
        ABRSharedResources.getInstance().addAllEntity(blocks, BlockDTO.class, () -> ABRSharedResources.getInstance()
                .addAllEntity(instructions, BlockLoopInstructionDTO.class, () -> ABRSharedResources.getInstance()
                        .addAllEntity(
                                references,
                                InstructionReferenceDTO.class,
                                () -> new ABRAlertScene(
                                        Alert.AlertType.INFORMATION,
                                        "Bot Job Saved",
                                        "The bot job has been saved successfully",
                                        ButtonType.OK))));
    }
}
