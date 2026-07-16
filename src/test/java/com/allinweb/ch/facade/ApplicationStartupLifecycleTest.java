package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApplicationStartupLifecycleTest {
    private final ApplicationStartupLifecycle lifecycle = ApplicationStartupLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void runsActivationContinuationOnce() {
        AtomicInteger calls = new AtomicInteger();
        lifecycle.waitForActivation(calls::incrementAndGet);

        assertTrue(lifecycle.continueAfterActivation());
        assertFalse(lifecycle.continueAfterActivation());
        assertEquals(1, calls.get());
    }

    @Test
    void ignoresContinuationWhenActivationIsNotPending() {
        assertFalse(lifecycle.continueAfterActivation());
    }
}
