package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;

/** Persists prepared scanner update rows and their references. */
public final class ScannerUpdatePersistenceService {
    private final DataPort data;

    public ScannerUpdatePersistenceService(DataPort data) {
        this.data = data;
    }

    public Result persist(List<InstructionLoad> instructions, int botJobId, int blockId, int homeBankingId) {
        if (instructions == null || instructions.isEmpty()) {
            return Result.empty();
        }

        ErrorMessage error = data.updateInstructionsBatchByNameAndBlockId(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, instructions, botJobId, blockId, homeBankingId);
        int updatedCount = data.updatedInstructionIds().size();
        instructions.removeIf(instruction -> instruction.getId() == null);

        if (error == null) {
            error = data.upsertReferencesBatch(ScannerWorkspaceSessions.BOT_JOB_TASKS, instructions);
        }
        return Result.persisted(error, updatedCount);
    }

    public enum Status {
        EMPTY,
        PERSISTED
    }

    public record Result(Status status, ErrorMessage error, int updatedCount) {
        static Result empty() {
            return new Result(Status.EMPTY, null, 0);
        }

        static Result persisted(ErrorMessage error, int updatedCount) {
            return new Result(Status.PERSISTED, error, updatedCount);
        }
    }

    public interface DataPort {
        ErrorMessage updateInstructionsBatchByNameAndBlockId(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId);

        List<Integer> updatedInstructionIds();

        ErrorMessage upsertReferencesBatch(String sessionId, List<InstructionLoad> instructions);
    }
}
