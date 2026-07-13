package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BotJobWorkspaceCapabilityServiceTest {

    private final BotJobWorkspaceCapabilityService service = BotJobWorkspaceCapabilityService.getInstance();

    @Test
    void desktopToolsSupportWebAndRestButRejectMobile() {
        assertTrue(service.supportsDesktopBrowserTools(null));
        assertTrue(service.supportsDesktopBrowserTools("Web App"));
        assertTrue(service.supportsDesktopBrowserTools("Rest Api"));
        assertFalse(service.supportsDesktopBrowserTools("Android"));
        assertThrows(IllegalStateException.class, () -> service.requirePreScan("iOS"));
    }

    @Test
    void organizationManagerOnlyRequiresLicenseWhenGuardEnabled() {
        assertDoesNotThrow(() -> service.requireOrganizationManager(false, false));
        assertDoesNotThrow(() -> service.requireOrganizationManager(true, true));
        assertThrows(IllegalStateException.class, () -> service.requireOrganizationManager(true, false));
    }
}
