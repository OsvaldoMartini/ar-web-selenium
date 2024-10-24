package com.allinweb.ch.component.listCell;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.ABRScannedElementPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final PerformActions performAction;
    // Static block to initialize
    static {
        performAction = PerformActions.getInstance();
    }

    private static final ABRComponentBuilder builder = new ABRComponentBuilder();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();
    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();

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
                    loadBlocksForBotJob(savedBlocksDTO.getBotJobDTO().getId());

                    BotJobLoadDTO botJobLoadDTO =
                            loadBotJob(savedBlocksDTO.getBotJobDTO().getId());

                    if (botJobLoadDTO == null) {
                        performAction.showAlertError(
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

                    // It Prevents Start without blocks
                    //                    if (blockLoadList.isEmpty()) {

                    BotJobDTO botJob = ABRSharedResources.getInstance()
                            .getEntityById(
                                    BotJobDTO.class,
                                    savedBlocksDTO.getBotJobDTO().getId());
                    if (botJob == null) {
                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .severe("I was not able to load the BotJod id: "
                                        + savedBlocksDTO.getBotJobDTO().getId());

                        performAction.showAlertError(
                                "Error Loading BotJob",
                                "Bot Job Loading Error",
                                "I was not able to load the BotJod id: "
                                        + savedBlocksDTO.getBotJobDTO().getId());

                        return;
                    }

                    // It Prevents Start without blocks

                    savedBlocksDTO.setDescription("Default Block description");
                    savedBlocksDTO.setName("Default Block");
                    BlockDTO blockDTO = BlockDTO.createBlocksDTOFromSavedBlocksDTO(savedBlocksDTO, botJob);
                    blockDTO.setTypeId(1);
                    blockDTO.setBotJob(botJob);
                    blockDTO.setName("Default Block");
                    blockDTO.setDescription("Default Block description");

                    if (botJob != null && blockDTO != null) {

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
                                                                        "The block has been added to the bot job successfully",
                                                                        ButtonType.OK))));

                        ABRSharedResources.getInstance().addEntity(blockDTO, BlockDTO.class);
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

    public List<BlockLoadDTO> loadBlocksForBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT " + "b.id AS block_id, "
                + "b.block_order_number, "
                + "b.name AS block_name, "
                + "b.description AS block_description, "
                + "b.type_id, "
                + "bj.id AS bot_job_id, "
                + "bj.name AS bot_job_name "
                + "FROM bot_job bj "
                + "JOIN block b ON b.bot_job_id = bj.id "
                + "WHERE bj.id = "
                + botJobId + " " + // Use the botJobId directly in the query string
                "ORDER BY b.block_order_number ASC";

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

    public BotJobLoadDTO loadBotJob(int botJobId) {
        // SQL query to get the blocks for a specific bot job
        String query = "SELECT bj.id, "
                + " bj.name, "
                + " bj.description, "
                + " bj.home_banking_id, "
                + " bj.priority "
                + " FROM bot_job bj "
                + " WHERE bj.id = " + botJobId;

        // Initialize the necessary data structures

        // Use Statement to execute the query
        try (Statement stmt = ABRSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            BotJobLoadDTO botJobLoadDTO = new BotJobLoadDTO();

            while (rs.next()) {
                botJobLoadDTO = new BotJobLoadDTO();

                botJobLoadDTO.setId(rs.getInt("id"));
                botJobLoadDTO.setName(rs.getString("name"));
                botJobLoadDTO.setDescription(rs.getString("description"));
                botJobLoadDTO.setPriority(rs.getString("priority"));
                botJobLoadDTO.setHomeBankingId(rs.getInt("home_banking_id"));
            }
            return botJobLoadDTO;

        } catch (SQLException e) {
            ABRLogger.getInstance(Thread.class)
                    .severe(String.format("Error loadBlockAll for botJobId %d\nError: %s", botJobId, e.getMessage()));
        }

        return null;
    }
}
