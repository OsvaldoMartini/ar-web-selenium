package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class ScannerBrowserOperationsTest {

    @Test
    void browserStateUsesSeleniumWhenDriverIsAvailable() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getWindowHandles()).thenReturn(Set.of("first", "second"));
        when(driver.getCurrentUrl()).thenReturn("https://selenium.example");
        when(driver.getTitle()).thenReturn("Selenium page");
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.seleniumDriver = driver;
        runtime.playwrightOpen = true;

        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(runtime).browserState();

        assertEquals("OPEN", browser.state());
        assertEquals("https://selenium.example", browser.activeUrl());
        assertEquals("Selenium page", browser.activeTitle());
        assertEquals(2, browser.openTabs());
        assertTrue(browser.scannable());
    }

    @Test
    void browserStateUsesPlaywrightWhenSeleniumIsNotAvailable() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.playwrightOpen = true;
        runtime.playwrightCurrentUrl = "https://playwright.example";
        runtime.playwrightTitle = "Playwright page";
        runtime.playwrightPageCount = 3;

        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(runtime).browserState();

        assertEquals("OPEN", browser.state());
        assertEquals("https://playwright.example", browser.activeUrl());
        assertEquals("Playwright page", browser.activeTitle());
        assertEquals(3, browser.openTabs());
        assertTrue(browser.scannable());
    }

    @Test
    void browserStateIsClosedWhenNoBrowserIsAvailable() {
        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(new RecordingRuntime()).browserState();

        assertEquals("CLOSED", browser.state());
        assertEquals("", browser.activeUrl());
        assertEquals("", browser.activeTitle());
        assertEquals(0, browser.openTabs());
        assertFalse(browser.scannable());
    }

    @Test
    void browserStateIsClosedWhenSeleniumStateThrows() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getWindowHandles()).thenThrow(new IllegalStateException("browser gone"));
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.seleniumDriver = driver;

        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(runtime).browserState();

        assertEquals("CLOSED", browser.state());
        assertFalse(browser.scannable());
    }

    @Test
    void browserStateKeepsPlaywrightOpenWhenOptionalDetailsFail() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.playwrightOpen = true;
        runtime.failPlaywrightDetails = true;

        ScannerWorkspaceState.Browser browser = new ScannerBrowserOperations(runtime).browserState();

        assertEquals("OPEN", browser.state());
        assertEquals("", browser.activeUrl());
        assertEquals("", browser.activeTitle());
        assertEquals(1, browser.openTabs());
        assertTrue(browser.scannable());
    }

    private static final class RecordingRuntime implements ScannerBrowserOperations.Runtime {
        private WebDriver seleniumDriver;
        private boolean playwrightOpen;
        private String playwrightCurrentUrl = "";
        private String playwrightTitle = "";
        private int playwrightPageCount;
        private boolean failPlaywrightDetails;

        @Override
        public WebDriver seleniumDriver() {
            return seleniumDriver;
        }

        @Override
        public boolean playwrightOpen() {
            return playwrightOpen;
        }

        @Override
        public String playwrightCurrentUrl() {
            if (failPlaywrightDetails) {
                throw new IllegalStateException("url failed");
            }
            return playwrightCurrentUrl;
        }

        @Override
        public String playwrightTitle() {
            if (failPlaywrightDetails) {
                throw new IllegalStateException("title failed");
            }
            return playwrightTitle;
        }

        @Override
        public int playwrightPageCount() {
            if (failPlaywrightDetails) {
                throw new IllegalStateException("count failed");
            }
            return playwrightPageCount;
        }
    }
}
