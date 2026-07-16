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

class ScannerInsertPersistenceServiceTest {

    @Test
    void returnsEmptyWhenThereAreNoInstructions() {
        RecordingData data = new RecordingData(List.of(), null, null);
        ScannerInsertPersistenceService service = new ScannerInsertPersistenceService(data);

        ScannerInsertPersistenceService.Result result = service.persist(new ArrayList<>(), 1, 2, 3);

        assertEquals(ScannerInsertPersistenceService.Status.EMPTY, result.status());
        assertNull(data.insertCall);
    }

    @Test
    void assignsGeneratedIdsAndInsertsReferencesOnSuccess() {
        RecordingData data = new RecordingData(List.of(10, 11), null, null);
        ScannerInsertPersistenceService service = new ScannerInsertPersistenceService(data);
        List<InstructionLoad> rows = rows(2);

        ScannerInsertPersistenceService.Result result = service.persist(rows, 1, 2, 3);

        assertEquals(ScannerInsertPersistenceService.Status.PERSISTED, result.status());
        assertNull(result.error());
        assertEquals(10, rows.get(0).getId());
        assertEquals(11, rows.get(1).getId());
        assertEquals("insert:" + ScannerWorkspaceSessions.BOT_JOB_TASKS + ":1:2:3:2", data.insertCall);
        assertSame(rows, data.referencesRows);
    }

    @Test
    void reportsMismatchWhenGeneratedIdCountDoesNotMatchRows() {
        RecordingData data = new RecordingData(List.of(10), null, null);
        ScannerInsertPersistenceService service = new ScannerInsertPersistenceService(data);

        ScannerInsertPersistenceService.Result result = service.persist(rows(2), 1, 2, 3);

        assertEquals(ScannerInsertPersistenceService.Status.MISMATCH, result.status());
        assertEquals(2, result.expectedCount());
        assertEquals(1, result.actualCount());
        assertNull(data.referencesRows);
    }

    @Test
    void skipsReferencesWhenInsertReturnsError() {
        ErrorMessage error = new ErrorMessage("title", "header", "detail");
        RecordingData data = new RecordingData(List.of(10), error, null);
        ScannerInsertPersistenceService service = new ScannerInsertPersistenceService(data);

        ScannerInsertPersistenceService.Result result = service.persist(rows(1), 1, 2, 3);

        assertEquals(ScannerInsertPersistenceService.Status.PERSISTED, result.status());
        assertSame(error, result.error());
        assertNull(data.referencesRows);
    }

    @Test
    void returnsReferenceInsertError() {
        ErrorMessage error = new ErrorMessage("title", "header", "detail");
        RecordingData data = new RecordingData(List.of(10), null, error);
        ScannerInsertPersistenceService service = new ScannerInsertPersistenceService(data);

        ScannerInsertPersistenceService.Result result = service.persist(rows(1), 1, 2, 3);

        assertEquals(ScannerInsertPersistenceService.Status.PERSISTED, result.status());
        assertSame(error, result.error());
    }

    private static List<InstructionLoad> rows(int count) {
        List<InstructionLoad> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new InstructionLoad());
        }
        return rows;
    }

    private static final class RecordingData implements ScannerInsertPersistenceService.DataPort {
        private final List<Integer> insertedIds;
        private final ErrorMessage insertError;
        private final ErrorMessage referencesError;
        private String insertCall;
        private List<InstructionLoad> referencesRows;

        private RecordingData(List<Integer> insertedIds, ErrorMessage insertError, ErrorMessage referencesError) {
            this.insertedIds = insertedIds;
            this.insertError = insertError;
            this.referencesError = referencesError;
        }

        @Override
        public ErrorMessage insertInstructionsBatch(
                String sessionId,
                List<InstructionLoad> instructions,
                int botJobId,
                int blockId,
                int homeBankingId) {
            assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, sessionId);
            insertCall = "insert:" + sessionId + ":" + botJobId + ":" + blockId + ":" + homeBankingId + ":"
                    + instructions.size();
            return insertError;
        }

        @Override
        public List<Integer> insertedInstructionIds() {
            return insertedIds;
        }

        @Override
        public ErrorMessage insertReferencesBatch(List<InstructionLoad> instructions) {
            referencesRows = instructions;
            return referencesError;
        }
    }
}
