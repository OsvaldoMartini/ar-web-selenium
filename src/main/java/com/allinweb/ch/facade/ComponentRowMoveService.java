package com.allinweb.ch.facade;

import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

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
 *  2. layout validation    — {@link #validateLayout}
 *  3. idempotency          — {@link #isDuplicate} / {@link #rememberCompleted}
 *  4. revision validation  — {@link #validateRevision}
 *  5. persistence          — {@link #persist}
 *  6. state refresh        — {@link #refreshState}
 */
@Slf4j
public final class ComponentRowMoveService {

    private static final String INSTRUCTION_TABLE = "component_instruction";
    private static final String BLOCK_TABLE = "component_block";

    protected static volatile ComponentRowMoveService instance;

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();

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

        error = validateLayout(splitDTO);
        if (error != null) return error;

        if (isDuplicate(homeBankingId, requestId)) return null;

        error = validateRevision(splitDTO);
        if (error != null) return error;

        error = persist(homeBankingId, splitDTO);
        if (error != null) return error;

        // The database mutation is already committed. Remember it before the best-effort
        // in-memory refresh so a refresh failure cannot cause a retry to apply the move twice.
        rememberCompleted(homeBankingId, requestId);

        return finishCommittedMove(refreshState(homeBankingId), homeBankingId);
    }

    private ErrorMessage validateRequestId(String requestId) {
        if (requestId.isEmpty()) {
            return new ErrorMessage(
                    "Move Instruction Refused", "Request ID is required", "Refresh the grid and try again.");
        }
        return null;
    }

    private ErrorMessage validateLayout(SplitDTO splitDTO) {
        if (!Integer.valueOf(2).equals(splitDTO.getRowMoveLayoutVersion())) {
            return new ErrorMessage(
                    "Move Instruction Refused", "Unsupported row-move layout", "Refresh the grid and try again.");
        }
        if (splitDTO.getUpdatedRows() == null || splitDTO.getUpdatedRows().isEmpty()) {
            return new ErrorMessage(
                    "Move Instruction Refused",
                    "Complete row layout is required",
                    "Refresh the grid and try again.");
        }
        return null;
    }

    private boolean isDuplicate(int homeBankingId, String requestId) {
        synchronized (completedRequests) {
            return completedRequests.containsKey(idempotencyKey(homeBankingId, requestId));
        }
    }

    private void rememberCompleted(int homeBankingId, String requestId) {
        synchronized (completedRequests) {
            completedRequests.put(idempotencyKey(homeBankingId, requestId), Boolean.TRUE);
            while (completedRequests.size() > 256) {
                completedRequests.remove(completedRequests.keySet().iterator().next());
            }
        }
    }

    private String idempotencyKey(int homeBankingId, String requestId) {
        return homeBankingId + ":" + requestId;
    }

    private ErrorMessage validateRevision(SplitDTO splitDTO) {
        return CommandEditorService.getInstance().validateMoveRevision(splitDTO);
    }

    private ErrorMessage persist(int homeBankingId, SplitDTO splitDTO) {
        return performDataBase.updateMoveRowsOrder(BLOCK_TABLE, homeBankingId, splitDTO.getUpdatedRows(),
                splitDTO.getGraphRevision(), splitDTO.getRowMoveLayoutVersion());
    }

    private ErrorMessage refreshState(int homeBankingId) {
        ErrorMessage error = performDataBase.loadInstructions(homeBankingId, -1, -1, INSTRUCTION_TABLE);
        if (error != null) return error;
        return performDataBase.loadBlocks(homeBankingId, "", BLOCK_TABLE);
    }

    /**
     * Persistence has already committed before this method is called. A cache/snapshot refresh
     * failure must therefore never be reported as a failed mutation: the socket layer will publish
     * an authoritative snapshot or its existing resync-required response.
     */
    static ErrorMessage finishCommittedMove(ErrorMessage refreshError, int homeBankingId) {
        if (refreshError != null) {
            log.warn(
                    "COMPONENT_ROW_MOVE committed for homeBankingId={}, but the in-memory refresh "
                            + "failed; the client must resynchronize: {}",
                    homeBankingId,
                    refreshError.getErrorMessage());
        }
        return null;
    }
}
