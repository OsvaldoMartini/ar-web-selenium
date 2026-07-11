package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.license.LicenceVal;
import com.google.gson.JsonObject;
import java.util.List;
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

    @Test
    void inactiveSessionsCanOnlyUseLicenseRecoveryAndAboutOperations() {
        List<String> allowed = List.of(
                "echo",
                "license.bootstrap",
                "license.status",
                "license.startup",
                "license.request",
                "license.activate",
                "license.useExisting",
                "about.bootstrap");
        allowed.forEach(operation -> assertTrue(LicenseService.permits(operation, false), operation));

        List<String> protectedOperations = List.of(
                "mainDashboard.list",
                "mainDashboard.openBotJob",
                "organization.list",
                "config.bootstrap",
                "commandEditor.bootstrap",
                "commandEditor.apply");
        protectedOperations.forEach(operation -> assertFalse(LicenseService.permits(operation, false), operation));
        assertFalse(LicenseService.permits(null, false));
        assertFalse(LicenseService.permits("", false));
    }

    @Test
    void activeSessionsCanUseProtectedOperations() {
        assertTrue(LicenseService.permits("mainDashboard.list", true));
        assertTrue(LicenseService.permits("commandEditor.apply", true));
        assertFalse(LicenseService.permits(null, true));
    }

    @Test
    void normalizesSupportedExpirationFormatsToIsoDates() {
        assertEquals("2027-07-11", LicenseService.isoExpiration("2027-07-11"));
        assertEquals("2027-07-11", LicenseService.isoExpiration("11-07-2027"));
        assertEquals("2027-07-11", LicenseService.isoExpiration("11/07/2027"));
    }

    @Test
    void rejectsMissingAndAmbiguousExpirationValues() {
        assertNull(LicenseService.isoExpiration(null));
        assertNull(LicenseService.isoExpiration(""));
        assertNull(LicenseService.isoExpiration("07/11/27"));
        assertNull(LicenseService.isoExpiration("31-02-2027"));
    }

    @Test
    void requestRequiresAgreementBeforeFileGeneration() {
        JsonObject body = requestBody("Client Org", "Owner", "owner@example.com", false);
        JsonObject response = LicenseService.getInstance().request(body);
        assertFalse(response.get("ok").getAsBoolean());
        assertEquals("Accept the software license agreement.", response.get("error").getAsString());
    }

    @Test
    void requestRejectsMissingAndUnsafeOrganizationNames() {
        JsonObject missing = LicenseService.getInstance().request(requestBody("", "Owner", "owner@example.com", true));
        JsonObject unsafe = LicenseService.getInstance().request(
                requestBody("../../Client", "Owner", "owner@example.com", true));
        assertEquals("Enter a valid organization name.", missing.get("error").getAsString());
        assertEquals("Enter a valid organization name.", unsafe.get("error").getAsString());
    }

    @Test
    void requestRejectsUnsafeOwnerAndInvalidEmail() {
        JsonObject unsafeOwner = LicenseService.getInstance().request(
                requestBody("Client Org", "Owner/../../", "owner@example.com", true));
        JsonObject invalidEmail = LicenseService.getInstance().request(
                requestBody("Client Org", "Owner", "not-an-email", true));
        assertEquals("Enter a valid license owner.", unsafeOwner.get("error").getAsString());
        assertEquals("Enter a valid email address.", invalidEmail.get("error").getAsString());
    }

    @Test
    void mutationRequiresARequestId() {
        JsonObject body = requestBody("Client Org", "Owner", "owner@example.com", true);
        body.remove("requestId");
        JsonObject response = LicenseService.getInstance().request(body);
        assertFalse(response.get("ok").getAsBoolean());
        assertEquals("License mutation request ID is required.", response.get("error").getAsString());
    }

    private JsonObject requestBody(
            String organization, String owner, String email, boolean agreementAccepted) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "test-" + organization + "-" + owner + "-" + email + "-" + agreementAccepted);
        body.addProperty("organization", organization);
        body.addProperty("owner", owner);
        body.addProperty("email", email);
        body.addProperty("agreementAccepted", agreementAccepted);
        return body;
    }
}
