package com.allinweb.ch.license;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.util.SystemDetails;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LicenseManagerValidationTest {
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

    private String payload(String computer, String domain, String user, LocalDate expiration) {
        return String.join("|", computer, domain, user, expiration.toString(), "org-key", "Client Org", "4.6");
    }
}
