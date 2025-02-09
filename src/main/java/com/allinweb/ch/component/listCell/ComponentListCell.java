package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.ABRSaveBlockPane;
import com.allinweb.ch.component.pane.ABRScannedElementPane;
import com.allinweb.ch.component.pane.ABRViewBotJobPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDBSavedBlock;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.google.common.base.Strings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.concurrent.Task;
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

    private static final PerformDataBase performDataBase;
    private static final PerformDBSavedBlock performDBSavedBlock;
    private static final PerformActions performAction;

    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        performDBSavedBlock = PerformDBSavedBlock.getInstance();
        performAction = PerformActions.getInstance();
    }

    private BlockLoadDTO blockLoadDTO;
    private BotJobDTO botJobDTO;

    private static final ABRComponentBuilder builder = new ABRComponentBuilder();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private List<BlockLoopInstructionLoadDTO> originalLoopInstruction;
    private List<InstructionReferenceLoadDTO> originalReferences;

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
                //                ABRViewBotJobPane currentPane =
                //                        (ABRViewBotJobPane) getListView().getUserData();

                Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Are you sure you want to Add the Component Selected to the Bot-Job?",
                        ButtonType.YES,
                        ButtonType.NO);
                ABRLogger.getInstance(ABRScannedElementPane.class).fine("Confirmation Alert shown. Waiting for result");
                Optional<ButtonType> result = alert.showAndWait();

                ABRLogger.getInstance(ABRScannedElementPane.class).finer("result got: " + result.get());
                if (result.isPresent() && result.get() == ButtonType.YES) {

                    try {
                        this.blockLoadList = performDataBase.loadBlocksByBotJobId(
                                savedBlocksDTO.getBotJobDTO().getId());

                        BotJobLoadDTO botJobLoadDTO = performDataBase.loadBotJobById(
                                savedBlocksDTO.getBotJobDTO().getId());

                        if (botJobLoadDTO == null) {
                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Bot Job DOES NOT EXIST",
                                    "Verify the Bot Job Name if have any: ",
                                    String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            savedBlocksDTO.getBotJobDTO().getName()));

                            ABRLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            savedBlocksDTO.getBotJobDTO().getName()));
                            return;
                        }

                        // It Prevents Start without block
                        //                    if (blockLoadList.isEmpty()) {

                        this.botJobDTO = ABRSharedResources.getInstance()
                                .getEntityById(
                                        BotJobDTO.class,
                                        savedBlocksDTO.getBotJobDTO().getId());
                        if (this.botJobDTO == null) {
                            ABRLogger.getInstance(ABRScannedElementPane.class)
                                    .severe("I was not able to load the BotJod id: "
                                            + savedBlocksDTO.getBotJobDTO().getId());

                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Error Loading BotJob",
                                    "Bot Job Loading Error",
                                    "I was not able to load the BotJod id: "
                                            + savedBlocksDTO.getBotJobDTO().getId());

                            return;
                        }

                        this.blockLoadDTO = performDBSavedBlock.createBlocksDTOFromSavedBlocksDTO(
                                savedBlocksDTO, blockLoadDTO.getBotJobId());
                        this.blockLoadDTO.setTypeId(1);
                        this.blockLoadDTO.setActive(savedBlocksDTO.getActive());
                        this.blockLoadDTO.setWait(savedBlocksDTO.getWait());
                        this.blockLoadDTO.setName(savedBlocksDTO.getName());
                        this.blockLoadDTO.setDescription(savedBlocksDTO.getDescription());

                        BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
                        newBlockDetails.setBlockName("Default Block");
                        newBlockDetails.setBlockDescription(
                                !Strings.isNullOrEmpty(this.blockLoadDTO.getDescription())
                                        ? this.blockLoadDTO.getDescription()
                                        : "Default Block description");
                        newBlockDetails.setTypeId(1);
                        newBlockDetails.setActive(savedBlocksDTO.getActive());
                        newBlockDetails.setWait(savedBlocksDTO.getWait());

                        newBlockDetails.setBotJobId(this.blockLoadDTO.getId());

                        int currentBlockId = performDataBase.createNewBlock(newBlockDetails);

                        if (currentBlockId > 0) {
                            this.blockLoadDTO.setId(currentBlockId);

                            ABRSharedResources.getInstance().cacheEntitiesFromDB();

                            ABRLogger.getInstance(Thread.class)
                                    .info(String.format(
                                            "Component %s has been Added to the BotJob %S",
                                            savedBlocksDTO.getName(),
                                            savedBlocksDTO.getBotJobDTO().getName()));
                        } else {
                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Error Getting the Component",
                                    "Not Possible to user the component",
                                    String.format(
                                            "Error Trying to Insert Component %S to BotJob %d\n!",
                                            savedBlocksDTO.getName(),
                                            savedBlocksDTO.getBotJobDTO().getName()));

                            ABRLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "Error Trying to Insert Component %S to BotJob %d\n!",
                                            savedBlocksDTO.getName(),
                                            savedBlocksDTO.getBotJobDTO().getName()));
                            return;
                        }

                        if (this.botJobDTO != null) {

                            originalLoopInstruction =
                                    performDBSavedBlock.createBlockLoopInstructionsFromSavedBlocksDTO(savedBlocksDTO);

                            // Debugging: Ensure originalLoopInstruction has the right data
                            ABRLogger.getInstance(ComponentListCell.class)
                                    .fine("originalLoopInstruction Size: " + originalLoopInstruction.size());

                            boolean savedInstStatus = false;
                            for (int j = 0; j < originalLoopInstruction.size(); j++) {
                                BlockLoopInstructionLoadDTO task = originalLoopInstruction.get(j);
                                int newId = preFillInstruction(
                                        task.getName(),
                                        task.getDescription(),
                                        task.getActions(),
                                        task.getOperation(),
                                        task.getOnHoldSeconds(),
                                        task.getVariableId(),
                                        task.getInstructionOrderNumber(),
                                        task.getExportToABR(),
                                        task.getPath(),
                                        this.blockLoadDTO);

                                task.setId(newId);

                                if (newId < 0) {
                                    savedInstStatus = false;
                                } else {
                                    savedInstStatus = true;
                                }
                            }

                            if (savedInstStatus) {
                                ABRSharedResources.getInstance().cacheEntitiesFromDB();
                            } else {
                                return;
                            }

                            try {

                                // Build References
                                originalReferences = new ArrayList<>();
                                originalLoopInstruction.forEach(instruction -> {
                                    originalReferences.addAll(instruction.getInstructionReferenceLoadDTOList());
                                    instruction.setInstructionReferenceLoadDTOList(null);
                                });

                                if (savedInstStatus && originalReferences.size() > 0) {
                                    ABRLogger.getInstance(ABRSaveBlockPane.class)
                                            .fine("originalReferences Size: " + originalReferences.size());

                                    boolean success = false;
                                    for (InstructionReferenceLoadDTO reference : originalReferences) {

                                        BlockLoopInstructionLoadDTO instructionDTO =
                                                reference.getBlockLoopInstructionLoadDTO();
                                        if (instructionDTO == null) {
                                            ABRLogger.getInstance(ABRViewBotJobPane.class)
                                                    .warning("BlockLoopInstructionDTO is null for reference: "
                                                            + reference.getReferenceType());
                                            continue;
                                        }

                                        success = insertComponentReferences(reference, instructionDTO.getId());
                                        if (!success) {
                                            break;
                                        }
                                    }
                                    final boolean successFinal = success;
                                    Platform.runLater(() -> {
                                        if (successFinal) {
                                            performAction.showAlert(
                                                    Alert.AlertType.INFORMATION,
                                                    "Re utilize Component",
                                                    "Component was Added",
                                                    String.format(
                                                            "Re utilize Component:\n" + "Added Block Name: %s"
                                                                    + "" + "\nAdded %d Instructions"
                                                                    + "\nAdded %d references locators",
                                                            this.blockLoadDTO.getName(),
                                                            originalLoopInstruction.size(),
                                                            originalReferences.size()));

                                            ABRLogger.getInstance(Thread.class)
                                                    .info(String.format(
                                                            "Re utilize Component:\n" + "Added Block Name: %s"
                                                                    + "" + "\nAdded %d Instructions"
                                                                    + "\nAdded %d references locators",
                                                            this.blockLoadDTO.getName(),
                                                            originalLoopInstruction.size(),
                                                            originalReferences.size()));

                                        } else {
                                            performAction.showAlert(
                                                    Alert.AlertType.ERROR,
                                                    "Error Re  utilize Web Components",
                                                    "Re utilize Web Components FAILED",
                                                    String.format(
                                                            "ERROR: Re utilize Component:\n"
                                                                    + "Block Name: %s\nWAS NOT INCLUDED"
                                                                    + "\nWAS NOT INCLUDED- %d Instructions"
                                                                    + "\nWAS NOT INCLUDED -  %d references locators",
                                                            this.blockLoadDTO.getName(),
                                                            originalLoopInstruction.size(),
                                                            originalReferences.size()));

                                            ABRLogger.getInstance(Thread.class)
                                                    .severe(String.format(
                                                            "ERROR: Re utilize Component:\n"
                                                                    + "Block Name: %s\nWAS NOT INCLUDED"
                                                                    + "\nWAS NOT INCLUDED- %d Instructions"
                                                                    + "\nWAS NOT INCLUDED -  %d references locators",
                                                            this.blockLoadDTO.getName(),
                                                            originalLoopInstruction.size(),
                                                            originalReferences.size()));

                                            return;
                                        }
                                    });
                                }
                            } catch (Exception ex) {
                                ABRLogger.getInstance(Task.class).severe("Error Re utilize Component");
                            }

                            //                        ABRSharedResources.getInstance()
                            //                                .addEntity(blockDTO, BlockDTO.class, () ->
                            // ABRSharedResources.getInstance()
                            //                                        .addAllEntity(
                            //                                                originalLoopInstruction,
                            //                                                BlockLoopInstructionDTO.class,
                            //                                                () -> ABRSharedResources.getInstance()
                            //                                                        .addAllEntity(
                            //                                                                references,
                            //
                            // InstructionReferenceDTO.class,
                            //                                                                () -> new ABRAlertScene(
                            //
                            // Alert.AlertType.INFORMATION,
                            //                                                                        "Block Added",
                            //                                                                        "The block has
                            // been
                            // added to the bot job successfully",
                            //                                                                        ButtonType.OK))));
                            //
                            //                        ABRSharedResources.getInstance().addEntity(blockDTO,
                            // BlockDTO.class);
                        }
                    } catch (Exception ex) {
                        // Handle the exception and display a warning message on the JavaFX Application Thread
                        performAction.showAlert(
                                Alert.AlertType.ERROR,
                                "Error Getting Component",
                                "Unable to Utilize the Component",
                                String.format(
                                        "Error: Unable to Utilize the Component Name : %s\nBotJob Name: %s\nPlease try again!",
                                        savedBlocksDTO.getName(),
                                        savedBlocksDTO.getBotJobDTO().getName()));
                        ABRLogger.getInstance(Task.class)
                                .severe("Error: Unable to save the block. Please try again.\nError: "
                                        + ex.getMessage());
                    }
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

    private Integer loadNextIdBlockData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block";
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

    private Integer loadNextIdInstructionData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM block_loop_instruction";
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

    private int preFillInstruction(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            Integer varId,
            Integer instructionOrderNumber,
            boolean exportABR,
            String xPath,
            BlockLoadDTO blockDTOLoad) {

        BlockLoopInstructionLoadDTO instruction = new BlockLoopInstructionLoadDTO();

        instruction.setName(name);

        instruction.setCodified(false);
        instruction.setExportToABR(true);
        instruction.setInstructionActive(true);

        instruction.setInstructionOrderNumber(instructionOrderNumber);

        instruction.setOptional(false);

        instruction.setOperation(operation);
        instruction.setActions(actions);
        instruction.setDescription(description);

        instruction.setVariableId(varId);

        instruction.setActionCustomMaxWaitSec(30);
        instruction.setOnHoldSeconds(onHold);
        instruction.setBlockId(blockLoadDTO.getId());
        instruction.setExportToABR(exportABR);
        instruction.setPath(xPath);

        // Wrap the persistence in a try-catch block
        int newId = -1;

        try {
            newId = insertInstruction(instruction);

            if (newId > 0) {
                //                performAction.showAlert(
                //                        Alert.AlertType.INFORMATION,
                //                        "Add New \"Component\" Instruction",
                //                        "Component Instruction Added",
                //                        String.format(
                //                                "\"Component\" Instruction \"%s\"\nhas been added successfully!",
                //                                instruction.getName()));
                //
                //                ABRLogger.getInstance(ABRViewBotJobPane.class)
                //                        .info(String.format(
                //                                "\"Component\" Instruction: \"%s\"\nhas been added successfully!",
                //                                instruction.getName()));
            } else {
                //                performAction.showAlert(
                //                        Alert.AlertType.ERROR,
                //                        "Error Add New \"Component\" Instruction",
                //                        "Not possible to insert new Operation",
                //                        String.format("\"Component\" Instruction \"%s\"\nCannot be saved",
                // instruction.getName()));
                //                ABRLogger.getInstance(ABRViewBotJobPane.class)
                //                        .severe(String.format(
                //                                "Error Add New \"Component\" Instruction: \"%s\"\nCannot be saved!",
                //                                instruction.getName()));
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
        return newId;
    }

    private int insertInstruction(BlockLoopInstructionLoadDTO instructionDTO) throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            Integer nextId = loadNextIdInstructionData() + 1;
            instructionDTO.setId(nextId);

            String coordinates =
                    (instructionDTO.getCoordinates() != null) ? "'" + instructionDTO.getCoordinates() + "'" : "";
            String pathValue = (instructionDTO.getPath() != null) ? "'" + instructionDTO.getPath() + "'" : "";
            String iframeXPath = !Strings.isNullOrEmpty(instructionDTO.getIFrameXPath())
                    ? "'" + instructionDTO.getIFrameXPath() + "'"
                    : "";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO block_loop_instruction(\n" + "id, "
                    + "action_custom_max_wait_sec, "
                    + "actions, "
                    + "block_marked, "
                    + "defaultValue, "
                    + "description, "
                    + "codified, "
                    + "export_to_abr, "
                    + "instruction_order_number, "
                    + "name, "
                    + "on_hold_seconds, "
                    + "operation, "
                    + "optional, "
                    + "parent_id, "
                    + "path, "
                    + "coordinates, "
                    + "iframe_xpath, "
                    + "variable_id, "
                    + "block_id, "
                    + "bot_job_id, "
                    + "active)\n"
                    + "VALUES ("
                    + instructionDTO.getId()
                    + ", " + instructionDTO.getActionCustomMaxWaitSec()
                    + ", '" + instructionDTO.getActions() + "'"
                    + ", " + instructionDTO.getBlockMarked()
                    + "," + instructionDTO.getDefaultValue()
                    + ", '" + instructionDTO.getDescription() + "'"
                    + ", " + instructionDTO.getCodified()
                    + ", " + instructionDTO.getExportToABR()
                    + ", " + instructionDTO.getInstructionOrderNumber()
                    + ", '" + instructionDTO.getName() + "'"
                    + ", " + instructionDTO.getOnHoldSeconds()
                    + ", '" + instructionDTO.getOperation() + "'"
                    + ", " + instructionDTO.getOptional()
                    + ", " + instructionDTO.getParentId()
                    + ", " + pathValue
                    + ", " + coordinates
                    + ", " + iframeXPath
                    + ", " + instructionDTO.getVariableId()
                    + ", " + instructionDTO.getBlockId()
                    + ", " + instructionDTO.getBotJobId()
                    + ", " + instructionDTO.getInstructionActive()
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ABRLogger.getInstance(ComponentListCell.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY id: %d Name: %s Actions: %s Operation: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return nextId;
            } else {
                ABRLogger.getInstance(ComponentListCell.class)
                        .warning(String.format(
                                "Instruction NOT SAVED id: %d Name: %s Actions: %s Operations: %s",
                                instructionDTO.getId(),
                                instructionDTO.getName(),
                                instructionDTO.getActions(),
                                instructionDTO.getOperation()));
                return -1;
            }
        }
    }

    private boolean insertComponentReferences(InstructionReferenceLoadDTO referenceDTO, int instructionId) {

        // Generate a Unique-ID for the block
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement()) {

            // Fetch instructionId from savedBlockLoopInstructionDTO

            Integer nextId = loadNextIdBReferenceData() + 1;

            // Build the SQL insert query
            String insertSQL =
                    "INSERT INTO instruction_reference(id, reference_type, value, block_loop_instruction_id) VALUES ("
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
}
