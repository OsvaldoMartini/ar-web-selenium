package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScannerTestRunExecutionStateTest {

    @Test
    void reportsCurrentActiveThenLastSubmittedExecution() {
        AtomicLong active = new AtomicLong();
        AtomicLong lastSubmitted = new AtomicLong(4L);
        ScannerTestRunExecutionState state =
                new ScannerTestRunExecutionState(active, lastSubmitted, new AtomicLong(), new TestRunExecutionOutcomeTracker());

        assertEquals(4L, state.currentExecutionId());

        active.set(7L);
        assertEquals(7L, state.currentExecutionId());
    }

    @Test
    void completeExecutionRecordsTerminalOutcomeAndMaxCompletedId() {
        AtomicLong completed = new AtomicLong(10L);
        TestRunExecutionOutcomeTracker outcomes = new TestRunExecutionOutcomeTracker();
        outcomes.started(8L);
        outcomes.started(12L);
        ScannerTestRunExecutionState state =
                new ScannerTestRunExecutionState(new AtomicLong(), new AtomicLong(), completed, outcomes);

        state.completeExecution(8L, false);
        assertEquals(10L, completed.get());
        assertEquals("FAILED", state.terminalState(8L));

        state.completeExecution(12L, true);
        assertEquals(12L, completed.get());
        assertEquals("PASSED", state.terminalState(12L));
    }

    @Test
    void requestStopMarksRunningExecutionInterrupted() {
        TestRunExecutionOutcomeTracker outcomes = new TestRunExecutionOutcomeTracker();
        outcomes.started(3L);
        ScannerTestRunExecutionState state =
                new ScannerTestRunExecutionState(new AtomicLong(), new AtomicLong(), new AtomicLong(), outcomes);

        assertTrue(state.requestStop(3L));
        state.completeExecution(3L, true);

        assertEquals("INTERRUPTED", state.terminalState(3L));
    }

    @Test
    void clearActiveExecutionOnlyClearsMatchingId() {
        AtomicLong active = new AtomicLong(9L);
        ScannerTestRunExecutionState state =
                new ScannerTestRunExecutionState(active, new AtomicLong(), new AtomicLong(), new TestRunExecutionOutcomeTracker());

        state.clearActiveExecution(8L);
        assertEquals(9L, active.get());

        state.clearActiveExecution(9L);
        assertEquals(0L, active.get());
    }

    @Test
    void completionUsesCompletedWatermark() {
        ScannerTestRunExecutionState state =
                new ScannerTestRunExecutionState(new AtomicLong(), new AtomicLong(), new AtomicLong(5L),
                        new TestRunExecutionOutcomeTracker());

        assertTrue(state.isExecutionComplete(0L));
        assertTrue(state.isExecutionComplete(5L));
        assertFalse(state.isExecutionComplete(6L));
    }

    @Test
    void terminalStateRequiresKnownCompletedExecution() {
        ScannerTestRunExecutionState state =
                new ScannerTestRunExecutionState(new AtomicLong(), new AtomicLong(), new AtomicLong(),
                        new TestRunExecutionOutcomeTracker());

        assertThrows(IllegalStateException.class, () -> state.terminalState(1L));
    }
}
