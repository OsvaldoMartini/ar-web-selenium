package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;
import java.util.Objects;

public final class ScannerInstructionPersistenceDataPorts {
    private ScannerInstructionPersistenceDataPorts() {}

    public static final class InsertDataPort implements ScannerInsertPersistenceService.DataPort {
        private final PerformDataBase performDataBase;

        public InsertDataPort(PerformDataBase performDataBase) {
            this.performDataBase = Objects.requireNonNull(performDataBase, "performDataBase");
        }

        @Override
        public ErrorMessage insertInstructionsBatch(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId) {
            return performDataBase.insertInstructionsBatch(sessionId, instructions, botJobId, blockId, homeBankingId);
        }

        @Override
        public List<Integer> insertedInstructionIds() {
            return performDataBase.getIdsInstrucAfter();
        }

        @Override
        public ErrorMessage insertReferencesBatch(List<InstructionLoad> instructions) {
            return performDataBase.insertReferencesBatch(instructions);
        }
    }

    public static final class UpdateDataPort implements ScannerUpdatePersistenceService.DataPort {
        private final PerformDataBase performDataBase;

        public UpdateDataPort(PerformDataBase performDataBase) {
            this.performDataBase = Objects.requireNonNull(performDataBase, "performDataBase");
        }

        @Override
        public ErrorMessage updateInstructionsBatchByNameAndBlockId(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId) {
            return performDataBase.updateInstructionsBatchByNameAndBlockId(
                    sessionId, instructions, botJobId, blockId, homeBankingId);
        }

        @Override
        public List<Integer> updatedInstructionIds() {
            return performDataBase.getIdsInstrucAfter();
        }

        @Override
        public ErrorMessage upsertReferencesBatch(String sessionId, List<InstructionLoad> instructions) {
            return performDataBase.upsertReferencesBatch(sessionId, instructions);
        }
    }
}
