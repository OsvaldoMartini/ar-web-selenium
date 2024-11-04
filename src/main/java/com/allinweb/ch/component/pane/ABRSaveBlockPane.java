package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ABRSaveBlockPane extends ABRPane {

    private final ABRComponentBuilder builder = new ABRComponentBuilder();
    private SavedBlocksDTO savedBlocksDTO;
    private BlockDTO blockDTO;
    private BotJobDTO botJobDTO;

    private List<BlockLoadDTO> savedBlockLoadList = new ArrayList<>();
    private List<SavedBlockLoopInstructionDTO> originalLoopInstruction;
    private List<SavedInstructionReferenceDTO> originalReferences;

    private static final PerformActions performAction;
    // Static block to initialize
    static {
        performAction = PerformActions.getInstance();
    }

    TextField nameTextField;
    TextArea descriptionTextField;

    Text regularText;
    Text variableText1;
    Text variableText2;

    Label warningLabel;

    private Button saveBlockButton;
    Button closeButton;

    private AnchorPane mainPane;

    public ABRSaveBlockPane(SavedBlocksDTO savedBlocksDTO, BlockDTO blockDTO, BotJobDTO botJobDTO) {
        this.savedBlocksDTO = savedBlocksDTO;
        this.blockDTO = blockDTO;
        this.botJobDTO = botJobDTO;
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

        regularText = new Text("Excluded Special Operations: ");
        variableText1 = new Text("Set Value" + " / " + "Get Value");
        variableText2 = new Text("Check Value" + " / " + "Excel Save");
        variableText1.setFill(Color.BLUE);
        variableText2.setFill(Color.BLUE);

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
            //            ABRSharedResources.getInstance().cacheEntitiesFromDB();

            if (nameTextField.getText() != null
                    && !nameTextField.getText().trim().isEmpty()
                    && descriptionTextField.getText() != null
                    && !descriptionTextField.getText().trim().isEmpty()) {
                try {
                    // Ensure UI update happens on the JavaFX Application Thread
                    warningMSG("");

                    savedBlocksDTO.setName(nameTextField.getText());
                    savedBlocksDTO.setDescription(descriptionTextField.getText());
                    savedBlocksDTO.setBotJob(this.botJobDTO);

                    loadSavedBlocksForBotJob(this.botJobDTO.getId());

                    originalLoopInstruction =
                            performAction.createSavedBlockLoopInstructionsFromBlocksDTO(this.blockDTO, savedBlocksDTO);

                    // Debugging: Ensure originalLoopInstruction has the right data
                    ABRLogger.getInstance(ABRSaveBlockPane.class)
                            .fine("originalLoopInstruction Size: " + originalLoopInstruction.size());

                    boolean existName = savedBlockLoadList.stream().anyMatch(block -> block.getName()
                            .equalsIgnoreCase(nameTextField.getText().trim()));

                    if (existName) {
                        performAction.showAlert(
                                Alert.AlertType.ERROR,
                                "Name Already Taken!",
                                "Change the Component Name",
                                "The Name: \"" + nameTextField.getText().trim() + "\"\nHas been take!");
                        warningMSG("The Name: \"" + nameTextField.getText().trim() + "\" Has been take!");
                        return;
                    }

                    // Debugging: Print statements to track data
                    ABRLogger.getInstance(ABRSaveBlockPane.class)
                            .fine("Saving Component Block: " + savedBlocksDTO.getName());

                    // Here Always Creating a New Component Block
                    BlockDTO blockDTO = performAction.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, this.botJobDTO);
                    blockDTO.setTypeId(1);
                    blockDTO.setBotJob(botJobDTO);
                    blockDTO.setName(savedBlocksDTO.getName());
                    blockDTO.setDescription(savedBlocksDTO.getDescription());
                    int savedCurrentBlockId = createSavedBlock(blockDTO);

                    if (savedCurrentBlockId > 0) {
                        ABRSharedResources.getInstance().cacheEntitiesFromDB();

                        ABRLogger.getInstance(Thread.class)
                                .info(String.format(
                                        "Created a new Block id %d for bot job Id %d",
                                        savedCurrentBlockId, this.botJobDTO.getId()));
                    } else {
                        performAction.showAlert(
                                Alert.AlertType.ERROR,
                                "Error Creating new Block",
                                "Verify the Bot Job Name if have any",
                                "Check if you already have a Bot Job Created!");

                        ABRLogger.getInstance(Thread.class)
                                .severe(String.format(
                                        "Error Creating a new Block for bot job Id %d\nCheck if you already have a Bot Job Created!",
                                        this.botJobDTO.getId()));
                        warningMSG(
                                String.format("Error Creating a new Block for bot job Id %d!", this.botJobDTO.getId()));

                        return;
                    }

                    boolean savedInstStatus = false;
                    for (int j = 0; j < originalLoopInstruction.size(); j++) {
                        SavedBlockLoopInstructionDTO task = originalLoopInstruction.get(j);
                        int newId = preFillSavedInstruction(
                                task.getName(),
                                task.getDescription(),
                                task.getActions(),
                                task.getOperation(),
                                task.getOnHoldSeconds(),
                                task.getVariableId(),
                                task.getInstructionOrderNumber(),
                                task.getExportToABR(),
                                task.getPath(),
                                savedCurrentBlockId);

                        task.setId(newId);

                        if (newId < 0) {
                            savedInstStatus = false;

                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Error Add New \"Component\" Instruction",
                                    "Not possible to insert new Operation",
                                    String.format("\"Component\" Instruction \"%s\"\nCannot be saved", task.getName()));

                            warningMSG(String.format(
                                    "Error Creating a Instructions %s! - Actions: %s",
                                    task.getName(), task.getActions()));
                        } else {
                            savedInstStatus = true;
                        }

                        if (!savedInstStatus) {
                            break;
                        }
                    }

                    if (savedInstStatus) {
                        ABRSharedResources.getInstance().cacheEntitiesFromDB();
                    } else {
                        return;
                    }

                    try {

                        // Build Saved Components References
                        originalReferences = new ArrayList<>();
                        originalLoopInstruction.forEach(savedInstruction -> {
                            originalReferences.addAll(savedInstruction.getSavedInstructionReferenceDTOList());
                        });

                        if (savedInstStatus && originalReferences.size() > 0) {
                            ABRLogger.getInstance(ABRSaveBlockPane.class)
                                    .fine("originalReferences Size: " + originalReferences.size());

                            boolean success = false;
                            for (SavedInstructionReferenceDTO reference : originalReferences) {

                                SavedBlockLoopInstructionDTO instructionDTO =
                                        reference.getSavedBlockLoopInstructionDTO();
                                if (instructionDTO == null) {
                                    ABRLogger.getInstance(ABRViewBotJobPane.class)
                                            .warning("SavedBlockLoopInstructionDTO is null for reference: "
                                                    + reference.getReferenceType());
                                    continue;
                                }

                                success = insertComponentReferences(reference, instructionDTO.getId());
                                if (!success) {
                                    break;
                                }
                            }
                            final boolean successFinal = success;

                            // Create individual text elements with the necessary styling
                            Text regularTextStyled = new Text(regularText.getText());
                            regularTextStyled.setStyle("-fx-font-size: 18px; -fx-fill: black;");

                            Text variableText1Styled = new Text(variableText1.getText());
                            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

                            Text variableText2Styled = new Text(variableText2.getText());
                            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");

                            // Create an VBox to hold the individual text elements
                            VBox combinedTextContainer = new VBox();
                            combinedTextContainer.setSpacing(1);

                            combinedTextContainer
                                    .getChildren()
                                    .addAll(regularTextStyled, variableText1Styled, variableText2Styled);

                            Platform.runLater(() -> {
                                if (successFinal) {

                                    // Create Text for the variable part and set the color to red
                                    performAction.showAlertCombinedVBOX(
                                            Alert.AlertType.INFORMATION,
                                            "Created Web Component",
                                            String.format(
                                                    "Created Web Component:\n" + "Added Block Name: %s"
                                                            + "" + "\nAdded %d Instructions"
                                                            + "\nAdded %d references locators",
                                                    this.blockDTO.getName(),
                                                    originalLoopInstruction.size(),
                                                    originalReferences.size()),
                                            "",
                                            combinedTextContainer);

                                    ABRLogger.getInstance(Thread.class)
                                            .info(String.format(
                                                    "Created Web Component:\n" + "Added Block Name: %s"
                                                            + "" + "\nAdded %d Instructions"
                                                            + "\nAdded %d references locators",
                                                    this.blockDTO.getName(),
                                                    originalLoopInstruction.size(),
                                                    originalReferences.size()));

                                } else {
                                    performAction.showAlert(
                                            Alert.AlertType.ERROR,
                                            "Error Creating Web Components",
                                            "Add Web Components FAILED",
                                            String.format(
                                                    "ERROR: Creating Web Components:\n"
                                                            + "Block Name: %s\nWAS NOT INCLUDED"
                                                            + "\nWAS NOT INCLUDED- %d Instructions"
                                                            + "\nWAS NOT INCLUDED -  %d references locators",
                                                    this.blockDTO.getName(),
                                                    originalLoopInstruction.size(),
                                                    originalReferences.size()));

                                    ABRLogger.getInstance(Thread.class)
                                            .severe(String.format(
                                                    "ERROR: Creating Web Components:\n"
                                                            + "Block Name: %s\nWAS NOT INCLUDED"
                                                            + "\nWAS NOT INCLUDED- %d Instructions"
                                                            + "\nWAS NOT INCLUDED -  %d references locators",
                                                    this.blockDTO.getName(),
                                                    originalLoopInstruction.size(),
                                                    originalReferences.size()));
                                    warningMSG("Error: Unable to save the block. Please try again.");
                                    return;
                                }
                            });
                        }
                    } catch (Exception ex) {
                        ABRLogger.getInstance(Task.class).severe("Error Adding Instruction elements");
                    }

                    // Ensure closing is done on the JavaFX Application Thread
                    Platform.runLater(this::Close);

                } catch (Exception ex) {
                    // Handle the exception and display a warning message on the JavaFX Application Thread

                    ABRLogger.getInstance(Task.class)
                            .severe("Error: Unable to save the block. Please try again.\nError: " + ex.getMessage());
                }

                ABRSharedResources.getInstance().changeDbConnection();

                //                Close();
            } else {

                // Ensure UI update happens on the JavaFX Application Thread
                warningMSG("Warning: give the correct name and description");
            }
        });

        closeButton.setOnAction(e -> Close());
    }

    private int preFillSavedInstruction(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            Integer varId,
            Integer instructionOrderNumber,
            boolean exportToABR,
            String xPath,
            int savedCurrentBlockId) {

        SavedBlockLoopInstructionDTO savedInstruction = new SavedBlockLoopInstructionDTO();

        savedInstruction.setName(name);

        savedInstruction.setEncrypted(false);
        savedInstruction.setExportToABR(true);

        savedInstruction.setInstructionOrderNumber(instructionOrderNumber);

        savedInstruction.setOptional(false);

        savedInstruction.setOperation(operation);
        savedInstruction.setActions(actions);
        savedInstruction.setDescription(description);

        savedInstruction.setVariableId(varId);

        savedInstruction.setActionCustomMaxWaitSec(30);
        savedInstruction.setOnHoldSeconds(onHold);
        //        savedInstruction.setBlock(savedBlockDTO);
        savedInstruction.setExportToABR(exportToABR);

        savedInstruction.setPath(xPath);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            newId = insertSavedInstruction(savedInstruction, savedCurrentBlockId);

        } catch (SQLException e) {

            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe(String.format(
                            "Cannot Insert \"Component\" Instruction \"%s\"\nCannot be saved!\nError: %s",
                            savedInstruction.getName(), e.getMessage()));
        }
        return newId;
    }

    private int insertSavedInstruction(SavedBlockLoopInstructionDTO savedInstructionDTO, int savedCurrentBlockId)
            throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            Integer nextId = loadNextIdSavedInstructionData() + 1;
            savedInstructionDTO.setId(nextId);

            String pathValue =
                    (savedInstructionDTO.getPath() != null) ? "'" + savedInstructionDTO.getPath() + "'" : "null";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO saved_block_loop_instruction(\n" + "id, "
                    + "action_custom_max_wait_sec, "
                    + "actions, "
                    + "block_marked, "
                    + "default_val, "
                    + "description, "
                    + "encrypted, "
                    + "export_to_abr, "
                    + "instruction_order_number, "
                    + "name, "
                    + "on_hold_seconds, "
                    + "operation, "
                    + "optional, "
                    + "parent_id, "
                    + "path, "
                    + "variable_id, "
                    + "saved_block_id)\n"
                    + "VALUES ("
                    + savedInstructionDTO.getId()
                    + ", " + savedInstructionDTO.getActionCustomMaxWaitSec()
                    + ", '" + savedInstructionDTO.getActions() + "'"
                    + ", " + (savedInstructionDTO.isBlockMarked() ? "true" : "false")
                    + ", '" + savedInstructionDTO.getDefaultValue() + "'"
                    + ", '" + savedInstructionDTO.getDescription() + "'"
                    + ", " + (savedInstructionDTO.isEncrypted() ? 1 : 0)
                    + ", " + (savedInstructionDTO.getExportToABR() ? 1 : 0)
                    + ", " + savedInstructionDTO.getInstructionOrderNumber()
                    + ", '" + savedInstructionDTO.getName() + "'"
                    + ", " + savedInstructionDTO.getOnHoldSeconds()
                    + ", '" + savedInstructionDTO.getOperation() + "'"
                    + ", " + (savedInstructionDTO.isOptional() ? 1 : 0)
                    + ", " + savedInstructionDTO.getParentId()
                    + ", " + pathValue
                    + ", " + savedInstructionDTO.getVariableId()
                    + ", " + savedCurrentBlockId
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY\nid: %d\nName: %s\nActions: %s\nOperation: %s",
                                savedInstructionDTO.getId(),
                                savedInstructionDTO.getName(),
                                savedInstructionDTO.getActions(),
                                savedInstructionDTO.getOperation()));
                return nextId;
            } else {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .warning(String.format(
                                "Instruction NOT SAVED\nid: %d\nName: %s\nActions: %s\nOperations: %s",
                                savedInstructionDTO.getId(),
                                savedInstructionDTO.getName(),
                                savedInstructionDTO.getActions(),
                                savedInstructionDTO.getOperation()));
                return -1;
            }
        }
    }

    private Integer loadNextIdSavedInstructionData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_block_loop_instruction";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }

    private boolean insertComponentReferences(SavedInstructionReferenceDTO referenceDTO, int instructionId) {

        // Generate a Unique-ID for the block
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Fetch instructionId from savedBlockLoopInstructionDTO

            Integer nextId = loadNextIdBReferenceData() + 1;

            // Build the SQL insert query
            String insertSQL =
                    "INSERT INTO saved_instruction_reference(id, reference_type, value, saved_block_loop_instruction_id) VALUES ("
                            + nextId + ", "
                            + "'" + referenceDTO.getReferenceType() + "', "
                            + "'" + referenceDTO.getValue() + "', " // name
                            + instructionId + ")"; // bot_job_id, assuming BotJobDTO has an ID

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRViewBotJobPane.class)
                        .info(String.format(
                                "\"COMPONENT\" Instruction Reference SAVED SUCCESSFULLY\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                nextId, referenceDTO.getReferenceType(), referenceDTO.getValue(), instructionId));
            } else {
                ABRLogger.getInstance(ABRViewBotJobPane.class)
                        .warning(String.format(
                                "\"COMPONENT\" Instruction Reference NOT SAVED\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                nextId, referenceDTO.getReferenceType(), referenceDTO.getValue(), instructionId));
            }

            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe("Cannot Insert \"COMPONENT\" References\nError " + e.getMessage());
            return false;
        }
    }

    private Integer loadNextIdBReferenceData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_instruction_reference";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }

    private void Close() {
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
    }

    public List<BlockLoadDTO> loadSavedBlocksForBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT " + "b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "bj.id AS bot_job_id, "
                + "bj.name AS bot_job_name "
                + "FROM bot_job bj "
                + "JOIN saved_blocks b ON b.bot_job_id = bj.id "
                + "WHERE bj.id = "
                + botJobId + " ";

        // Initialize the necessary data structures
        savedBlockLoadList.clear();
        Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                    blockDTO.setBotJobName(rs.getString("bot_job_name"));

                    blockMap.put(blockId, blockDTO);
                    savedBlockLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return savedBlockLoadList;
    }

    private int createSavedBlock(BlockDTO blockDTO) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdSavedBlockData() + 1;
        Integer nextBlockOrder =
                loadNextSavedBlockOrderNumber(blockDTO.getBotJobDTO().getId()) + 1;

        // Build the SQL insert query
        String insertSQL =
                "INSERT INTO saved_blocks(id, block_order_number, description, name, type_id, bot_job_id) VALUES ("
                        + nextId + ", "
                        + nextBlockOrder + ", " // block_order_number
                        + "'" + blockDTO.getDescription() + "', " // description
                        + "'" + blockDTO.getName() + "', " // name
                        + 1 + ", " // type_id
                        + blockDTO.getBotJobDTO().getId() + ")"; // bot_job_id, assuming BotJobDTO has an ID

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ABRLogger.getInstance(ABRViewBotJobPane.class).info("Block data saved successfully id: " + nextId);
            return nextId;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class).severe("saveBlock  \nError: " + e.getMessage());
            return -1;
        }
    }

    private Integer loadNextIdSavedBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_blocks";
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private Integer loadNextSavedBlockOrderNumber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM saved_blocks where bot_job_id = " + botJobId;
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    public List<InstructionDTO> getSavedInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<InstructionDTO> instructions = new ArrayList<>();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM saved_block_loop_instruction WHERE block_id = " + blockId
                + " order by instruction_order_number ASC";

        // Execute the query and process the result set
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                InstructionDTO instruction = new InstructionDTO();
                instruction.setInstructionId(rs.getInt("id"));
                instruction.setInstructionName(rs.getString("name"));
                instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                instruction.setBlockId(rs.getInt("block_id"));
                instruction.setBlockOrderNumber(instruction.getBlockOrderNumber());
                instruction.setBotJobId(botJobId);

                instruction.setActions(rs.getString("actions"));
                instruction.setPath(rs.getString("path"));
                instruction.setDescription(rs.getString("description"));
                instruction.setOptional(rs.getInt("optional"));
                instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                instruction.setEncrypted(rs.getInt("encrypted"));
                instruction.setExportToABR(rs.getInt("export_to_abr"));

                // Add the instruction to the list
                instructions.add(instruction);
            }

            ABRLogger.getInstance(ABRWebDriver.class)
                    .info(String.format(
                            "Fetched %d Saved instructions for Block ID %d:", instructions.size(), blockId));

        } catch (SQLException e) {
            ABRLogger.getInstance(ABRWebDriver.class)
                    .severe(String.format(
                            "Error fetching Saved instructions for Block ID %d. Error: %s: ", blockId, e.getMessage()));
        }

        return instructions;
    }

    private void warningMSG(String msg) {
        Platform.runLater(() -> {
            warningLabel.setText(msg);
        });
    }

    public void setBlockJob(BlockDTO blockJob) {
        this.blockDTO = blockJob;
    }
}
