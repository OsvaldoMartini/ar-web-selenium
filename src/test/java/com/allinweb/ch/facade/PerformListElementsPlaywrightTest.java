package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformListElementsPlaywrightTest {

    @Test
    void scansThroughTheActivePlaywrightDriver() {
        ARWebDriver browserSession = mock(ARWebDriver.class);
        ARPlaywrightDriver browser = mock(ARPlaywrightDriver.class);
        String[] terms = {"button"};
        when(browserSession.currentPlaywrightDriver()).thenReturn(browser);
        when(browser.scanElements(terms, false)).thenReturn(List.of());

        PerformListElements.ScanResult result = PerformListElements.getInstance()
                .scanElements(browserSession, terms, false, 7, 42, "scanner-grid");

        assertNull(result.error);
        assertEquals(List.of(), result.elements);
        verify(browser).scanElements(terms, false);
    }

    @Test
    void reportsAUsefulErrorWhenNoPlaywrightBrowserExists() {
        PerformListElements.ScanResult result = PerformListElements.getInstance()
                .scanElements(null, new String[] {"button"}, false, 7, 42, "scanner-grid");

        assertNotNull(result.error);
        assertEquals("Playwright Page Scanner", result.error.getErrorTitle());
        assertEquals(List.of(), result.elements);
    }
}
