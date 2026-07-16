package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;

/** Persists prepared scanner insert rows and assigns generated instruction ids. */
public final class ScannerInsertPersistenceService {
    private final DataPort data;

    public ScannerInsertPersistenceService(DataPort data) {
        this.data = data;
    }

    public Result persist(List<InstructionLoad> instructions, int botJobId, int blockId, int homeBankingId) {
        if (instructions == null || instructions.isEmpty()) {
            return Result.empty();
        }

        ErrorMessage error = data.insertInstructionsBatch(
                ScannerWorkspaceSessions.BOT_JOB_TASKS, instructions, botJobId, blockId, homeBankingId);
        List<Integer> insertedIds = data.insertedInstructionIds();
        if (instructions.size() != insertedIds.size()) {
            return Result.mismatch(instructions.size(), insertedIds.size());
        }

        if (error == null) {
            for (int i = 0; i < instructions.size(); i++) {
                instructions.get(i).setId(insertedIds.get(i));
            }
            error = data.insertReferencesBatch(instructions);
        }
        return Result.persisted(error);
    }

    public enum Status {
        EMPTY,
        MISMATCH,
        PERSISTED
    }

    public record Result(Status status, ErrorMessage error, int expectedCount, int actualCount) {
        static Result empty() {
            return new Result(Status.EMPTY, null, 0, 0);
        }

        static Result mismatch(int expectedCount, int actualCount) {
            return new Result(Status.MISMATCH, null, expectedCount, actualCount);
        }

        static Result persisted(ErrorMessage error) {
            return new Result(Status.PERSISTED, error, 0, 0);
        }
    }

    public interface DataPort {
        ErrorMessage insertInstructionsBatch(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId);

        List<Integer> insertedInstructionIds();

        ErrorMessage insertReferencesBatch(List<InstructionLoad> instructions);
    }
}
