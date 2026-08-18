package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScannerTestRunBrowserClosePolicyTest {

    @Test
    void acceptedStopPreventsRunOwnedBrowserClose() {
        Fixture fixture = new Fixture(7L);

        assertTrue(fixture.policy.requestStop(7L));
        assertFalse(fixture.policy.closeBrowserIfAllowed(true, fixture::closeBrowser));
        assertEquals(0, fixture.closeCalls);
    }

    @Test
    void stopAcceptedAfterCompletionSnapshotStillPreventsLateDialogClose() {
        Fixture fixture = new Fixture(9L);
        boolean interruptedAtCompletionSnapshot = false;

        assertFalse(interruptedAtCompletionSnapshot);
        assertTrue(fixture.policy.requestStop(9L));
        assertFalse(fixture.policy.closeBrowserIfAllowed(true, fixture::closeBrowser));
        assertEquals(0, fixture.closeCalls);
    }

    @Test
    void explicitCloseCommittedBeforeStopMakesLateStopUnacceptable() {
        Fixture fixture = new Fixture(11L);

        assertTrue(fixture.policy.closeBrowserIfAllowed(true, fixture::closeBrowser));
        assertFalse(fixture.policy.requestStop(11L));
        assertEquals(1, fixture.closeCalls);
    }

    @Test
    void uninterruptedRunHonorsOneExplicitCloseOnly() {
        Fixture fixture = new Fixture(13L);

        assertTrue(fixture.policy.closeBrowserIfAllowed(true, fixture::closeBrowser));
        assertFalse(fixture.policy.closeBrowserIfAllowed(true, fixture::closeBrowser));
        assertEquals(1, fixture.closeCalls);
    }

    @Test
    void noCloseRequestKeepsBrowserOpen() {
        Fixture fixture = new Fixture(15L);

        assertFalse(fixture.policy.closeBrowserIfAllowed(false, fixture::closeBrowser));
        assertEquals(0, fixture.closeCalls);
    }

    @Test
    void unrestrictedPolicyPreservesExplicitShutdownStyleClose() {
        int[] closeCalls = {0};

        assertTrue(ScannerTestRunBrowserClosePolicy.unrestricted()
                .closeBrowserIfAllowed(true, () -> closeCalls[0]++));
        assertEquals(1, closeCalls[0]);
    }

    private static final class Fixture {
        private final AtomicLong activeExecutionId;
        private final Set<Long> interrupted = new HashSet<>();
        private final ScannerTestRunBrowserClosePolicy policy;
        private int closeCalls;

        private Fixture(long activeExecutionId) {
            this.activeExecutionId = new AtomicLong(activeExecutionId);
            policy = new ScannerTestRunBrowserClosePolicy(
                    this.activeExecutionId::get, this::requestStop, interrupted::contains);
        }

        private boolean requestStop(long executionId) {
            return activeExecutionId.get() == executionId && interrupted.add(executionId);
        }

        private void closeBrowser() {
            closeCalls++;
        }
    }
}
