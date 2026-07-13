package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotJobOrganizationCoordinatorTest {

    @Test
    void opensOnlyWhenCapabilityPolicyAllowsIt() {
        AtomicInteger opens = new AtomicInteger();
        BotJobOrganizationCoordinator coordinator = new BotJobOrganizationCoordinator(
                BotJobWorkspaceCapabilityService.getInstance(), opens::incrementAndGet);

        coordinator.open(false, false);
        coordinator.open(true, true);
        assertThrows(IllegalStateException.class, () -> coordinator.open(true, false));

        assertEquals(2, opens.get());
    }
}
