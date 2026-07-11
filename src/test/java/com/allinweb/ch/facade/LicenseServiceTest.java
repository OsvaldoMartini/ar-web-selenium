package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.license.LicenceVal;
import org.junit.jupiter.api.Test;

class LicenseServiceTest {
    @Test
    void mapsOnlyValidLicenseToActiveAccess() {
        for (LicenceVal value : LicenceVal.values()) {
            boolean expected = value == LicenceVal.VALID;
            assertTrue(LicenseService.status(value).get("ok").getAsBoolean());
            assertTrue(LicenseService.status(value).get("statusCode").getAsString().equals(value.name()));
            if (expected) {
                assertTrue(LicenseService.status(value).get("active").getAsBoolean());
                assertFalse(LicenseService.status(value).get("requiresActivation").getAsBoolean());
            } else {
                assertFalse(LicenseService.status(value).get("active").getAsBoolean());
                assertTrue(LicenseService.status(value).get("requiresActivation").getAsBoolean());
            }
        }
    }

    @Test
    void verificationErrorRequiresActivation() {
        assertFalse(LicenseService.status(null).get("ok").getAsBoolean());
        assertTrue(LicenseService.status(null).get("requiresActivation").getAsBoolean());
    }

    @Test
    void inactiveStatusTargetsActivationInsteadOfProtectedApplication() {
        assertTrue(LicenseService.status(LicenceVal.MISSING).get("requiresActivation").getAsBoolean());
        assertFalse(LicenseService.status(LicenceVal.MISSING).get("active").getAsBoolean());
    }
}
