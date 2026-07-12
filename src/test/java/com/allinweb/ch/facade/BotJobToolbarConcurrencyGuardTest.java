package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.model.BotJobToolbarAction;
import org.junit.jupiter.api.Test;

class BotJobToolbarConcurrencyGuardTest {

    @Test
    void rejectsConcurrentForegroundWorkWithoutQueueingAndReleasesIdempotently() {
        BotJobToolbarConcurrencyGuard guard = new BotJobToolbarConcurrencyGuard();
        BotJobToolbarConcurrencyGuard.Lease first =
                guard.tryAcquire(42, 7, BotJobToolbarAction.GENERATE_EXCEL);

        assertNotNull(first);
        assertEquals(BotJobToolbarAction.GENERATE_EXCEL, guard.activeOperation().action());
        assertNull(guard.tryAcquire(42, 7, BotJobToolbarAction.EXPORT_JOB));

        first.close();
        first.close();
        BotJobToolbarConcurrencyGuard.Lease next =
                guard.tryAcquire(43, 8, BotJobToolbarAction.IMPORT_JOB);
        assertNotNull(next);
        assertEquals(43, next.operation().botJobId());
        next.close();
        assertNull(guard.activeOperation());
    }

    @Test
    void tracksOnlyOneLiveExternalEngineAndClearsTheExactFinishedProcess() {
        BotJobToolbarConcurrencyGuard guard = new BotJobToolbarConcurrencyGuard();
        Process first = mock(Process.class);
        Process second = mock(Process.class);
        when(first.isAlive()).thenReturn(true, true, true, false);
        when(second.isAlive()).thenReturn(true);

        assertTrue(guard.trackExternalEngine(first));
        assertTrue(guard.externalEngineRunning());
        assertFalse(guard.trackExternalEngine(second));
        assertFalse(guard.externalEngineRunning());

        assertTrue(guard.trackExternalEngine(second));
        guard.externalEngineFinished(first);
        assertTrue(guard.externalEngineRunning());
        guard.externalEngineFinished(second);
        assertFalse(guard.externalEngineRunning());
    }
}
