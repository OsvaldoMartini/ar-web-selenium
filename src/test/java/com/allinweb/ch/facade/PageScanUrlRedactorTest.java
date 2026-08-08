package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageScanUrlRedactorTest {

    @Test
    void removesCredentialsQueryAndFragmentWithoutChangingThePagePath() {
        assertEquals(
                "https://bank.example:8443/accounts/payment%20history",
                PageScanUrlRedactor.redact(
                        "https://client:password@BANK.EXAMPLE:8443/accounts/payment%20history?token=secret#account"));
    }

    @Test
    void refusesToEchoMalformedOrNonHttpValues() {
        assertEquals("arweb://redacted-page", PageScanUrlRedactor.redact("not a secret?token=123"));
        assertEquals("arweb://redacted-page", PageScanUrlRedactor.redact("file:///private/account.txt"));
        assertEquals("arweb://redacted-page", PageScanUrlRedactor.redact(null));
    }
}
