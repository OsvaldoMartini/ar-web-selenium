package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Row-move pipeline for the COMPONENTS (Bank/Organization) workspace ONLY (tables
 * {@code component_instruction}/{@code component_block}, scope {@code home_banking_id}).
 * Deliberately separated from {@link BotJobRowMoveService} — each workspace owns its own
 * pipeline end-to-end (duplicated by design, so neither can regress the other). Reached via
 * the dedicated {@code COMPONENT_ROW_MOVE} verb (legacy {@code ROW_MOVE} on a component
 * session routes here too).
 *
 * One method per concern, chained by {@link #move}:
 *  1. request validation   — {@link #validateRequestId}
 *  2. idempotency          — {@link #isDuplicate} / {@link #rememberCompleted}
 *  3. revision validation  — {@link #validateRevision}
 *  4. graph validation     — {@link #validateGraph}
 *  5. persistence          — {@link #persist}
 *  6. state refresh        — {@link #refreshState}
 */
public final class ComponentRowMoveService {

    private static final String INSTRUCTION_TABLE = "component_instruction";
    private static final String BLOCK_TABLE = "component_block";

    protected static volatile ComponentRowMoveService instance;

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final InstructionMoveValidator moveValidator = new InstructionMoveValidator();

    /** Completed request ids (bounded), so client retries of an applied move are no-ops. */
    private final Map<String, Boolean> completedRequests = new LinkedHashMap<>();

    private ComponentRowMoveService() {}

    public static ComponentRowMoveService getInstance() {
        if (instance == null) {
            synchronized (ComponentRowMoveService.class) {
                if (instance == null) instance = new ComponentRowMoveService();
            }
        }
        return instance;
    }

    /** Runs the full pipeline. Null = success (duplicates absorb silently, like the legacy inline case). */
    public ErrorMessage move(SplitDTO splitDTO, int homeBankingId) {
        String requestId = splitDTO.getRequestId() == null ? "" : splitDTO.getRequestId().trim();

        ErrorMessage error = validateRequestId(requestId);
        if (error != null) return error;

        if (isDuplicate(requestId)) return null;

        error = validateRevision(splitDTO);
        if (error != null) return error;

        error = validateGraph(splitDTO);
        if (error != null) return error;

        error = persist(homeBankingId, splitDTO);
        if (error != null) return error;

        error = refreshState(homeBankingId);
        if (error != null) return error;

        rememberCompleted(requestId);
        return null;
    }

    private ErrorMessage validateRequestId(String requestId) {
        if (requestId.isEmpty()) {
            return new ErrorMessage(
                    "Move Instruction Refused", "Request ID is required", "Refresh the grid and try again.");
        }
        return null;
    }

    private boolean isDuplicate(String requestId) {
        synchronized (completedRequests) {
            return completedRequests.containsKey(requestId);
        }
    }

    private void rememberCompleted(String requestId) {
        synchronized (completedRequests) {
            completedRequests.put(requestId, Boolean.TRUE);
            while (completedRequests.size() > 256) {
                completedRequests.remove(completedRequests.keySet().iterator().next());
            }
        }
    }

    private ErrorMessage validateRevision(SplitDTO splitDTO) {
        return CommandEditorService.getInstance().validateMoveRevision(splitDTO);
    }

    private ErrorMessage validateGraph(SplitDTO splitDTO) {
        List<InstructionLoad> currentRows = performLists.getListInstructionComp();
        String moveError = moveValidator.validate(currentRows, splitDTO.getUpdatedRows());
        if (moveError != null) {
            return new ErrorMessage("Move Instruction Refused", "Invalid instruction graph", moveError);
        }
        return null;
    }

    private ErrorMessage persist(int homeBankingId, SplitDTO splitDTO) {
        return performDataBase.updateMoveRowsOrder(BLOCK_TABLE, homeBankingId, splitDTO.getUpdatedRows());
    }

    private ErrorMessage refreshState(int homeBankingId) {
        ErrorMessage error = performDataBase.loadInstructions(homeBankingId, -1, -1, INSTRUCTION_TABLE);
        if (error != null) return error;
        return performDataBase.loadBlocks(homeBankingId, "", BLOCK_TABLE);
    }
}
