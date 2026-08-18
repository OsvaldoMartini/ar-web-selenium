package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerUpdatePersistenceServiceTest {

    @Test
    void returnsEmptyWhenThereAreNoInstructions() {
        RecordingData data = new RecordingData(List.of(), null, null);
        ScannerUpdatePersistenceService service = new ScannerUpdatePersistenceService(data);

        ScannerUpdatePersistenceService.Result result = service.persist(new ArrayList<>(), 1, 2, 3);

        assertEquals(ScannerUpdatePersistenceService.Status.EMPTY, result.status());
        assertNull(data.updateCall);
    }

    @Test
    void filtersRowsWithoutIdsAndUpsertsReferencesOnSuccess() {
        RecordingData data = new RecordingData(List.of(20, 21), null, null);
        ScannerUpdatePersistenceService service = new ScannerUpdatePersistenceService(data);
        List<InstructionLoad> rows = rowsWithIds(10, null);

        ScannerUpdatePersistenceService.Result result = service.persist(rows, 1, 2, 3);

        assertEquals(ScannerUpdatePersistenceService.Status.PERSISTED, result.status());
        assertNull(result.error());
        assertEquals(2, result.updatedCount());
        assertEquals(1, rows.size());
        assertEquals(10, rows.get(0).getId());
        assertEquals("update:" + ScannerWorkspaceSessions.BOT_JOB_TASKS + ":1:2:3:2", data.updateCall);
        assertSame(rows, data.referencesRows);
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, data.referencesSessionId);
    }

    @Test
    void skipsReferencesWhenUpdateReturnsError() {
        ErrorMessage error = new ErrorMessage("title", "header", "detail");
        RecordingData data = new RecordingData(List.of(20), error, null);
        ScannerUpdatePersistenceService service = new ScannerUpdatePersistenceService(data);
        List<InstructionLoad> rows = rowsWithIds(null, 11);

        ScannerUpdatePersistenceService.Result result = service.persist(rows, 1, 2, 3);

        assertEquals(ScannerUpdatePersistenceService.Status.PERSISTED, result.status());
        assertSame(error, result.error());
        assertEquals(1, rows.size());
        assertEquals(11, rows.get(0).getId());
        assertNull(data.referencesRows);
    }

    @Test
    void returnsReferenceUpsertError() {
        ErrorMessage error = new ErrorMessage("title", "header", "detail");
        RecordingData data = new RecordingData(List.of(20), null, error);
        ScannerUpdatePersistenceService service = new ScannerUpdatePersistenceService(data);

        ScannerUpdatePersistenceService.Result result = service.persist(rowsWithIds(10), 1, 2, 3);

        assertEquals(ScannerUpdatePersistenceService.Status.PERSISTED, result.status());
        assertSame(error, result.error());
    }

    private static List<InstructionLoad> rowsWithIds(Integer... ids) {
        List<InstructionLoad> rows = new ArrayList<>();
        for (Integer id : ids) {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            rows.add(row);
        }
        return rows;
    }

    private static final class RecordingData implements ScannerUpdatePersistenceService.DataPort {
        private final List<Integer> updatedIds;
        private final ErrorMessage updateError;
        private final ErrorMessage referencesError;
        private String updateCall;
        private String referencesSessionId;
        private List<InstructionLoad> referencesRows;

        private RecordingData(List<Integer> updatedIds, ErrorMessage updateError, ErrorMessage referencesError) {
            this.updatedIds = updatedIds;
            this.updateError = updateError;
            this.referencesError = referencesError;
        }

        @Override
        public ErrorMessage updateInstructionsBatchByNameAndBlockId(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId) {
            assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, sessionId);
            updateCall = "update:" + sessionId + ":" + botJobId + ":" + blockId + ":" + homeBankingId + ":"
                    + instructions.size();
            return updateError;
        }

        @Override
        public List<Integer> updatedInstructionIds() {
            return updatedIds;
        }

        @Override
        public ErrorMessage upsertReferencesBatch(String sessionId, List<InstructionLoad> instructions) {
            referencesSessionId = sessionId;
            referencesRows = instructions;
            return referencesError;
        }
    }
}
