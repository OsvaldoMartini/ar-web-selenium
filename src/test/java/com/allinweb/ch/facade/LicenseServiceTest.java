package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.license.SystemDetails;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseServiceTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void configureWritablePropertiesFile() throws Exception {
        Path config = temporaryDirectory.resolve("TESTS.config");
        Files.createFile(config);
        ARPropertyManager.getInstance().setConfigurationFileName(config.toString());
    }

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
                "about.bootstrap",
                "about.openLicense");
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
    void reportsHeadlessSafeLicenseCapabilities() {
        JsonObject capabilities = LicenseService.capabilities();
        assertTrue(capabilities.get("request").getAsBoolean());
        assertTrue(capabilities.get("activate").getAsBoolean());
        assertTrue(capabilities.get("useExisting").getAsBoolean());
        assertTrue(capabilities.get("directoryRequest").getAsBoolean());
        assertTrue(capabilities.get("typedPath").getAsBoolean());
        assertFalse(capabilities.get("onlineRequest").getAsBoolean());
        assertFalse(capabilities.get("chooseDirectory").getAsBoolean());
        assertFalse(capabilities.get("chooseFile").getAsBoolean());
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

    @Test
    void generatesEncryptedRequestInsideConfiguredTemporaryDirectory() throws Exception {
        ARPropertyManager properties = ARPropertyManager.getInstance();
        String previous = properties.getProperty(ARPropertyEnum.PATH_LICENSE);
        properties.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), temporaryDirectory.toString());
        try {
            JsonObject response = LicenseService.getInstance().request(
                    requestBody("Temporary Client", "Test Owner", "owner@example.com", true));
            Path generated = temporaryDirectory.resolve("Temporary Client-Test Owner.request");
            assertTrue(response.get("ok").getAsBoolean());
            assertEquals(generated.toString(), response.get("requestFile").getAsString());
            assertTrue(Files.isRegularFile(generated));
            assertFalse(Files.readString(generated).contains("Temporary Client"));
        } finally {
            properties.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), previous == null ? "" : previous);
        }
    }

    @Test
    void importsResponseAndTransitionsStartupToMainDashboard() throws Exception {
        ARPropertyManager properties = ARPropertyManager.getInstance();
        String previous = properties.getProperty(ARPropertyEnum.PATH_LICENSE);
        properties.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), temporaryDirectory.toString());
        try {
            String payload = String.join(
                    "|",
                    SystemDetails.getSystemComputerName(),
                    SystemDetails.getSystemDomainName(),
                    SystemDetails.getSystemUserName(),
                    java.time.LocalDate.now().plusDays(30).toString(),
                    "test-org-key",
                    "Temporary Client",
                    "4.6");
            Path responseFile = temporaryDirectory.resolve("activation.response");
            Files.writeString(responseFile, LicenseManager.encrypt(payload, "0123456789abcdef"));

            JsonObject body = new JsonObject();
            body.addProperty("requestId", "activation-e2e-" + System.nanoTime());
            body.addProperty("responseFile", responseFile.toString());
            body.addProperty("agreementAccepted", true);
            JsonObject activation = LicenseService.getInstance().activate(body);
            JsonObject startup = LicenseService.getInstance().startup();

            assertTrue(activation.get("ok").getAsBoolean());
            assertTrue(activation.get("active").getAsBoolean());
            assertEquals(SystemDetails.getSystemUserName(), activation.get("licensedUser").getAsString());
            assertTrue(Files.isRegularFile(temporaryDirectory.resolve("ARWeb.lic")));
            assertTrue(startup.get("allowed").getAsBoolean());
            assertEquals("mainDashboard", startup.get("targetSessionId").getAsString());
        } finally {
            properties.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), previous == null ? "" : previous);
        }
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
