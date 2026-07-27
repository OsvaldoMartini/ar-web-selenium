package com.allinweb.ch.facade;

import com.allinweb.ch.model.ErrorMessage;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ROW_MOVE pipeline, extracted verbatim from SimpleWebSocketServer's message switch and
 * split by concern. One public orchestrator ({@link #move}) chains small single-purpose
 * steps; each step owns exactly one responsibility:
 *
 *  1. request validation   — a request id must be present ({@link #validateRequestId})
 *  2. idempotency          — duplicate retries are absorbed ({@link #isDuplicate}/{@link #rememberCompleted})
 *  3. revision validation  — the client's graph revision must be current ({@link #validateRevision})
 *  4. graph validation     — the proposed layout must be structurally legal ({@link #validateGraph})
 *  5. persistence          — the verified layout is written transactionally ({@link #persist})
 *  6. state refresh        — in-memory lists are reloaded from the database ({@link #refreshState})
 *
 * Behavior is identical to the original inline case, for BOTH workspaces:
 * Bot Job (instruction/block, whereId = botJobId) and Components
 * (component_instruction/component_block, whereId = homeBankingId).
 */
public final class RowMoveService {

    protected static volatile RowMoveService instance;

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final InstructionMoveValidator moveValidator = new InstructionMoveValidator();

    /** Completed request ids (bounded), so client retries of an applied move are no-ops. */
    private final Map<String, Boolean> completedRequests = new LinkedHashMap<>();

    private RowMoveService() {}

    public static RowMoveService getInstance() {
        if (instance == null) {
            synchronized (RowMoveService.class) {
                if (instance == null) instance = new RowMoveService();
            }
        }
        return instance;
    }

    /**
     * Runs the full ROW_MOVE pipeline. Returns null on success — including the duplicate-request
     * case, which is silently absorbed exactly like the original inline handling.
     */
    public ErrorMessage move(SplitDTO splitDTO, String instrTable, String blockTable, int whereId) {
        String requestId = splitDTO.getRequestId() == null ? "" : splitDTO.getRequestId().trim();

        ErrorMessage error = validateRequestId(requestId);
        if (error != null) return error;

        if (isDuplicate(requestId)) return null;

        error = validateRevision(splitDTO);
        if (error != null) return error;

        error = validateGraph(currentRows(instrTable), splitDTO);
        if (error != null) return error;

        error = persist(blockTable, whereId, splitDTO);
        if (error != null) return error;

        error = refreshState(whereId, instrTable, blockTable);
        if (error != null) return error;

        rememberCompleted(requestId);
        return null;
    }

    // ── 1. request validation ─────────────────────────────────────────────────────────────

    private ErrorMessage validateRequestId(String requestId) {
        if (requestId.isEmpty()) {
            return new ErrorMessage(
                    "Move Instruction Refused", "Request ID is required", "Refresh the grid and try again.");
        }
        return null;
    }

    // ── 2. idempotency ────────────────────────────────────────────────────────────────────

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

    // ── 3. revision validation ────────────────────────────────────────────────────────────

    private ErrorMessage validateRevision(SplitDTO splitDTO) {
        return CommandEditorService.getInstance().validateMoveRevision(splitDTO);
    }

    // ── 4. graph validation ───────────────────────────────────────────────────────────────

    private List<InstructionLoad> currentRows(String instrTable) {
        return instrTable.equals("instruction")
                ? performLists.getListInstruction()
                : performLists.getListInstructionComp();
    }

    private ErrorMessage validateGraph(List<InstructionLoad> currentRows, SplitDTO splitDTO) {
        String moveError = moveValidator.validate(currentRows, splitDTO.getUpdatedRows());
        if (moveError != null) {
            return new ErrorMessage("Move Instruction Refused", "Invalid instruction graph", moveError);
        }
        return null;
    }

    // ── 5. persistence ────────────────────────────────────────────────────────────────────

    private ErrorMessage persist(String blockTable, int whereId, SplitDTO splitDTO) {
        return performDataBase.updateMoveRowsOrder(blockTable, whereId, splitDTO.getUpdatedRows());
    }

    // ── 6. state refresh ──────────────────────────────────────────────────────────────────

    private ErrorMessage refreshState(int whereId, String instrTable, String blockTable) {
        ErrorMessage error = performDataBase.loadInstructions(whereId, -1, -1, instrTable);
        if (error != null) return error;
        return performDataBase.loadBlocks(whereId, "", blockTable);
    }
}
