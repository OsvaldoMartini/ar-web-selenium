package com.allinweb.ch.facade.scanner.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class ScannerSeleniumBrowserAdapterTest {

    @Test
    void reportsNoCurrentDriverWhenSupplierReturnsNull() {
        ScannerSeleniumBrowserAdapter adapter = new ScannerSeleniumBrowserAdapter(() -> null);

        assertFalse(adapter.hasCurrentDriver());
        assertEquals("", adapter.pageSource());
        assertEquals("(unknown)", adapter.currentUrl());
        assertEquals("", adapter.title());
    }

    @Test
    void delegatesBrowserSnapshotCallsToCurrentDriver() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getPageSource()).thenReturn("<html></html>");
        when(driver.getCurrentUrl()).thenReturn("https://selected.example");
        when(driver.getTitle()).thenReturn("Selected");

        ScannerSeleniumBrowserAdapter adapter = new ScannerSeleniumBrowserAdapter(driver);

        assertTrue(adapter.hasCurrentDriver());
        assertEquals("<html></html>", adapter.pageSource());
        assertEquals("https://selected.example", adapter.currentUrl());
        assertEquals("Selected", adapter.title());
    }
}
