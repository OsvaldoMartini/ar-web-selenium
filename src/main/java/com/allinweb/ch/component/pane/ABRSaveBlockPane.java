package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.component.scene.ABRAlertScene;
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
import java.util.Queue;
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
import javafx.stage.Stage;

public class ABRSaveBlockPane extends ABRPane {

    private final ABRComponentBuilder builder = new ABRComponentBuilder();
    private SavedBlocksDTO savedBlocksDTO;
    private BlockDTO blockDTO;
    private BotJobDTO botJobDTO;

    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private static final PerformActions performAction;
    // Static block to initialize
    static {
        performAction = PerformActions.getInstance();
    }

    TextField nameTextField;
    TextArea descriptionTextField;

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
                try {
                    // Ensure UI update happens on the JavaFX Application Thread
                    Platform.runLater(() -> warningLabel.setText(""));

                    savedBlocksDTO.setName(nameTextField.getText());
                    savedBlocksDTO.setDescription(descriptionTextField.getText());
                    savedBlocksDTO.setBotJob(this.botJobDTO);

                    loadSavedBlocksForBotJob(this.botJobDTO.getId());

                    boolean existName = blockLoadList.stream().anyMatch(block -> block.getName()
                            .equalsIgnoreCase(nameTextField.getText().trim()));

                    if (existName) {
                        performAction.showAlert(
                                Alert.AlertType.ERROR,
                                "Name Already Taken!",
                                "Change the Component Name",
                                "The Name: \"" + nameTextField.getText().trim() + "\"\nHas been take!");
                        return;
                    }

                    // Debugging: Print statements to track data
                    ABRLogger.getInstance(ABRSaveBlockPane.class).fine("Saving Block: " + savedBlocksDTO.getName());

                    Queue<SavedBlockLoopInstructionDTO> savedBlockLoopInstructionList =
                            SavedBlocksDTO.createSavedBlockLoopInstructionsFromBlocksDTO(blockDTO, savedBlocksDTO);

                    // Debugging: Ensure savedBlockLoopInstructionList has the right data
                    ABRLogger.getInstance(ABRSaveBlockPane.class)
                            .fine("SavedBlockLoopInstructionList Size: " + savedBlockLoopInstructionList.size());

                    List<SavedInstructionReferenceDTO> savedReferenceQueue = new ArrayList<>();
                    savedBlockLoopInstructionList.forEach(savedInstruction -> {
                        savedReferenceQueue.addAll(savedInstruction.getSavedInstructionReferenceDTOList());
                        //                        savedInstruction.setSavedInstructionReferenceDTOList(null); // Reset
                        // to avoid reuse
                    });

                    // Debugging: Check the size of savedReferenceQueue before proceeding
                    ABRLogger.getInstance(ABRSaveBlockPane.class)
                            .fine("SavedReferenceQueue Size: " + savedReferenceQueue.size());

                    // Ensure that we only add the entity once
                    //                    ABRSharedResources.getInstance()
                    //                            .addEntity(savedBlocksDTO, SavedBlocksDTO.class, () ->
                    // ABRSharedResources.getInstance()
                    //                                    .addAllEntity(
                    //                                            savedBlockLoopInstructionList,
                    // SavedBlockLoopInstructionDTO.class, () -> {
                    //                                                // Check if savedReferenceQueue has elements
                    // before proceeding
                    //                                                if (savedReferenceQueue.size() > 0) {
                    //                                                    ABRSharedResources.getInstance()
                    //                                                            .addAllEntity(
                    //                                                                    savedReferenceQueue,
                    //
                    // SavedInstructionReferenceDTO.class,
                    //                                                                    () -> {
                    //                                                                        // Ensure alert UI is
                    // shown on the JavaFX
                    //                                                                        // Application Thread
                    //                                                                        Platform.runLater(() -> {
                    //                                                                            System.out.println(
                    //                                                                                    "Showing Block
                    // Saved Alert");
                    //                                                                            new ABRAlertScene(
                    //
                    // Alert.AlertType.INFORMATION,
                    //                                                                                    "Block Saved",
                    //                                                                                    "The block has
                    // been saved successfully",
                    //
                    // ButtonType.OK);
                    //                                                                        });
                    //                                                                    });
                    //                                                } else {
                    //                                                    // If there are no references, still show the
                    // success message
                    //                                                    Platform.runLater(() -> {
                    //                                                        System.out.println("Showing Block Saved
                    // Alert (No References)");
                    //                                                        new ABRAlertScene(
                    //                                                                Alert.AlertType.INFORMATION,
                    //                                                                "Block Saved",
                    //                                                                "The block has been saved
                    // successfully",
                    //                                                                ButtonType.OK);
                    //                                                    });
                    //                                                }
                    //                                            }));

                    int currentBlockId = this.blockDTO.getId();

                    if (blockLoadList.isEmpty()) {

                        // It Prevents Start without blocks
                        SavedBlocksDTO savedBlocksDTO = new SavedBlocksDTO();

                        savedBlocksDTO.setDescription("Default Block description");
                        savedBlocksDTO.setName("Default Block");
                        BlockDTO blockDTO = BlockDTO.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, this.botJobDTO);
                        //                        BotJobDTO botJob =
                        //
                        // ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, this.botJobDTO.getId());
                        blockDTO.setTypeId(1);
                        blockDTO.setBotJob(botJobDTO);
                        blockDTO.setName("Default Block");
                        blockDTO.setDescription("Default Block description");

                        //            ABRSharedResources.getInstance().addEntity(blockDTO, BlockDTO.class);

                        currentBlockId = createSavedBlock(blockDTO);

                        if (currentBlockId < 0) {
                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Error Creating new Block",
                                    "Verify the Bot Job Name if have any",
                                    "Check if you already have a Bot Job Created!");

                            ABRLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "Error Creating a new Block for bot job Id %d\nCheck if you already have a Bot Job Created!",
                                            this.botJobDTO.getId()));
                            return;
                        } else {

                            setBlockJob(ABRSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                            ABRLogger.getInstance(Thread.class)
                                    .info(String.format(
                                            "Created a new Block id %d for bot job Id %d",
                                            currentBlockId, this.botJobDTO.getId()));
                        }
                    } else {
                        if (blockLoadList.size() > 0 && this.blockDTO == null) {
                            currentBlockId = blockLoadList.get(0).getId();
                            setBlockJob(ABRSharedResources.getInstance().getEntityById(BlockDTO.class, currentBlockId));
                        } else if (this.blockDTO != null) {
                            currentBlockId = this.blockDTO.getId();
                        }
                    }

                    while (!savedBlockLoopInstructionList.isEmpty()) {
                        SavedBlockLoopInstructionDTO task = savedBlockLoopInstructionList.poll();
                        preFillSavedInstruction(
                                task.getName(),
                                task.getDescription(),
                                task.getActions(),
                                task.getOperation(),
                                task.getOnHoldSeconds(),
                                task.getVariableId(),
                                task.getId(),
                                this.blockDTO, // blockDTO
                                this.botJobDTO,
                                false);
                    }

                    try {

                        Platform.runLater(() -> {
                            boolean saved = insertReferences(savedReferenceQueue);
                            if (saved) {

                                new ABRAlertScene(
                                        Alert.AlertType.INFORMATION,
                                        "Web Instruction Add",
                                        "The Block  \"" + this.blockDTO.getName()
                                                + "\" with "
                                                + savedReferenceQueue.size() + " references locators"
                                                + "\nHas been added successfully!",
                                        ButtonType.OK);
                            } else {
                                new ABRAlertScene(
                                        Alert.AlertType.ERROR,
                                        "Add Web Instruction FAILED",
                                        "The Bock " + this.blockDTO.getName() + " with "
                                                + savedReferenceQueue.size() + " reference locators"
                                                + "\nWas Added!"
                                                + "\nTHE ENGINE IS GOING TO FAIL FOR THIS ELEMENT",
                                        ButtonType.OK);
                            }
                        });
                    } catch (Exception ex) {
                        ABRLogger.getInstance(Task.class).severe("Error Adding Instruction elements");
                    }

                    // Ensure closing is done on the JavaFX Application Thread
                    Platform.runLater(this::Close);

                } catch (Exception ex) {
                    // Handle the exception and display a warning message on the JavaFX Application Thread
                    Platform.runLater(() -> {
                        warningLabel.setText("Error: Unable to save the block. Please try again.");
                    });
                    ex.printStackTrace(); // Optionally log the error for debugging purposes
                }

                //                Close();
            } else {

                // Ensure UI update happens on the JavaFX Application Thread
                Platform.runLater(() -> warningLabel.setText("Warning: give the correct name and description"));
                ;
            }
        });

        closeButton.setOnAction(e -> Close());
    }

    private void preFillSavedInstruction(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            Integer varId,
            Integer instructionId,
            BlockDTO blockDTO,
            BotJobDTO botJob,
            boolean isShowAlert) {

        BlockLoopInstructionDTO instruction = new BlockLoopInstructionDTO();

        instruction.setName(name);

        instruction.setEncrypted(false);
        instruction.setExportToABR(true);

        instruction.setInstructionOrderNumber(1);

        instruction.setOptional(false);

        instruction.setOperation(operation);
        instruction.setActions(actions);
        instruction.setDescription(description);

        instruction.setVariableId(varId);

        Integer nextId = loadNextIdSavedInstructionData() + 1;
        instruction.setId(nextId);

        instruction.setActionCustomMaxWaitSec(30);
        instruction.setOnHoldSeconds(onHold);
        instruction.setBlock(blockDTO);
        instruction.setExportToABR(false);
        // Wrap the persistence in a try-catch block
        boolean response;

        try {
            response = insertSavedInstruction(instruction);

            if (response) {
                new ABRAlertScene(
                        Alert.AlertType.INFORMATION,
                        "Component Instruction Added",
                        "\"Component\" Instruction \"" + instruction.getName() + "\" has been added successfully",
                        ButtonType.OK);
                performAction.showAlert(
                        Alert.AlertType.ERROR,
                        "Error Add New \"Component\" Instruction",
                        "Not possible to insert new Operation",
                        String.format("\"Component\" Instruction \"%s\"\nCannot be saved", instruction.getName()));

                ABRLogger.getInstance(ABRViewBotJobPane.class)
                        .info(String.format(
                                "\"Component\" Instruction: \"%s\"\nhas been added successfully!",
                                instruction.getName()));
            } else {
                performAction.showAlert(
                        Alert.AlertType.ERROR,
                        "Error Add New \"Component\" Instruction",
                        "Not possible to insert new Operation",
                        String.format("\"Component\" Instruction \"%s\"\nCannot be saved", instruction.getName()));
                ABRLogger.getInstance(ABRViewBotJobPane.class)
                        .severe(String.format(
                                "Error Add New \"Component\" Instruction: \"%s\"\nCannot be saved!",
                                instruction.getName()));
            }

        } catch (SQLException e) {
            performAction.showAlert(
                    Alert.AlertType.ERROR,
                    "Error Add New \"Component\" Instruction",
                    "Not possible to insert new Operation",
                    String.format("\"Component\" Instruction \"%s\"\nCannot be saved", instruction.getName()));

            ABRLogger.getInstance(ABRViewBotJobPane.class)
                    .severe(String.format(
                            "Cannot Insert \"Component\" Instruction \"%s\"\nCannot be saved!\nError: %s",
                            instruction.getName(), e.getMessage()));
        }
    }

    private boolean insertSavedInstruction(BlockLoopInstructionDTO instructionDTO) throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            //            Integer nextId = loadNextIdBInstructionData() + 1;

            String pathValue = (instructionDTO.getPath() != null) ? "'" + instructionDTO.getPath() + "'" : "null";

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
                    + "block_id)\n"
                    + "VALUES ("
                    + instructionDTO.getId()
                    + ", " + instructionDTO.getActionCustomMaxWaitSec()
                    + ", '" + instructionDTO.getActions() + "'"
                    + ", " + (instructionDTO.isBlockMarked() ? "true" : "false")
                    + ", '" + instructionDTO.getDefaultValue() + "'"
                    + ", '" + instructionDTO.getDescription() + "'"
                    + ", " + (instructionDTO.isEncrypted() ? 1 : 0)
                    + ", " + (instructionDTO.getExportToABR() ? 1 : 0)
                    + ", " + instructionDTO.getInstructionOrderNumber()
                    + ", '" + instructionDTO.getName() + "'"
                    + ", " + instructionDTO.getOnHoldSeconds()
                    + ", '" + instructionDTO.getOperation() + "'"
                    + ", " + (instructionDTO.isOptional() ? 1 : 0)
                    + ", " + instructionDTO.getParentId()
                    + ", " + pathValue
                    + ", " + instructionDTO.getVariableId()
                    + ", " + instructionDTO.getBlock().getId()
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY\nid: %d\nName: %s\nActions: %s\nOperation: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return true;
            } else {
                ABRLogger.getInstance(ABRNewCommandPane.class)
                        .warning(String.format(
                                "Instruction NOT SAVED\nid: %d\nName: %s\nActions: %s\nOperations: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return false;
            }
        }
    }

    private boolean insertReferences(List<SavedInstructionReferenceDTO> queue) {

        // Generate a Unique-ID for the block
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            for (SavedInstructionReferenceDTO reference : queue) {

                // Fetch instructionId from savedBlockLoopInstructionDTO
                SavedBlockLoopInstructionDTO instructionDTO = reference.getSavedBlockLoopInstructionDTO();
                if (instructionDTO == null) {
                    ABRLogger.getInstance(ABRViewBotJobPane.class)
                            .warning("SavedBlockLoopInstructionDTO is null for reference: " + reference);
                    continue; // Skip this reference if it doesn't have an associated instruction
                }

                Integer nextId = loadNextIdBReferenceData() + 1;

                // Build the SQL insert query
                String insertSQL =
                        "INSERT INTO saved_instruction_reference(id, reference_type, value, block_loop_instruction_id) VALUES ("
                                + nextId + ", "
                                + "'" + reference.getReferenceType() + "', "
                                + "'" + reference.getValue() + "', " // name
                                + instructionDTO.getId() + ")"; // bot_job_id, assuming BotJobDTO has an ID

                int rowsAffected = stmt.executeUpdate(insertSQL);
                if (rowsAffected > 0) {
                    ABRLogger.getInstance(ABRViewBotJobPane.class)
                            .info(String.format(
                                    "Saved Instruction Reference SAVED SUCCESSFULLY\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                    nextId,
                                    reference.getReferenceType(),
                                    reference.getValue(),
                                    instructionDTO.getId()));
                } else {
                    ABRLogger.getInstance(ABRViewBotJobPane.class)
                            .warning(String.format(
                                    "Saved Instruction Reference NOT SAVED\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                    nextId,
                                    reference.getReferenceType(),
                                    reference.getValue(),
                                    instructionDTO.getId()));
                }
            }
            return true;
        } catch (SQLException e) {
            ABRLogger.getInstance(ABRViewBotJobPane.class).severe("Cannot Insert References\nError " + e.getMessage());
            return false;
        }
    }

    private Integer loadNextIdBReferenceData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM instruction_reference";
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
        blockLoadList.clear();
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
                    blockLoadList.add(blockDTO);
                }
            }
        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return blockLoadList;
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

    public void setBlockJob(BlockDTO blockJob) {
        this.blockDTO = blockJob;
    }
}
