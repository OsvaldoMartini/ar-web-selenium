package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerSearchCleanupServiceTest {
    private final ScannerSearchCleanupService service = new ScannerSearchCleanupService();

    @Test
    void beforeSearchResetsFrameWhenDriverExistsAndClearsInjections() {
        RecordingOperations operations = new RecordingOperations(true);

        service.beforeSearch(operations);

        assertEquals(List.of("hasDriver", "defaultContent", "clearXPath", "revertClone", "revertPick"), operations.calls);
    }

    @Test
    void beforeSearchSkipsFrameResetWithoutDriver() {
        RecordingOperations operations = new RecordingOperations(false);

        service.beforeSearch(operations);

        assertEquals(List.of("hasDriver", "clearXPath", "revertClone", "revertPick"), operations.calls);
    }

    @Test
    void afterSearchWaitsAndRevertsSearchTerms() {
        RecordingOperations operations = new RecordingOperations(true);

        service.afterSearchDelay(operations, 2000);

        assertEquals(List.of("sleep:2000", "revertSearch"), operations.calls);
    }

    private static final class RecordingOperations implements ScannerSearchCleanupService.Operations {
        private final List<String> calls = new ArrayList<>();
        private final boolean hasDriver;

        private RecordingOperations(boolean hasDriver) {
            this.hasDriver = hasDriver;
        }

        @Override
        public boolean hasCurrentDriver() {
            calls.add("hasDriver");
            return hasDriver;
        }

        @Override
        public void switchToDefaultContent() {
            calls.add("defaultContent");
        }

        @Override
        public void clearPreviousXPath() {
            calls.add("clearXPath");
        }

        @Override
        public void revertCloneInjections() {
            calls.add("revertClone");
        }

        @Override
        public void revertPickInjections() {
            calls.add("revertPick");
        }

        @Override
        public void sleep(long millis) {
            calls.add("sleep:" + millis);
        }

        @Override
        public void revertSearchTermsInjections() {
            calls.add("revertSearch");
        }
    }
}
