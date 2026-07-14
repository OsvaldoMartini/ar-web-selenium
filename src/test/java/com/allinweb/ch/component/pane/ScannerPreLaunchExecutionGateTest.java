package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchExecutionGateTest {

    @Test
    void startIfIdleAllocatesExecutionAndMarksRunning() {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicLong sequence = new AtomicLong();
        AtomicLong active = new AtomicLong();
        AtomicLong lastSubmitted = new AtomicLong();
        TestRunExecutionOutcomeTracker outcomes = new TestRunExecutionOutcomeTracker();
        ScannerPreLaunchExecutionGate gate =
                new ScannerPreLaunchExecutionGate(running, sequence, active, lastSubmitted, outcomes);

        ScannerPreLaunchExecutionGate.StartAttempt attempt = gate.startIfIdle(id -> true);

        assertTrue(attempt.started());
        assertEquals(1L, attempt.executionId());
        assertTrue(running.get());
        assertEquals(1L, active.get());
        assertEquals(1L, lastSubmitted.get());
    }

    @Test
    void startIfIdleBlocksWhenActiveExecutionIsIncomplete() {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicLong active = new AtomicLong(7L);
        ScannerPreLaunchExecutionGate gate = new ScannerPreLaunchExecutionGate(
                running, new AtomicLong(), active, new AtomicLong(), new TestRunExecutionOutcomeTracker());

        ScannerPreLaunchExecutionGate.StartAttempt attempt = gate.startIfIdle(id -> false);

        assertEquals(ScannerPreLaunchExecutionGate.Status.ACTIVE_EXECUTION, attempt.status());
        assertFalse(attempt.started());
        assertFalse(running.get());
    }

    @Test
    void startIfIdleBlocksWhenRunningFlagAlreadySet() {
        AtomicBoolean running = new AtomicBoolean(true);
        ScannerPreLaunchExecutionGate gate = new ScannerPreLaunchExecutionGate(
                running, new AtomicLong(), new AtomicLong(), new AtomicLong(), new TestRunExecutionOutcomeTracker());

        ScannerPreLaunchExecutionGate.StartAttempt attempt = gate.startIfIdle(id -> true);

        assertEquals(ScannerPreLaunchExecutionGate.Status.ALREADY_RUNNING, attempt.status());
        assertFalse(attempt.started());
    }
}
