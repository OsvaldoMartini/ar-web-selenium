package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScannerBrowserTabSelectorTest {

    @Test
    void switchToLastBrowserTabTreatsMissingDriverAsReady() {
        RecordingOperations operations = new RecordingOperations();
        ScannerBrowserTabSelector selector = new ScannerBrowserTabSelector(operations);

        assertTrue(selector.switchToLastBrowserTab());

        assertEquals(0, operations.windowHandleCalls);
        assertEquals(0, operations.switchCalls);
    }

    @Test
    void switchToLastBrowserTabSwitchesToLastHandle() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasCurrentDriver = true;
        operations.windowHandles = linkedSet("first", "second", "third");
        ScannerBrowserTabSelector selector = new ScannerBrowserTabSelector(operations);

        assertTrue(selector.switchToLastBrowserTab());

        assertEquals("third", operations.switchedHandle);
        assertEquals(1, operations.windowHandleCalls);
        assertEquals(1, operations.switchCalls);
    }

    @Test
    void switchToLastBrowserTabReportsBrowserNotAttachedOnFailure() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasCurrentDriver = true;
        operations.windowHandlesFailure = new IllegalStateException("closed");
        ScannerBrowserTabSelector selector = new ScannerBrowserTabSelector(operations);

        assertFalse(selector.switchToLastBrowserTab());

        assertEquals(1, operations.browserNotAttachedCalls);
    }

    private static Set<String> linkedSet(String... values) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return set;
    }

    private static final class RecordingOperations implements ScannerBrowserTabSelector.Operations {
        private boolean hasCurrentDriver;
        private Set<String> windowHandles = Set.of();
        private RuntimeException windowHandlesFailure;
        private String switchedHandle;
        private int windowHandleCalls;
        private int switchCalls;
        private int browserNotAttachedCalls;

        @Override
        public boolean hasCurrentDriver() {
            return hasCurrentDriver;
        }

        @Override
        public Set<String> windowHandles() {
            windowHandleCalls++;
            if (windowHandlesFailure != null) {
                throw windowHandlesFailure;
            }
            return windowHandles;
        }

        @Override
        public void switchToWindow(String windowHandle) {
            switchCalls++;
            switchedHandle = windowHandle;
        }

        @Override
        public void browserNotAttached() {
            browserNotAttachedCalls++;
        }
    }
}
