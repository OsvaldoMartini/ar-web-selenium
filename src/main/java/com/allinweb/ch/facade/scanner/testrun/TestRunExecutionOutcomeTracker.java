package com.allinweb.ch.facade.scanner.testrun;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, run-ID-owned terminal outcomes for Scanner TEST RUN execution. */
public final class TestRunExecutionOutcomeTracker {

    private static final int MAX_RETAINED_OUTCOMES = 64;
    private final LinkedHashMap<Long, Outcome> outcomes = new LinkedHashMap<>();

    public synchronized void started(long executionId) {
        requireExecutionId(executionId);
        outcomes.put(executionId, Outcome.RUNNING);
        prune();
    }

    public synchronized boolean requestStop(long executionId) {
        if (outcomes.get(executionId) != Outcome.RUNNING) return false;
        outcomes.put(executionId, Outcome.INTERRUPTED);
        return true;
    }

    public synchronized boolean isInterrupted(long executionId) {
        requireExecutionId(executionId);
        return outcomes.get(executionId) == Outcome.INTERRUPTED;
    }

    public synchronized Outcome completed(long executionId, boolean passed) {
        requireExecutionId(executionId);
        Outcome current = outcomes.get(executionId);
        Outcome terminal = current == Outcome.INTERRUPTED
                ? Outcome.INTERRUPTED
                : (passed ? Outcome.PASSED : Outcome.FAILED);
        outcomes.put(executionId, terminal);
        prune();
        return terminal;
    }

    public synchronized Outcome terminalOutcome(long executionId) {
        requireExecutionId(executionId);
        Outcome outcome = outcomes.get(executionId);
        if (outcome == null) {
            throw new IllegalStateException("No TEST RUN outcome exists for execution " + executionId);
        }
        if (outcome == Outcome.RUNNING) {
            throw new IllegalStateException("TEST RUN execution " + executionId + " is still running");
        }
        return outcome;
    }

    public synchronized int retainedOutcomeCount() {
        return outcomes.size();
    }

    private void prune() {
        Iterator<Map.Entry<Long, Outcome>> iterator = outcomes.entrySet().iterator();
        while (outcomes.size() > MAX_RETAINED_OUTCOMES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static void requireExecutionId(long executionId) {
        if (executionId <= 0) throw new IllegalArgumentException("A positive execution ID is required");
    }

    public enum Outcome {
        RUNNING,
        PASSED,
        FAILED,
        INTERRUPTED
    }
}
