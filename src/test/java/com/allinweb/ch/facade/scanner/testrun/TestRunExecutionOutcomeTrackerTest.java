package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestRunExecutionOutcomeTrackerTest {

    @Test
    void keepsPassedAndFailedOutcomesOwnedByTheirExecutionIds() {
        TestRunExecutionOutcomeTracker tracker = new TestRunExecutionOutcomeTracker();
        tracker.started(1);
        tracker.started(2);

        assertEquals(TestRunExecutionOutcomeTracker.Outcome.PASSED, tracker.completed(1, true));
        assertEquals(TestRunExecutionOutcomeTracker.Outcome.FAILED, tracker.completed(2, false));
        assertEquals(TestRunExecutionOutcomeTracker.Outcome.PASSED, tracker.terminalOutcome(1));
        assertEquals(TestRunExecutionOutcomeTracker.Outcome.FAILED, tracker.terminalOutcome(2));
    }

    @Test
    void exactIdStopRemainsInterruptedEvenWhenExecutionUnwindsSuccessfully() {
        TestRunExecutionOutcomeTracker tracker = new TestRunExecutionOutcomeTracker();
        tracker.started(7);
        tracker.started(8);

        assertTrue(tracker.requestStop(7));
        assertFalse(tracker.requestStop(9));
        assertEquals(TestRunExecutionOutcomeTracker.Outcome.INTERRUPTED, tracker.completed(7, true));
        assertTrue(tracker.requestStop(8));
    }

    @Test
    void lateStopCannotRewriteAnAlreadyCompletedRun() {
        TestRunExecutionOutcomeTracker tracker = new TestRunExecutionOutcomeTracker();
        tracker.started(11);
        tracker.completed(11, true);

        assertFalse(tracker.requestStop(11));
        assertEquals(TestRunExecutionOutcomeTracker.Outcome.PASSED, tracker.terminalOutcome(11));
    }

    @Test
    void retainedHistoryIsBounded() {
        TestRunExecutionOutcomeTracker tracker = new TestRunExecutionOutcomeTracker();
        for (long executionId = 1; executionId <= 80; executionId++) {
            tracker.started(executionId);
            tracker.completed(executionId, true);
        }

        assertEquals(64, tracker.retainedOutcomeCount());
        assertEquals(TestRunExecutionOutcomeTracker.Outcome.PASSED, tracker.terminalOutcome(80));
        assertThrows(IllegalStateException.class, () -> tracker.terminalOutcome(1));
    }

    @Test
    void rejectsUnknownAndStillRunningOutcomes() {
        TestRunExecutionOutcomeTracker tracker = new TestRunExecutionOutcomeTracker();
        tracker.started(21);

        assertThrows(IllegalStateException.class, () -> tracker.terminalOutcome(20));
        assertThrows(IllegalStateException.class, () -> tracker.terminalOutcome(21));
    }
}
