package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.DetailsDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARSaveComponentPane extends ARPane {

    private final ARComponentBuilder builder = new ARComponentBuilder();
    private ComponentBlockDTO componentBlockDTO;
    private BlockDTO blockDTO;
    private DetailsDTO detailsDTO;

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private List<BlockLoadDTO> savedBlockLoadList = new ArrayList<>();
    private List<ComponentInstructionDTO> originalLoopInstruction;
    private List<ComponentReferenceDTO> originalReferences;

    private static final PerformMessage performMessage;
    private static final PerformActions performAction;
    private static final PerformDataBase performDataBase;
    //    private static final PerformDBSavedBlock performDBSavedBlock;
    // Static block to initialize
    static {
        performMessage = PerformMessage.getInstance();
        performAction = PerformActions.getInstance();
        performDataBase = PerformDataBase.getInstance();
        //        performDBSavedBlock = PerformDBSavedBlock.getInstance();
    }

    TextField nameTextField;
    TextArea descriptionTextField;

    Text regularText;
    Text variableText1;
    Text variableText2;

    Label warningLabel;

    private Button saveNewComponentButton;
    Button closeButton;

    private AnchorPane mainPane;

    public ARSaveComponentPane(ComponentBlockDTO componentBlockDTO, BlockDTO blockDTO, DetailsDTO detailsDTO) {
        this.componentBlockDTO = componentBlockDTO;
        this.blockDTO = blockDTO;
        this.detailsDTO = detailsDTO;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        //  Alert Timer Components
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        countdownLabel.setVisible(false);
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Title");
        alertToShow.setHeaderText("Header Message");
        alertToShow.setContentText("Main Message");
        alertToShow.initModality(Modality.APPLICATION_MODAL);
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        saveNewComponentButton = builder.buildButton("Save New Component", ARConstants.SPACE_L);
        closeButton = builder.buildButton(" Close ", ARConstants.SPACE_L);

        HBox actionPanel = new HBox(saveNewComponentButton, closeButton);
        actionPanel.setSpacing(ARConstants.SPACE_SM);
        actionPanel.setAlignment(Pos.CENTER);

        Label nameLabel = new Label("Name :         ");

        nameTextField = new TextField(componentBlockDTO.getName());
        HBox nameHBox = new HBox(nameLabel, nameTextField);
        HBox.setHgrow(nameTextField, Priority.ALWAYS);
        HBox.setMargin(nameLabel, new Insets(ARConstants.SPACE_XS));

        Label descriptionLabel = new Label("Description : ");
        descriptionTextField = new TextArea(componentBlockDTO.getDescription());

        regularText = new Text("Excluded Special Operations: ");
        variableText1 = new Text("Set Value" + " / " + "Get Value");
        variableText2 = new Text("Check Value" + " / " + "Excel Save");
        variableText1.setFill(Color.BLUE);
        variableText2.setFill(Color.BLUE);

        HBox descriptionHBox = new HBox(descriptionLabel, descriptionTextField);
        HBox.setHgrow(descriptionTextField, Priority.ALWAYS);
        HBox.setMargin(descriptionLabel, new Insets(ARConstants.SPACE_XS));

        nameTextField.setMaxHeight(ARConstants.SPACE_XL);
        nameTextField.setPrefHeight(ARConstants.SPACE_XL);

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
        separtorPanel.setSpacing(ARConstants.SPACE_SM);

        // separtorPanel.setPrefWidth(400);

        AnchorPane.setTopAnchor(separtorPanel, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(separtorPanel, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(separtorPanel, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(separtorPanel, ARConstants.SPACE_M);

        mainPane = new AnchorPane(separtorPanel);
    }

    @Override
    public void initUIBehaviour() {

        saveNewComponentButton.setOnMouseClicked(e -> {
            //            ARSharedResources.getInstance().cacheEntitiesFromDB();

            if (nameTextField.getText() != null
                    && !nameTextField.getText().trim().isEmpty()
                    && descriptionTextField.getText() != null
                    && !descriptionTextField.getText().trim().isEmpty()) {
                try {
                    // Ensure UI update happens on the JavaFX Application Thread
                    warningMSG("");

                    componentBlockDTO.setName(nameTextField.getText().trim());
                    componentBlockDTO.setDescription(
                            descriptionTextField.getText().trim());

                    this.savedBlockLoadList =
                            loadSavedBlocksForBotJob(detailsDTO.getNewBlock().getBotJobId());

                    //                    originalLoopInstruction =
                    // performDBSavedBlock.createSavedBlockLoopInstructionsFromBlocksDTO(
                    //                            detailsDTO, componentBlockDTO);

                    // Debugging: Ensure originalLoopInstruction has the right data
                    //                    ARLogger.getInstance(ARSaveComponentPane.class)
                    //                            .fine("originalLoopInstruction Size: " +
                    // originalLoopInstruction.size());

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
                    ARLogger.getInstance(ARSaveComponentPane.class)
                            .fine("Saving New Component Block: " + componentBlockDTO.getName());

                    try (Connection conn = performDataBase.getConnection()) {
                        int newBotJobId = performDataBase.getMaxId(conn, "bot_job") + 1;

                        // String[] arrayTables = {"block", "instruction", "reference", "complex_instruction",
                        // "variable"};
                        String[] arrayTables = {
                            "component_block",
                            "component_instruction",
                            "component_reference",
                            "component_complex",
                            "component_variable"
                        };
                        // Now you can proceed with duplicating the related tables
                        ErrorMessage errorMessage = performDataBase.duplicateRelatedTables(
                                conn,
                                detailsDTO.getNewBlock().getBotJobId(),
                                detailsDTO.getNewBlock().getBotJobId(),
                                arrayTables);

                        if (errorMessage == null) {
                            showAlertTimer(
                                    Alert.AlertType.INFORMATION,
                                    "Success",
                                    "New Component Creation",
                                    "The Block Component job has been successfully created!",
                                    String.format(
                                            "Component (ID: %d) Name '%s' ", newBotJobId, componentBlockDTO.getName()),
                                    componentBlockDTO.getDescription(),
                                    null);

                        } else {
                            String errorType = "Database error";
                            String errorDetail = "Verify  [INSERT] or [UPDATE] or [SELECT]";

                            String detailedMessage = "Type: " + errorType + "\nDetail: " + errorDetail;
                            showAlertTimer(
                                    Alert.AlertType.ERROR,
                                    "Error",
                                    errorMessage.getErrorTitle(),
                                    errorMessage.getErrorHeader(),
                                    detailedMessage,
                                    null,
                                    null);
                        }
                        Stage stage =
                                (Stage) ((Button) e.getSource()).getScene().getWindow();
                        stage.close();
                    } catch (SQLException ex) {
                        System.out.println(ex.getMessage());
                    }

                    //
                    //
                    //                    // Here Always Creating a New Component Block
                    //                    BlockLoadDTO blockDTO = performDBSavedBlock.createBlocksDTOFromSavedBlocksDTO(
                    //                            componentBlockDTO, detailsDTO.getNewBlock().getBotJobId());
                    //
                    //                    int savedCurrentBotJobId = detailsDTO.getNewBlock().getBotJobId();
                    //                    int savedCurrentBlockId = createSavedBlock(blockDTO);
                    //
                    //                    if (savedCurrentBlockId > 0) {
                    //                        //
                    // ARSharedResources.getInstance().cacheEntitiesFromDB();
                    //
                    //                        ARLogger.getInstance(Thread.class)
                    //                                .info(String.format(
                    //                                        "Created a new Block id %d for bot job Id %d",
                    //                                        savedCurrentBlockId,
                    //                                        detailsDTO.getNewBlock().getBotJobId()));
                    //                    } else {
                    //                        performAction.showAlert(
                    //                                Alert.AlertType.ERROR,
                    //                                "Error Creating new Block",
                    //                                "Verify the Bot Job Name if have any",
                    //                                "Check if you already have a Bot Job Created!");
                    //
                    //                        ARLogger.getInstance(Thread.class)
                    //                                .severe(String.format(
                    //                                        "Error Creating a new Block for bot job Id %d\nCheck if
                    // you already have a Bot Job Created!",
                    //                                        detailsDTO.getNewBlock().getBotJobId()));
                    //                        warningMSG(String.format(
                    //                                "Error Creating a new Block for bot job Id %d!",
                    //                                detailsDTO.getNewBlock().getBotJobId()));
                    //
                    //                        return;
                    //                    }
                    //
                    //                    boolean savedInstStatus = false;
                    //                    for (int j = 0; j < originalLoopInstruction.size(); j++) {
                    //                        ComponentInstructionDTO task = originalLoopInstruction.get(j);
                    //                        int newId = preFillSavedInstruction(
                    //                                task.getName(),
                    //                                task.getDescription(),
                    //                                task.getActions(),
                    //                                task.getOperation(),
                    //                                task.getOnHoldSeconds(),
                    //                                task.getVariableId(),
                    //                                task.getInstructionOrderNumber(),
                    //                                task.getExportToAR(),
                    //                                task.getPath(),
                    //                                savedCurrentBotJobId,
                    //                                savedCurrentBlockId);
                    //
                    //                        task.setId(newId);
                    //
                    //                        if (newId < 0) {
                    //                            savedInstStatus = false;
                    //
                    //                            performAction.showAlert(
                    //                                    Alert.AlertType.ERROR,
                    //                                    "Error Add New \"Component\" Instruction",
                    //                                    "Not possible to insert new Operation",
                    //                                    String.format("\"Component\" Instruction \"%s\"\nCannot be
                    // saved", task.getName()));
                    //
                    //                            warningMSG(String.format(
                    //                                    "Error Creating a Instructions %s! - Actions: %s",
                    //                                    task.getName(), task.getActions()));
                    //                        } else {
                    //                            savedInstStatus = true;
                    //                        }
                    //
                    //                        if (!savedInstStatus) {
                    //                            break;
                    //                        }
                    //                    }
                    //
                    //                    if (savedInstStatus) {
                    //                        ARSharedResources.getInstance().cacheEntitiesFromDB();
                    //                    } else {
                    //                        return;
                    //                    }
                    //
                    //                    try {
                    //
                    //                        // Build Saved Components References
                    //                        originalReferences = new ArrayList<>();
                    //                        originalLoopInstruction.forEach(savedInstruction -> {
                    //
                    // originalReferences.addAll(savedInstruction.getSavedInstructionReferenceDTOList());
                    //                        });
                    //
                    //                        if (savedInstStatus && originalReferences.size() > 0) {
                    //                            ARLogger.getInstance(ARSaveComponentPane.class)
                    //                                    .fine("originalReferences Size: " +
                    // originalReferences.size());
                    //
                    //                            boolean success = false;
                    //                            for (ComponentReferenceDTO reference : originalReferences) {
                    //
                    //                                ComponentInstructionDTO instructionDTO =
                    // reference.getSavedBlockLoopInstructionDTO();
                    //                                if (instructionDTO == null) {
                    //                                    ARLogger.getInstance(ARViewBotJobPane.class)
                    //                                            .warning("SavedBlockLoopInstructionDTO is null for
                    // reference: "
                    //                                                    + reference.getReferenceType());
                    //                                    continue;
                    //                                }
                    //
                    //                                success = insertComponentReferences(reference,
                    // instructionDTO.getId());
                    //                                if (!success) {
                    //                                    break;
                    //                                }
                    //                            }
                    //                            final boolean successFinal = success;
                    //
                    //                            // Create individual text elements with the necessary styling
                    //                            Text regularTextStyled = new Text(regularText.getText());
                    //                            regularTextStyled.setStyle("-fx-font-size: 18px; -fx-fill: black;");
                    //
                    //                            Text variableText1Styled = new Text(variableText1.getText());
                    //                            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
                    //
                    //                            Text variableText2Styled = new Text(variableText2.getText());
                    //                            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
                    //
                    //                            // Create an VBox to hold the individual text elements
                    //                            VBox combinedTextContainer = new VBox();
                    //                            combinedTextContainer.setSpacing(1);
                    //
                    //                            combinedTextContainer
                    //                                    .getChildren()
                    //                                    .addAll(regularTextStyled, variableText1Styled,
                    // variableText2Styled);
                    //
                    //                            Platform.runLater(() -> {
                    //                                if (successFinal) {
                    //
                    //                                    // Create Text for the variable part and set the color to red
                    //                                    performMessage.showAlertCombinedVBOX(
                    //                                            Alert.AlertType.INFORMATION,
                    //                                            "Created Web Component",
                    //                                            String.format(
                    //                                                    "Created Web Component:\n" + "Added Block
                    // Name: %s"
                    //                                                            + "" + "\nAdded %d Instructions"
                    //                                                            + "\nAdded %d references locators",
                    //                                                    this.blockDTO.getName(),
                    //                                                    originalLoopInstruction.size(),
                    //                                                    originalReferences.size()),
                    //                                            "",
                    //                                            combinedTextContainer);
                    //
                    //                                    ARLogger.getInstance(Thread.class)
                    //                                            .info(String.format(
                    //                                                    "Created Web Component:\n" + "Added Block
                    // Name: %s"
                    //                                                            + "" + "\nAdded %d Instructions"
                    //                                                            + "\nAdded %d references locators",
                    //                                                    this.blockDTO.getName(),
                    //                                                    originalLoopInstruction.size(),
                    //                                                    originalReferences.size()));
                    //
                    //                                } else {
                    //                                    performAction.showAlert(
                    //                                            Alert.AlertType.ERROR,
                    //                                            "Error Creating Web Components",
                    //                                            "Add Web Components FAILED",
                    //                                            String.format(
                    //                                                    "ERROR: Creating Web Components:\n"
                    //                                                            + "Block Name: %s\nWAS NOT INCLUDED"
                    //                                                            + "\nWAS NOT INCLUDED- %d
                    // Instructions"
                    //                                                            + "\nWAS NOT INCLUDED -  %d references
                    // locators",
                    //                                                    this.blockDTO.getName(),
                    //                                                    originalLoopInstruction.size(),
                    //                                                    originalReferences.size()));
                    //
                    //                                    ARLogger.getInstance(Thread.class)
                    //                                            .severe(String.format(
                    //                                                    "ERROR: Creating Web Components:\n"
                    //                                                            + "Block Name: %s\nWAS NOT INCLUDED"
                    //                                                            + "\nWAS NOT INCLUDED- %d
                    // Instructions"
                    //                                                            + "\nWAS NOT INCLUDED -  %d references
                    // locators",
                    //                                                    this.blockDTO.getName(),
                    //                                                    originalLoopInstruction.size(),
                    //                                                    originalReferences.size()));
                    //                                    warningMSG("Error: Unable to save the block. Please try
                    // again.");
                    //                                    return;
                    //                                }
                    //                            });
                    //                        }
                    //                    } catch (Exception ex) {
                    //                        ARLogger.getInstance(Task.class).severe("Error Adding Instruction
                    // elements");
                    //                    }

                    // Ensure closing is done on the JavaFX Application Thread
                    Platform.runLater(this::Close);

                } catch (Exception ex) {
                    // Handle the exception and display a warning message on the JavaFX Application Thread

                    ARLogger.getInstance(Task.class)
                            .severe("Error: Unable to save the block. Please try again.\nError: " + ex.getMessage());

                    showAlertTimer(
                            Alert.AlertType.ERROR,
                            "Error Component",
                            "Unable to create new Component.",
                            "Error creating a new component",
                            "Please try again.",
                            null,
                            null);

                    warningMSG("Error creating a new componen! Please try again.");
                }

                //                ARSharedResources.getInstance().changeDbConnection();

                //                Close();
            } else {

                // Ensure UI update happens on the JavaFX Application Thread
                warningMSG("Warning: give the correct name and description");
            }
        });

        closeButton.setOnAction(e -> Close());
    }

    //    private int preFillSavedInstruction(
    //            String name,
    //            String description,
    //            String actions,
    //            String operation,
    //            Integer onHold,
    //            Integer varId,
    //            Integer instructionOrderNumber,
    //            boolean exportToAR,
    //            String xPath,
    //            Integer savedCurrentBotJobId,
    //            Integer savedCurrentBlockId) {
    //
    //        ComponentInstructionDTO savedInstruction = new ComponentInstructionDTO();
    //
    //        savedInstruction.setName(name);
    //        savedInstruction.setActive(true);
    //
    //        savedInstruction.setCodified(false);
    //        savedInstruction.setExportToAR(true);
    //
    //        savedInstruction.setInstructionOrderNumber(instructionOrderNumber);
    //
    //        savedInstruction.setOptional(false);
    //
    //        savedInstruction.setOperation(operation);
    //        savedInstruction.setActions(actions);
    //        savedInstruction.setDescription(description);
    //
    //        savedInstruction.setVariableId(varId);
    //
    //        savedInstruction.setActionCustomMaxWaitSec(30);
    //        savedInstruction.setOnHoldSeconds(onHold);
    //        //        savedInstruction.setBlock(savedBlockDTO);
    //        savedInstruction.setExportToAR(exportToAR);
    //
    //        savedInstruction.setPath(xPath);
    //
    //        // Wrap the persistence in a try-catch block
    //        int newId = -1;
    //
    //        try {
    //            newId = performDBSavedBlock.insertSavedInstruction(
    //                    savedInstruction, savedCurrentBotJobId, savedCurrentBlockId);
    //
    //        } catch (SQLException e) {
    //
    //            ARLogger.getInstance(ARViewBotJobPane.class)
    //                    .severe(String.format(
    //                            "Cannot Insert \"Component\" Instruction \"%s\"\nCannot be saved!\nError: %s",
    //                            savedInstruction.getName(), e.getMessage()));
    //        }
    //        return newId;
    //    }

    private boolean insertComponentReferences(ComponentReferenceDTO referenceDTO, int instructionId) {

        // Generate a Unique-ID for the block
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {

            // Fetch instructionId from savedBlockLoopInstructionDTO

            Integer nextId = loadNextIdBReferenceData() + 1;

            // Build the SQL insert query
            String insertSQL =
                    "INSERT INTO component_reference(id, reference_type, value, component_instruction_id) VALUES ("
                            + nextId + ", "
                            + "'" + referenceDTO.getReferenceType() + "', "
                            + "'" + referenceDTO.getValue() + "', " // name
                            + instructionId + ")"; // bot_job_id, assuming BotJobDTO has an ID

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(ARViewBotJobPane.class)
                        .info(String.format(
                                "\"COMPONENT\" Instruction Reference SAVED SUCCESSFULLY\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                nextId, referenceDTO.getReferenceType(), referenceDTO.getValue(), instructionId));
            } else {
                ARLogger.getInstance(ARViewBotJobPane.class)
                        .warning(String.format(
                                "\"COMPONENT\" Instruction Reference NOT SAVED\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                nextId, referenceDTO.getReferenceType(), referenceDTO.getValue(), instructionId));
            }

            return true;
        } catch (SQLException e) {
            ARLogger.getInstance(ARViewBotJobPane.class)
                    .severe("Cannot Insert \"COMPONENT\" References\nError " + e.getMessage());
            return false;
        }
    }

    private Integer loadNextIdBReferenceData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM component_reference";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ARViewBotJobPane.class).severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
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
        String query = "SELECT bj.home_banking_id, b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "bj.id AS bot_job_id, "
                + "bj.name AS bot_job_name "
                + "FROM bot_job bj "
                + "JOIN component_block b ON b.bot_job_id = bj.id "
                + "WHERE bj.id = "
                + botJobId + " ";

        // Initialize the necessary data structures
        savedBlockLoadList.clear();
        Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMap.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setHomeBankingId(rs.getInt("home_banking_id"));
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
            ARLogger.getInstance(Thread.class)
                    .severe(String.format(
                            "Error loadSavedBlocksForBotJob for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return savedBlockLoadList;
    }

    private int createSavedBlock(BlockLoadDTO blockDTO) {
        // Generate a Unique-ID for the block
        Integer nextId = loadNextIdSavedBlockData() + 1;
        Integer nextBlockOrder = loadNextSavedBlockOrderNumber(blockDTO.getBotJobId()) + 1;

        // Build the SQL insert query
        String insertSQL =
                "INSERT INTO component_block(id, block_order_number, description, name, type_id, bot_job_id, active) VALUES ("
                        + nextId + ", "
                        + nextBlockOrder + ", " // block_order_number
                        + "'" + blockDTO.getDescription() + "', " // description
                        + "'" + blockDTO.getName() + "', " // name
                        + 1 + ", " // type_id
                        + blockDTO.getBotJobId() + ", " // bot_job_id, assuming BotJobDTO has an ID
                        + blockDTO.getActive() + ", " // active
                        + ")";

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
            stmt.executeUpdate(insertSQL);
            ARLogger.getInstance(ARViewBotJobPane.class).info("Block data saved successfully id: " + nextId);
            return nextId;
        } catch (SQLException e) {
            ARLogger.getInstance(ARViewBotJobPane.class).severe("saveBlock  \nError: " + e.getMessage());
            return -1;
        }
    }

    private Integer loadNextIdSavedBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM component_block";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ARViewBotJobPane.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private Integer loadNextSavedBlockOrderNumber(int botJobId) {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM component_block where bot_job_id = " + botJobId;
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ARViewBotJobPane.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    public List<InstructionDTO> getSavedInstructionsByBlockId(int botJobId, int blockId) {
        // List to store the fetched instructions
        List<InstructionDTO> instructions = new ArrayList<>();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM component_instruction WHERE block_id = " + blockId
                + " order by instruction_order_number ASC";

        // Execute the query and process the result set
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
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
                instruction.setCoordinates(rs.getString("coordinates"));
                instruction.setIFrameXPath(rs.getString("iframe_xpath"));
                instruction.setDescription(rs.getString("description"));
                instruction.setOptional(rs.getBoolean("optional"));
                instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                instruction.setCodified(rs.getBoolean("codified"));
                instruction.setExportToAR(rs.getBoolean("export_to_abr"));
                instruction.setInstructionActive(rs.getBoolean("active"));

                // Add the instruction to the list
                instructions.add(instruction);
            }

            ARLogger.getInstance(ARWebDriver.class)
                    .info(String.format(
                            "Fetched %d Saved instructions for Block ID %d:", instructions.size(), blockId));

        } catch (SQLException e) {
            ARLogger.getInstance(ARWebDriver.class)
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

    private void showAlertTimer(
            Alert.AlertType alertType,
            String title,
            String header,
            String msg1,
            String msg2,
            String msg3,
            String msg4) {
        executorService = Executors.newSingleThreadExecutor();
        alertToShow.setAlertType(alertType);
        alertToShow.setTitle(title);
        alertToShow.setHeaderText(header);
        //        alertToShow.setContentText(content);

        // Remove the border of the DialogPane
        alertToShow.getDialogPane().setStyle("-fx-border-color: transparent; -fx-border-width: 0;");

        // Create VBox to hold multiple Text elements
        VBox allMsgVer = new VBox();
        allMsgVer.setSpacing(10); // Add some spacing between texts
        allMsgVer.setPadding(new Insets(20));

        Text variableText1Styled = new Text(msg1);
        Text variableText2Styled = new Text(msg2);
        Text variableText3Styled = new Text(msg3);
        Text variableText4Styled = new Text(msg4);

        // Set styles based on alert type
        if (alertType.equals(Alert.AlertType.ERROR)) {
            // Change the font color of the title to red
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
        } else {
            // Change the font color of the title to red
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
        }

        // Add Text elements to VBox
        if (msg1 != null && msg2 == null && msg3 == null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled);
        } else if (msg1 != null && msg2 != null && msg3 == null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled);
        } else if (msg1 != null && msg2 != null && msg3 != null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);
        } else {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);
        }

        // Create a StackPane to hold the VBox
        StackPane stackPane = new StackPane(allMsgVer);
        stackPane.setPadding(new Insets(20));

        // Set StackPane content to alert dialog pane
        alertToShow.getDialogPane().setContent(stackPane);

        executorService.execute(() -> {
            timeline.setCycleCount(SECONDS); // Run for seconds
            timeline.play(); // Start the timeline

            // Show the alert on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
        });

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
    }
}
