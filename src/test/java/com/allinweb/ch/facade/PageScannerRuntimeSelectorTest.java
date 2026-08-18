package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageScannerRuntimeSelectorTest {

    @Test
    void preservesLegacyV1DefaultAndAcceptsOnlyTheTwoRuntimeModes() {
        assertEquals(
                PageScannerRuntimeSelector.RuntimeMode.JAVA_V1,
                PageScannerRuntimeSelector.RuntimeMode.parse(null));
        assertEquals(
                PageScannerRuntimeSelector.RuntimeMode.JAVA_V1,
                PageScannerRuntimeSelector.RuntimeMode.parse("  "));
        assertEquals(
                PageScannerRuntimeSelector.RuntimeMode.JAVA_V1,
                PageScannerRuntimeSelector.RuntimeMode.parse("java_v1"));
        assertEquals(
                PageScannerRuntimeSelector.RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2,
                PageScannerRuntimeSelector.RuntimeMode.parse("typescript_playwright_v2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PageScannerRuntimeSelector.RuntimeMode.parse("AUTO"));
    }
}
