package com.allinweb.ch.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseManagerValidationTest {
    @TempDir
    Path temporaryDirectory;
    @Test
    void acceptsCurrentMachineWithFutureExpiration() {
        assertEquals(LicenceVal.VALID, LicenseManager.validateLicense(payload(
                SystemDetails.getSystemComputerName(),
                SystemDetails.getSystemDomainName(),
                SystemDetails.getSystemUserName(),
                LocalDate.now().plusDays(30))));
    }

    @Test
    void mapsExpiredAndMachineIdentityMismatches() {
        assertEquals(LicenceVal.EXPIRED, LicenseManager.validateLicense(payload(
                SystemDetails.getSystemComputerName(),
                SystemDetails.getSystemDomainName(),
                SystemDetails.getSystemUserName(),
                LocalDate.now().minusDays(1))));
        assertEquals(LicenceVal.PCNOTMATCH, LicenseManager.validateLicense(payload(
                "wrong-computer",
                SystemDetails.getSystemDomainName(),
                SystemDetails.getSystemUserName(),
                LocalDate.now().plusDays(30))));
        assertEquals(LicenceVal.DOMAINNOTMATCH, LicenseManager.validateLicense(payload(
                SystemDetails.getSystemComputerName(),
                "wrong-domain",
                SystemDetails.getSystemUserName(),
                LocalDate.now().plusDays(30))));
        assertEquals(LicenceVal.USRNOTMATCH, LicenseManager.validateLicense(payload(
                SystemDetails.getSystemComputerName(),
                SystemDetails.getSystemDomainName(),
                "wrong-user",
                LocalDate.now().plusDays(30))));
    }

    @Test
    void rejectsMissingAndMalformedPayloads() {
        assertEquals(LicenceVal.MISSING, LicenseManager.validateLicense(null));
        assertEquals(LicenceVal.MISSING, LicenseManager.validateLicense(""));
        assertEquals(LicenceVal.MISSING, LicenseManager.validateLicense("only|three|parts"));
        assertEquals(LicenceVal.MISSING, LicenseManager.validateLicense("computer|domain|user|not-a-date"));
    }

    @Test
    void mapsMissingFileAndRejectsUnreadableLicensePath() throws Exception {
        assertEquals(LicenceVal.MISSING, LicenseManager.checkLicenseFile(temporaryDirectory.toString()));
        Files.createDirectory(temporaryDirectory.resolve("ARWeb.lic"));
        assertThrows(Exception.class, () -> LicenseManager.checkLicenseFile(temporaryDirectory.toString()));
    }

    private String payload(String computer, String domain, String user, LocalDate expiration) {
        return String.join("|", computer, domain, user, expiration.toString(), "org-key", "Client Org", "4.6");
    }
}
