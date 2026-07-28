package com.allinweb.ch.facade;

import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * ROW_MOVE pipeline for the BOT JOB workspace ONLY (tables {@code instruction}/{@code block},
 * scope {@code bot_job_id}). Deliberately separated from {@link ComponentRowMoveService} —
 * each workspace owns its own pipeline end-to-end (duplicated by design, so neither can
 * regress the other).
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
public final class BotJobRowMoveService {

    private static final String INSTRUCTION_TABLE = "instruction";
    private static final String BLOCK_TABLE = "block";

    protected static volatile BotJobRowMoveService instance;

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();

    /** Completed request ids (bounded), so client retries of an applied move are no-ops. */
    private final Map<String, Boolean> completedRequests = new LinkedHashMap<>();

    private BotJobRowMoveService() {}

    public static BotJobRowMoveService getInstance() {
        if (instance == null) {
            synchronized (BotJobRowMoveService.class) {
                if (instance == null) instance = new BotJobRowMoveService();
            }
        }
        return instance;
    }

    /** Runs the full pipeline. Null = success (duplicates absorb silently, like the legacy inline case). */
    public ErrorMessage move(SplitDTO splitDTO, int botJobId) {
        String requestId = splitDTO.getRequestId() == null ? "" : splitDTO.getRequestId().trim();

        ErrorMessage error = validateRequestId(requestId);
        if (error != null) return error;

        error = validateLayout(splitDTO);
        if (error != null) return error;

        if (isDuplicate(botJobId, requestId)) return null;

        error = validateRevision(splitDTO);
        if (error != null) return error;

        error = persist(botJobId, splitDTO);
        if (error != null) return error;

        // The database mutation is already committed. Remember it before the best-effort
        // in-memory refresh so a refresh failure cannot cause a retry to apply the move twice.
        rememberCompleted(botJobId, requestId);

        return finishCommittedMove(refreshState(botJobId), botJobId);
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

    private boolean isDuplicate(int botJobId, String requestId) {
        synchronized (completedRequests) {
            return completedRequests.containsKey(idempotencyKey(botJobId, requestId));
        }
    }

    private void rememberCompleted(int botJobId, String requestId) {
        synchronized (completedRequests) {
            completedRequests.put(idempotencyKey(botJobId, requestId), Boolean.TRUE);
            while (completedRequests.size() > 256) {
                completedRequests.remove(completedRequests.keySet().iterator().next());
            }
        }
    }

    private String idempotencyKey(int botJobId, String requestId) {
        return botJobId + ":" + requestId;
    }

    private ErrorMessage validateRevision(SplitDTO splitDTO) {
        return CommandEditorService.getInstance().validateMoveRevision(splitDTO);
    }

    private ErrorMessage persist(int botJobId, SplitDTO splitDTO) {
        return performDataBase.updateMoveRowsOrder(BLOCK_TABLE, botJobId, splitDTO.getUpdatedRows(),
                splitDTO.getGraphRevision(), splitDTO.getRowMoveLayoutVersion());
    }

    private ErrorMessage refreshState(int botJobId) {
        ErrorMessage error = performDataBase.loadInstructions(botJobId, -1, -1, INSTRUCTION_TABLE);
        if (error != null) return error;
        return performDataBase.loadBlocks(botJobId, "", BLOCK_TABLE);
    }

    /**
     * Persistence has already committed before this method is called. A cache/snapshot refresh
     * failure must therefore never be reported as a failed mutation: the socket layer will publish
     * an authoritative snapshot or its existing resync-required response.
     */
    static ErrorMessage finishCommittedMove(ErrorMessage refreshError, int botJobId) {
        if (refreshError != null) {
            log.warn(
                    "ROW_MOVE committed for botJobId={}, but the in-memory refresh failed; "
                            + "the client must resynchronize: {}",
                    botJobId,
                    refreshError.getErrorMessage());
        }
        return null;
    }
}
