package com.allinweb.ch.socket;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.model.BlockMoveDTO;
import com.allinweb.ch.component.model.BlockOrderDTO;
import com.allinweb.ch.component.model.BlockOrderDetailDTO;
import com.allinweb.ch.component.model.BlockSplitDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.DeleteBlockDTO;
import com.allinweb.ch.component.model.InstructionDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.pane.ABRScannedElementPane;
import com.allinweb.ch.component.scene.ABRExcelFileScene;
import com.allinweb.ch.component.scene.ABRNewCommandScene;
import com.allinweb.ch.component.scene.ABRSaveBlockScene;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SavedBlocksDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ComboBoxVars;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(
        value = "/websocket",
        subprotocols = {"v12.stomp", "v11.stomp", "v10.stomp"}, // Supported STOMP subprotocols
        configurator = StompConfigurator.class // Use the custom configurator
        )
public class WebSocketStompServer {

    private int botJobId;

    // Store all connected sessions
    public static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    private StompHandler stompHandler = new StompHandler();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create(); // Initialize Gson
    private List<BotJobLoadDTO> botLoadJobs = new ArrayList<>();
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();

    private static final PerformDataBase performDataBase;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
    }

    public int getBotJobId() {
        return botJobId;
    }

    public void setBotJobId(int botJobId) {
        this.botJobId = botJobId;
    }

    private void handleMessageByType(String type, String body, Session session) {
        // Dispatch to the correct method based on the message type
        switch (type) {
            case "RESPONSE_BACK":
                BlockSplitDTO responseBack = gson.fromJson(body, BlockSplitDTO.class);
                responseBack.setType("MARTINI");
                String jsonData = gson.toJson(responseBack);
                sendMessageToAll(jsonData);
                break;
            case "BLOCKS_COMPONENT":
                BlockSplitDTO blockComponentDTO = gson.fromJson(body, BlockSplitDTO.class);
                createBlockComponent(blockComponentDTO);
                break;
            case "BLOCKS_SPLITTER":
                BlockSplitDTO blockSplitDTO = gson.fromJson(body, BlockSplitDTO.class);
                splitBlocks(blockSplitDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_MOVE":
                BlockMoveDTO blockMoveDTO = gson.fromJson(body, BlockMoveDTO.class);
                moveBlock(blockMoveDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "ROW_UPDATE":
                RowMoveDTO rowUpdateDTO = gson.fromJson(body, RowMoveDTO.class);
                rowUpdate(rowUpdateDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "ROW_MOVE":
                RowMoveDTO rowMoveDTO = gson.fromJson(body, RowMoveDTO.class);
                if (performDataBase.updateMoveRowsOrder(rowMoveDTO.getUpdatedRows())
                        && rowMoveDTO.getDeleteBlockId() > -1) {
                    performDataBase.deleteBlock(rowMoveDTO.getBotJobId(), rowMoveDTO.getDeleteBlockId());

                    performDataBase.updateBlockOrderNumber(
                            performDataBase.selectAllBlocks(rowMoveDTO.getBotJobId()), true);
                }
                sendMessageToAll("ROW_MOVE");
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "INSERT_BEFORE":
            case "INSERT_AFTER":
            case "INSERT_AFTER_ELSEIF":
            case "INSERT_BEFORE_ELSEIF":
                RowMoveDTO insertBeforeDTO = gson.fromJson(body, RowMoveDTO.class);
                injectStepAfterOrBefore(insertBeforeDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_EXCEL_FILE":
                BlockDetailsDTO blockExcelDTO = gson.fromJson(body, BlockDetailsDTO.class);
                excelFileBlock(blockExcelDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_ORDER":
                BlockOrderDTO blockReorder = gson.fromJson(body, BlockOrderDTO.class);
                if (blockReorder.getUpdatedBlocks().size() > 0) {
                    performDataBase.updateBlockOrderNumber(
                            performDataBase.selectAllBlocks(
                                    blockReorder.getUpdatedBlocks().get(0).getBotJobId()),
                            true);
                    performDataBase.deleteNullBlocks(
                            blockReorder.getUpdatedBlocks().get(0).getBotJobId());

                    ABRSharedResources.getInstance().changeDbConnection();
                }
                break;
            case "INSTRUCTION_STATUS":
                InstructionDTO instructionDTO = gson.fromJson(body, InstructionDTO.class);
                performDataBase.updateInstructionStatus(instructionDTO);

                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "BLOCK_STATUS":
                RowMoveDTO blockStateDTO = gson.fromJson(body, RowMoveDTO.class);
                performDataBase.updateBlockStatus(
                        blockStateDTO.getBotJobId(),
                        blockStateDTO.getBlockId(),
                        blockStateDTO.getBlockName(),
                        blockStateDTO.isBlockActive(),
                        3); // Block wait time Default 3 seconds per block

                performDataBase.updateInstructionStatusByBlock(
                        blockStateDTO.getBotJobId(), blockStateDTO.getBlockId(), blockStateDTO.isBlockActive());

                ABRSharedResources.getInstance().changeDbConnection();

                break;
            case "BLOCK_UPDATE":
                RowMoveDTO blockUpdateDTO = gson.fromJson(body, RowMoveDTO.class);
                performDataBase.updateBlockName(
                        blockUpdateDTO.getBotJobId(), blockUpdateDTO.getBlockId(), blockUpdateDTO.getBlockName());
                ABRSharedResources.getInstance().changeDbConnection();

                break;
            case "DELETE_INSTRUCTION":
                InstructionDTO deleteInstructionDTO = gson.fromJson(body, InstructionDTO.class);
                performDataBase.deleteInstruction(deleteInstructionDTO.getBotJobId(), deleteInstructionDTO);

                List<InstructionDTO> rowList = performDataBase.getInstructionsByBlockId(
                        deleteInstructionDTO.getBotJobId(), deleteInstructionDTO.getBlockId());
                performDataBase.reorderInstructions(rowList);
                ABRSharedResources.getInstance().changeDbConnection();
                break;
            case "DELETE_BLOCK":
                DeleteBlockDTO deleteBlockDTO = gson.fromJson(body, DeleteBlockDTO.class);
                performDataBase.deleteBlock(deleteBlockDTO);
                ABRSharedResources.getInstance().changeDbConnection();
                sendMessageToAll("deleteBlock");
                break;
            case "BLOCK_ROLLBACK":
                RollBackBlocksDTO rollBackBlocksDTO = gson.fromJson(body, RollBackBlocksDTO.class);
                performDataBase.rollBackBlocksRows(rollBackBlocksDTO);
                performDataBase.deleteNullBlocks(rollBackBlocksDTO.getBotJobId());
                ABRSharedResources.getInstance().changeDbConnection();
                break;

            default:
                //                    System.err.println("Unknown message type: " + type);
                break;
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        // Check if the session is already in the sessions set before adding it
        if (!sessions.contains(session)) {
            sessions.add(session);
            ABRLogger.getInstance(WebSocketStompServer.class)
                    .info(String.format("Open Socket Connection - Session Id: %s", session.getId()));
        } else {
            ABRLogger.getInstance(WebSocketStompServer.class)
                    .info(String.format("Reusing existing Socket Connection - Session Id: %s", session.getId()));
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Parse the incoming STOMP frame
            StompFrame frame = StompParser.parse(message);

            // Handle the STOMP frame (e.g., CONNECT, SEND, SUBSCRIBE)
            stompHandler.handleFrame(frame, session);

            // Extract and handle the message type
            String type = extractType(frame.getBody());
            handleMessageByType(type, frame.getBody(), session);

            // Send a ping to keep the connection alive
            session.getAsyncRemote().sendPing(ByteBuffer.wrap(new byte[0]));
        } catch (IOException e) {
            ABRLogger.getInstance(WebSocketStompServer.class)
                    .warning(String.format("onMessage - IO Error: %s", e.getMessage()));
        } catch (Exception e) {
            ABRLogger.getInstance(WebSocketStompServer.class)
                    .warning(String.format("onMessage - Error: %s", e.getMessage()));
        }
    }

    public static void sendMessageToAll(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    try {
                        String stompMessage = "MESSAGE\ndestination:/topic/messages\ncontent-length:" + message.length()
                                + "\n\n" + message + "\u0000";
                        session.getBasicRemote().sendText(stompMessage);
                        ABRLogger.getInstance(WebSocketStompServer.class)
                                .info(String.format("Sent message to session %s: %s", session.getId(), message));
                    } catch (IOException e) {
                        ABRLogger.getInstance(WebSocketStompServer.class)
                                .warning(String.format("sendMessageToAll - IO Error: %s", e.getMessage()));
                    }
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        ABRLogger.getInstance(WebSocketStompServer.class)
                .warning(String.format("WebSocket error: %s", throwable.getMessage()));
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            ABRLogger.getInstance(WebSocketStompServer.class)
                    .warning(String.format("onError - IO Error: %s", e.getMessage()));
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        ABRLogger.getInstance(WebSocketStompServer.class)
                .info(String.format("Client disconnected: %s", session.getId()));
    }

    private void excelFileBlock(BlockDetailsDTO blockExcelDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
        Platform.runLater(() -> {
            ABRExcelFileScene excelFileScene = new ABRExcelFileScene(blockExcelDTO);
            excelFileScene.showModal();
        });
    }

    // Method to extract the type field from a JSON string
    public static String extractType(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has("type")) {
                return jsonObject.get("type").getAsString();
            } else {
                return "Unknown type";
            }
        } catch (Exception e) {
            return "Invalid JSON";
        }
    }

    private void createBlockComponent(BlockSplitDTO blockSplitDTO) {
        // Ensure JavaFX UI updates are done on the JavaFX Application Thread
        SavedBlocksDTO savedBlocksDTO = performDataBase.createSavedBlockDTO(blockSplitDTO);

        BlockDTO blockDTO = new BlockDTO();
        blockDTO.setId(blockSplitDTO.getDetails().getNewBlock().getBlockId());
        //        blockDTO.setBotJob(blockSplitDTO.getDetails().getNewBlock().getBotJobId());

        Platform.runLater(() -> {
            ABRSaveBlockScene newSaveBlockScene = new ABRSaveBlockScene(
                    savedBlocksDTO, blockDTO, blockSplitDTO.getDetails().getNewBlock());
            newSaveBlockScene.showModal();
        });
    }

    // Handle BLOCKS_SPLITTED message
    private void splitBlocks(BlockSplitDTO blockSplitDTO) {
        BlockDetailsDTO originalBlock = blockSplitDTO.getDetails().getOriginalBlock();
        BlockDetailsDTO newBlock = blockSplitDTO.getDetails().getNewBlock();
        List<BlockOrderDetailDTO> updatedBlock = blockSplitDTO.getDetails().getUpdatedBlocks();
        System.out.println("Original Block ID: " + originalBlock.getBlockId());
        System.out.println("New Block Name: " + newBlock.getBlockName());
        System.out.println("Updated Block: " + updatedBlock.size());
        newBlock.setForceOrder(true);

        int newBlockId = performDataBase.createNewBlock(newBlock);
        if (performDataBase.updateInstructionsSplitter(
                newBlock.getInstructions(), (int) originalBlock.getBlockId(), newBlockId)) {
            if (updatedBlock.size() > 0) {
                performDataBase.updateBlockOrderNumber(updatedBlock, false);
                //                performDataBase.updateBlockOrderNumber(
                //                        performDataBase.selectAllBlocks(updatedBlock.get(0).getBotJobId()), true);
            }
        }

        sendMessageToAll("splitBlocks");
    }

    // Handle BLOCK_MOVE message
    private void moveBlock(BlockMoveDTO blockMoveDTO) {
        List<BlockOrderDetailDTO> updatedBlocks = blockMoveDTO.getUpdatedBlocks();
        performDataBase.updateBlockOrderNumber(updatedBlocks, false);
    }

    // Handle ROW_MOVE message
    private void rowUpdate(RowMoveDTO rowUpdateDTO) {
        if (rowUpdateDTO.getUpdatedRows().size() > 0) {
            performDataBase.rowsUpdateName(rowUpdateDTO.getUpdatedRows());
        }

        // Add business logic to handle ROW_MOVE
    }

    private void injectStepAfterOrBefore(RowMoveDTO rowMoveDTO) {

        if (rowMoveDTO.getUpdatedRows().size() > 0) {

            //            List<BotJobLoadDTO> botJobLoadList =
            // performDataBase.loadBotJobComplete(rowMoveDTO.getBotJobId());
            BotJobLoadDTO botJobLoad = performDataBase.loadBotJobById(rowMoveDTO.getBotJobId());

            if (!rowMoveDTO.getType().equals("INSERT_BEFORE_ELSEIF")
                    && !rowMoveDTO.getType().equals("INSERT_AFTER_ELSEIF")) {

                this.webPageItems = performDataBase.loadWebPageFields(rowMoveDTO.getBotJobId());

                // Ensure JavaFX UI updates are done on the JavaFX Application Thread
                Platform.runLater(() -> {
                    ABRNewCommandScene newCommandScene =
                            new ABRNewCommandScene(rowMoveDTO, botJobLoad, this.webPageItems);
                    newCommandScene.showModal();
                });
            } else {

                if (rowMoveDTO.getUpdatedRows().size() > 0) {

                    int parentId = rowMoveDTO.getUpdatedRows().get(0).getParentId();
                    try {
                        // Run the instruction add in a separate Task

                        int newRowId = performDataBase.preFillInstruction(
                                "ELSEIF",
                                "ELSEIF",
                                ABRConstants.ELSEIF,
                                ABRConstants.ELSEIF,
                                1,
                                null,
                                null,
                                parentId,
                                rowMoveDTO,
                                botJobLoad,
                                false);

                    } catch (Exception e) {

                        ABRLogger.getInstance(ABRScannedElementPane.class)
                                .severe(String.format(
                                        "Cannot Insert \"Instruction\"  \"%s\"\nCannot be saved!\nError: %s",
                                        ABRConstants.ELSEIF, e.getMessage()));
                    }
                }
            }
        }
    }

    // Handle DELETE_BLOCK message

    // Dummy method to simulate fetching the BotJobDTO (replace with actual logic)
    private BotJobDTO fetchBotJob() {
        // Replace this with actual logic to fetch the associated BotJobDTO
        return new BotJobDTO(); // Example placeholder
    }
}
