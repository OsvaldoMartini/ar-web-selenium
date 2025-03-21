package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.pane.ARSaveComponentPane;
import com.allinweb.ch.component.pane.ARScannedElementPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
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

public class ComponentListCell extends ListCell<ComponentBlockDTO> {

    private static final PerformDataBase performDataBase;
    //    private static final PerformDBSavedBlock performDBSavedBlock;
    private static final PerformActions performAction;

    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        //        performDBSavedBlock = PerformDBSavedBlock.getInstance();
        performAction = PerformActions.getInstance();
    }

    private BlockLoadDTO blockLoadDTO;
    private BotJobDTO botJobDTO;

    private static final ARComponentBuilder builder = new ARComponentBuilder();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private List<InstructionLoadDTO> originalLoopInstruction;
    private List<InstructionReferenceLoadDTO> originalReferences;

    protected void updateItem(ComponentBlockDTO componentBlockDTO, boolean empty) {
        super.updateItem(componentBlockDTO, empty);
        // System.out.println(savedBlocksDTO.getName());
        Node graphic = null;
        if (!empty && componentBlockDTO != null) {
            Label nameLabel = new Label(componentBlockDTO.getName());
            nameLabel.setFont(Font.font(null, FontWeight.BOLD, FontPosture.REGULAR, ARConstants.SPACE_SM + 2));
            nameLabel.setWrapText(true);

            Label nameLabel1 = new Label(componentBlockDTO.getDescription());
            nameLabel1.setPrefWidth(150);
            nameLabel1.setWrapText(true);

            VBox nameVBox = new VBox(nameLabel, nameLabel1);
            nameVBox.setMaxWidth(Double.MAX_VALUE);
            nameVBox.setSpacing(ARConstants.SPACE_XS);

            Button appendButton = builder.buildButton(
                    "",
                    ARConstants.SPACE_XXS,
                    ARConstants.ICON_ARROWLEFT,
                    ARConstants.SPACE_M,
                    Insets.EMPTY,
                    Background.fill(Color.TRANSPARENT));
            Button detailButton = builder.buildButton(
                    "",
                    ARConstants.SPACE_XXS,
                    ARConstants.ICON_DOCS,
                    ARConstants.SPACE_M,
                    Insets.EMPTY,
                    Background.fill(Color.TRANSPARENT));
            Button deleteButton = builder.buildButton(
                    "",
                    ARConstants.SPACE_XXS,
                    ARConstants.ICON_BIN,
                    ARConstants.SPACE_M,
                    Insets.EMPTY,
                    Background.fill(Color.TRANSPARENT));

            HBox actionPaneBox = new HBox(appendButton, detailButton, deleteButton);
            actionPaneBox.setAlignment(Pos.TOP_RIGHT);
            actionPaneBox.setSpacing(ARConstants.SPACE_XXS);
            HBox.setHgrow(nameVBox, Priority.ALWAYS);

            StackPane itemPaneBox = new StackPane(nameVBox, actionPaneBox);
            // itemPaneBox.setBackground(Background.fill(Color.RED));
            itemPaneBox.setMaxWidth(Double.MAX_VALUE);
            StackPane.setAlignment(nameVBox, Pos.CENTER_LEFT);
            StackPane.setAlignment(actionPaneBox, Pos.TOP_RIGHT);

            AnchorPane itemPane = new AnchorPane(itemPaneBox);
            // itemPaneBox.setBackground(Background.fill(Color.BLUE));

            //  AnchorPane.setBottomAnchor(actionPaneBox, ARConstants.SPACE_XS);
            // AnchorPane.setTopAnchor(actionPaneBox, ARConstants.SPACE_XS);
            // AnchorPane.setRightAnchor(actionPaneBox, ARConstants.SPACE_XS);

            AnchorPane.setBottomAnchor(itemPaneBox, ARConstants.SPACE_ZERO);
            AnchorPane.setTopAnchor(itemPaneBox, ARConstants.SPACE_ZERO);
            AnchorPane.setLeftAnchor(itemPaneBox, ARConstants.SPACE_ZERO);
            AnchorPane.setRightAnchor(itemPaneBox, ARConstants.SPACE_ZERO);
            setPadding(new Insets(ARConstants.SPACE_XS));
            setBorder(new Border(new BorderStroke(
                    Color.LIGHTGREY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1))));

            detailButton.setOnAction(e -> {
                new ARComponentDetailsScene(componentBlockDTO).show();
            });

            deleteButton.setOnAction(e -> {
                ARSharedResources.getInstance().removeEntity(componentBlockDTO, ComponentBlockDTO.class);
                getListView().refresh();
            });

            appendButton.setOnMouseClicked(e -> {
                //                ARViewBotJobPane currentPane =
                //                        (ARViewBotJobPane) getListView().getUserData();

                Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Are you sure you want to Add the Component Selected to the Bot-Job?",
                        ButtonType.YES,
                        ButtonType.NO);
                ARLogger.getInstance(ARScannedElementPane.class).fine("Confirmation Alert shown. Waiting for result");
                Optional<ButtonType> result = alert.showAndWait();

                ARLogger.getInstance(ARScannedElementPane.class).finer("result got: " + result.get());
                if (result.isPresent() && result.get() == ButtonType.YES) {

                    try {
                        this.blockLoadList = performDataBase.loadBlocksByBotJobId(componentBlockDTO.getHomeBankingId());

                        BotJobLoadDTO botJobLoadDTO =
                                performDataBase.loadBotJobById(componentBlockDTO.getHomeBankingId());

                        if (botJobLoadDTO == null) {
                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Bot Job DOES NOT EXIST",
                                    "Verify the Bot Job Name if have any: ",
                                    String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            componentBlockDTO.getHomeBankingId()));

                            ARLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "Check if you already have a Bot Job \"%\" Created!",
                                            componentBlockDTO.getHomeBankingId()));
                            return;
                        }

                        // It Prevents Start without block
                        //                    if (blockLoadList.isEmpty()) {

                        this.botJobDTO = ARSharedResources.getInstance()
                                .getEntityById(BotJobDTO.class, componentBlockDTO.getHomeBankingId());
                        if (this.botJobDTO == null) {
                            ARLogger.getInstance(ARScannedElementPane.class)
                                    .severe("I was not able to load the BotJod id: "
                                            + componentBlockDTO.getHomeBankingId());

                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Error Loading BotJob",
                                    "Bot Job Loading Error",
                                    "I was not able to load the BotJod id: " + componentBlockDTO.getHomeBankingId());

                            return;
                        }

                        this.blockLoadDTO =
                                new BlockLoadDTO(); // performDBSavedBlock.createBlocksDTOFromSavedBlocksDTO(componentBlockDTO, blockLoadDTO.getHomeBankingId());
                        this.blockLoadDTO.setTypeId(1);
                        this.blockLoadDTO.setActive(componentBlockDTO.getActive());
                        this.blockLoadDTO.setWait(componentBlockDTO.getWait());
                        this.blockLoadDTO.setName(componentBlockDTO.getName());
                        this.blockLoadDTO.setDescription(componentBlockDTO.getDescription());

                        BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
                        newBlockDetails.setBlockName("Default Block");
                        newBlockDetails.setBlockDescription(
                                !Strings.isNullOrEmpty(this.blockLoadDTO.getDescription())
                                        ? this.blockLoadDTO.getDescription()
                                        : "Default Block description");
                        newBlockDetails.setTypeId(1);
                        newBlockDetails.setActive(componentBlockDTO.getActive());
                        newBlockDetails.setWait(componentBlockDTO.getWait());

                        newBlockDetails.setBotJobId(this.blockLoadDTO.getId());

                        int currentBlockId = performDataBase.createNewBlock(newBlockDetails);

                        if (currentBlockId > 0) {
                            this.blockLoadDTO.setId(currentBlockId);

                            ARSharedResources.getInstance().cacheEntitiesFromDB();

                            ARLogger.getInstance(Thread.class)
                                    .info(String.format(
                                            "Component %s has been Added to the BotJob %S",
                                            componentBlockDTO.getName(), componentBlockDTO.getHomeBankingId()));
                        } else {
                            performAction.showAlert(
                                    Alert.AlertType.ERROR,
                                    "Error Getting the Component",
                                    "Not Possible to user the component",
                                    String.format(
                                            "I cannot insert Component %S to BotJob %d\n!",
                                            componentBlockDTO.getName(), componentBlockDTO.getHomeBankingId()));

                            ARLogger.getInstance(Thread.class)
                                    .severe(String.format(
                                            "I cannot insert Component %S to BotJob %d\n!",
                                            componentBlockDTO.getName(), componentBlockDTO.getHomeBankingId()));
                            return;
                        }

                        if (this.botJobDTO != null) {

                            originalLoopInstruction =
                                    new ArrayList<>(); // performDBSavedBlock.createBlockLoopInstructionsFromSavedBlocksDTO(componentBlockDTO);

                            // Debugging: Ensure originalLoopInstruction has the right data
                            ARLogger.getInstance(ComponentListCell.class)
                                    .fine("originalLoopInstruction Size: " + originalLoopInstruction.size());

                            boolean savedInstStatus = false;
                            for (int j = 0; j < originalLoopInstruction.size(); j++) {
                                InstructionLoadDTO task = originalLoopInstruction.get(j);
                                int newId = preFillInstruction(
                                        task.getName(),
                                        task.getDescription(),
                                        task.getActions(),
                                        task.getOperation(),
                                        task.getOnHoldSeconds(),
                                        task.getVariableId(),
                                        task.getInstructionOrderNumber(),
                                        task.getExportToABR(),
                                        task.getXpath(),
                                        this.blockLoadDTO);

                                task.setId(newId);

                                if (newId < 0) {
                                    savedInstStatus = false;
                                } else {
                                    savedInstStatus = true;
                                }
                            }

                            if (savedInstStatus) {
                                ARSharedResources.getInstance().cacheEntitiesFromDB();
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
                                    ARLogger.getInstance(ARSaveComponentPane.class)
                                            .fine("originalReferences Size: " + originalReferences.size());

                                    boolean success = false;
                                    for (InstructionReferenceLoadDTO reference : originalReferences) {

                                        InstructionLoadDTO InstructionLoadDTO = reference.getInstructionLoadDTO();
                                        if (InstructionLoadDTO == null) {
                                            ARLogger.getInstance(ComponentListCell.class)
                                                    .warning("BlockLoopInstructionLoadDTO is null for reference: "
                                                            + reference.getReferenceType());
                                            continue;
                                        }

                                        success = insertComponentReferences(reference, InstructionLoadDTO.getId());
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

                                            ARLogger.getInstance(Thread.class)
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

                                            ARLogger.getInstance(Thread.class)
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
                                ARLogger.getInstance(Task.class).severe("Error Re utilize Component");
                            }

                            //                        ARSharedResources.getInstance()
                            //                                .addEntity(blockDTO, BlockDTO.class, () ->
                            // ARSharedResources.getInstance()
                            //                                        .addAllEntity(
                            //                                                originalLoopInstruction,
                            //                                                BlockLoopInstructionLoadDTO.class,
                            //                                                () -> ARSharedResources.getInstance()
                            //                                                        .addAllEntity(
                            //                                                                references,
                            //
                            // InstructionReferenceDTO.class,
                            //                                                                () -> new ARAlertScene(
                            //
                            // Alert.AlertType.INFORMATION,
                            //                                                                        "Block Added",
                            //                                                                        "The block has
                            // been
                            // added to the bot job successfully",
                            //                                                                        ButtonType.OK))));
                            //
                            //                        ARSharedResources.getInstance().addEntity(blockDTO,
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
                                        componentBlockDTO.getName(), componentBlockDTO.getHomeBankingId()));
                        ARLogger.getInstance(Task.class)
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
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ComponentListCell.class).severe("loadNextIdBlockData  \nError: " + e.getMessage());
        }
        return null;
    }

    private Integer loadNextIdInstructionData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM instruction";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ComponentListCell.class)
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
            boolean exportAR,
            String xPath,
            BlockLoadDTO blockDTOLoad) {

        InstructionLoadDTO instruction = new InstructionLoadDTO();

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
        instruction.setExportToABR(exportAR);
        instruction.setXpath(xPath);

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
                //                ARLogger.getInstance(ComponentListCell.class)
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
                //                ARLogger.getInstance(ComponentListCell.class)
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

            ARLogger.getInstance(ComponentListCell.class)
                    .severe(String.format(
                            "Cannot Insert \"Component\" Instruction \"%s\"\nCannot be saved!\nError: %s",
                            instruction.getName(), e.getMessage()));
        }
        return newId;
    }

    private int insertInstruction(InstructionLoadDTO InstructionLoadDTO) throws SQLException {
        // Generate a Unique-ID for the block

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {

            Integer nextId = loadNextIdInstructionData() + 1;
            InstructionLoadDTO.setId(nextId);

            String coordinates = (InstructionLoadDTO.getCoordinates() != null)
                    ? "'" + InstructionLoadDTO.getCoordinates() + "'"
                    : "";
            String pathValue = (InstructionLoadDTO.getXpath() != null) ? "'" + InstructionLoadDTO.getXpath() + "'" : "";
            String iframeXPath = !Strings.isNullOrEmpty(InstructionLoadDTO.getIFrameXPath())
                    ? "'" + InstructionLoadDTO.getIFrameXPath() + "'"
                    : "";
            String shadowHost = !Strings.isNullOrEmpty(InstructionLoadDTO.getShadowHost())
                    ? "'" + InstructionLoadDTO.getShadowHost() + "'"
                    : "";
            String shadowRoot = !Strings.isNullOrEmpty(InstructionLoadDTO.getShadowRoot())
                    ? "'" + InstructionLoadDTO.getShadowRoot() + "'"
                    : "";
            String cssSelector = !Strings.isNullOrEmpty(InstructionLoadDTO.getCssSelector())
                    ? "'" + InstructionLoadDTO.getCssSelector() + "'"
                    : "";

            // Build the SQL insert query

            String insertSQL = "INSERT INTO instruction(\n" + "id, "
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
                    + "shadow_host, "
                    + "shadow_root, "
                    + "css_selector, "
                    + "variable_id, "
                    + "block_id, "
                    + "bot_job_id, "
                    + "active)\n"
                    + "VALUES ("
                    + InstructionLoadDTO.getId()
                    + ", " + InstructionLoadDTO.getActionCustomMaxWaitSec()
                    + ", '" + InstructionLoadDTO.getActions() + "'"
                    + ", " + InstructionLoadDTO.getBlockMarked()
                    + "," + InstructionLoadDTO.getDefaultValue()
                    + ", '" + InstructionLoadDTO.getDescription() + "'"
                    + ", " + InstructionLoadDTO.getCodified()
                    + ", " + InstructionLoadDTO.getExportToABR()
                    + ", " + InstructionLoadDTO.getInstructionOrderNumber()
                    + ", '" + InstructionLoadDTO.getName() + "'"
                    + ", " + InstructionLoadDTO.getOnHoldSeconds()
                    + ", '" + InstructionLoadDTO.getOperation() + "'"
                    + ", " + InstructionLoadDTO.getOptional()
                    + ", " + InstructionLoadDTO.getParentId()
                    + ", " + pathValue
                    + ", " + coordinates
                    + ", " + iframeXPath
                    + ", " + shadowHost
                    + ", " + shadowRoot
                    + ", " + cssSelector
                    + ", " + InstructionLoadDTO.getVariableId()
                    + ", " + InstructionLoadDTO.getBlockId()
                    + ", " + InstructionLoadDTO.getBotJobId()
                    + ", " + InstructionLoadDTO.getInstructionActive()
                    + ");";

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(ComponentListCell.class)
                        .info(String.format(
                                "New Instruction SAVED SUCCESSFULLY id: %d Name: %s Actions: %s Operation: %s",
                                InstructionLoadDTO.getId(),
                                InstructionLoadDTO.getName(),
                                InstructionLoadDTO.getActions(),
                                InstructionLoadDTO.getOperation()));
                return nextId;
            } else {
                ARLogger.getInstance(ComponentListCell.class)
                        .warning(String.format(
                                "Instruction NOT SAVED id: %d Name: %s Actions: %s Operations: %s",
                                InstructionLoadDTO.getId(),
                                InstructionLoadDTO.getName(),
                                InstructionLoadDTO.getActions(),
                                InstructionLoadDTO.getOperation()));
                return -1;
            }
        }
    }

    private boolean insertComponentReferences(InstructionReferenceLoadDTO referenceDTO, int instructionId) {

        // Generate a Unique-ID for the block
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {

            // Fetch instructionId from savedBlockLoopInstructionLoadDTO

            Integer nextId = loadNextIdBReferenceData() + 1;

            // Build the SQL insert query
            String insertSQL = "INSERT INTO reference(id, reference_type, value, instruction_id) VALUES ("
                    + nextId + ", "
                    + "'" + referenceDTO.getReferenceType() + "', "
                    + "'" + referenceDTO.getValue() + "', " // name
                    + instructionId + ")"; // bot_job_id, assuming BotJobDTO has an ID

            int rowsAffected = stmt.executeUpdate(insertSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(ComponentListCell.class)
                        .info(String.format(
                                "\"COMPONENT\" Instruction Reference SAVED SUCCESSFULLY\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                nextId, referenceDTO.getReferenceType(), referenceDTO.getValue(), instructionId));
            } else {
                ARLogger.getInstance(ComponentListCell.class)
                        .warning(String.format(
                                "\"COMPONENT\" Instruction Reference NOT SAVED\nid: %d\nRef Type: %s\nValue: %s\nInstructionId: %d",
                                nextId, referenceDTO.getReferenceType(), referenceDTO.getValue(), instructionId));
            }

            return true;
        } catch (SQLException e) {
            ARLogger.getInstance(ComponentListCell.class)
                    .severe("Cannot Insert \"COMPONENT\" References\nError " + e.getMessage());
            return false;
        }
    }

    private Integer loadNextIdBReferenceData() {
        //        String selectSQL = "SELECT NEXT_VAL fROM homeBankingSeq";
        String selectSQL = "SELECT MAX(ID) AS max_id FROM reference";
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                return rs.getInt("max_id");
            }
        } catch (SQLException e) {
            ARLogger.getInstance(ComponentListCell.class)
                    .severe("loadNextIdBReferenceData  \nError: " + e.getMessage());
        }
        return null;
    }
}
