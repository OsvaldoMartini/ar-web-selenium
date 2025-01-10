package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.InstructionReferenceDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ABRSaveBotJobAsPane extends ABRPane {

    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;

    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;
    // UI

    private Label nameLabel;
    private Label descriptionLabel;

    private TextField nameField;
    private TextField descriptionField;

    private Button saveButton;

    private Pane mainPane;

    public ABRSaveBotJobAsPane(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList) {
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        nameLabel = new Label("Name:");
        descriptionLabel = new Label("Description:");
        nameField = new TextField(selecBotJobDTO.getName().trim() + "Cloned");
        descriptionField = new TextField("Description");

        saveButton = new Button("Clone Bot Job");

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

        saveButton.setOnMouseClicked(e -> {
            String newBotJob = nameField.getText().trim();

            // Clean Spaces
            Platform.runLater(() -> nameField.setText(newBotJob));

            if (Strings.isNullOrEmpty(nameField.getText().trim())) {
                performMessage.errorMessage(
                        "Select a new Bot Job name", "There is NOT a name defined", null, null, null, 0);
                return;
            }

            BotJobLoadDTO existBotJob = botJobList.stream()
                    .filter(botJob -> botJob.getName().equals(newBotJob))
                    .findFirst()
                    .orElse(null); //

            if (existBotJob != null) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "The name you have entered is already in use.",
                        "Please choose a different name and try again.",
                        null,
                        null,
                        0);
                return;
            }

            ABRPropertyManager managerProps = ABRPropertyManager.getInstance();
            String excelPath = managerProps.getProperty(ABRPropertyEnum.FOLDER_PATH_EXCEL);
            String originalFilePath = excelPath + "\\" + selecBotJobDTO.getName() + ".xlsx";
            String newFilePath = excelPath + "\\" + newBotJob + ".xlsx";
            boolean excelCreation = duplicateExcelFile(originalFilePath, newFilePath);
            if (!excelCreation) {
                return;
            }

            int rowsAffected = performDataBase.duplicateBotJobById(selecBotJobDTO.getId(), newBotJob, "Description");
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
                newInstruction.setCodified(instruction.getCodified());
                newInstruction.setExportToABR(instruction.getExportToABR());
                newInstruction.setActive(instruction.getActive());
                newInstruction.setInstructionOrderNumber(instruction.getInstructionOrderNumber());
                newInstruction.setName(instruction.getName());
                newInstruction.setOnHoldSeconds(instruction.getOnHoldSeconds());
                newInstruction.setOptional(instruction.getOptional());
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

    private boolean duplicateExcelFile(String originalFilePath, String newFilePath) {
        try {
            // Load the existing Excel file
            FileInputStream fis = new FileInputStream(new File(originalFilePath));
            Workbook workbook = new XSSFWorkbook(fis);

            // Create a new file output stream for the new file (to a restricted folder)
            FileOutputStream fos = new FileOutputStream(new File(newFilePath));

            // Write the workbook data to the new file
            workbook.write(fos);

            // Close all streams
            fos.close();
            fis.close();
            return true;
        } catch (IOException e) {
            String errorMessage = "Error occurred while copying the Excel file.";
            String errorDetails = "An error occurred while attempting to clone the file.";

            // Check if the exception message contains "Access is denied"
            if (e.getMessage() != null && e.getMessage().contains("Access is denied")) {
                errorDetails =
                        "Access Denied: You do not have permission to write to this location. Please check your permissions.";
            } else if (e instanceof FileNotFoundException) {
                File file = new File(newFilePath);
                if (!file.exists()
                        && file.getParentFile() != null
                        && !file.getParentFile().canWrite()) {
                    errorDetails =
                            "You don't have permission to write in the specified folder. Please check the folder's write permissions.";
                } else {
                    errorDetails = "The specified file path is invalid or the file is already in use.";
                }
            }

            // Show the error message
            performMessage.errorMessage("Excel File Cloning Error", errorMessage, errorDetails, null, null, 0);
            return false;
        }
    }
}
