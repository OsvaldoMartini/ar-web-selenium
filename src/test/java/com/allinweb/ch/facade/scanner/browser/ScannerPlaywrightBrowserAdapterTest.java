package com.allinweb.ch.facade.scanner.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.facade.SupportCapture;
import org.junit.jupiter.api.Test;

class ScannerPlaywrightBrowserAdapterTest {

    @Test
    void reportsNoCurrentBrowserWhenSupplierReturnsNull() {
        ScannerPlaywrightBrowserAdapter adapter = new ScannerPlaywrightBrowserAdapter(() -> null);

        assertFalse(adapter.hasCurrentDriver());
        assertEquals("", adapter.pageSource());
        assertEquals("(unknown)", adapter.currentUrl());
        assertEquals("", adapter.title());
        assertNull(adapter.viewportSize());
        assertEquals("no-active-browser", adapter.inspectElement("//button").reason());
    }

    @Test
    void delegatesBrowserSnapshotCallsToTheOpenPlaywrightPage() {
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        when(browser.isOpen()).thenReturn(true);
        when(browser.content()).thenReturn("<html></html>");
        when(browser.currentUrl()).thenReturn("https://selected.example");
        when(browser.title()).thenReturn("Selected");
        when(browser.viewportSize()).thenReturn(new int[] {1440, 900});
        when(browser.inspectElement("//button")).thenReturn(new ARPlaywrightDriver.BrowserElementSnapshot(
                true,
                2,
                true,
                true,
                false,
                10,
                20,
                120,
                40,
                "Continue",
                "<button>Continue</button>",
                "Continue",
                "<main><button>Continue</button></main>",
                ""));

        ScannerPlaywrightBrowserAdapter adapter = new ScannerPlaywrightBrowserAdapter(browser);

        assertTrue(adapter.hasCurrentDriver());
        assertEquals("<html></html>", adapter.pageSource());
        assertEquals("https://selected.example", adapter.currentUrl());
        assertEquals("Selected", adapter.title());
        assertArrayEquals(new int[] {1440, 900}, adapter.viewportSize());

        SupportCapture.ElementSnapshot snapshot = adapter.inspectElement("//button");
        assertTrue(snapshot.found());
        assertEquals(2, snapshot.matchCount());
        assertEquals("Continue", snapshot.text());
        assertEquals("<main><button>Continue</button></main>", snapshot.parentHtml());
    }
}
