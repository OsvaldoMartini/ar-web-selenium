package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ARWebDriverPlaywrightLifecycleTest {
    private ARWebDriver runtime;

    @AfterEach
    void resetSingleton() {
        if (runtime != null) {
            runtime.shutdown();
        }
        ARWebDriver.instance = null;
    }

    @Test
    void opensTheConfiguredBrowserOnlyThroughPlaywright() {
        runtime = new ARWebDriver();
        ARPlaywrightDriver playwright = mock(ARPlaywrightDriver.class);
        runtime.setPlaywrightDriver(playwright);

        assertTrue(runtime.openBrowser("edge", "https://example.test", "--inprivate"));

        verify(playwright).openOrNavigate("edge", "https://example.test", "--inprivate");
    }

    @Test
    void testRunReusesAnOpenPageWithoutNavigationOrReload() {
        runtime = new ARWebDriver();
        ARPlaywrightDriver playwright = mock(ARPlaywrightDriver.class);
        when(playwright.isOpen()).thenReturn(true);
        runtime.setPlaywrightDriver(playwright);

        assertTrue(runtime.openBrowserPreservingCurrentPage(
                "edge", "https://different-configured-url.test", "--inprivate"));

        verify(playwright).isOpen();
        verify(playwright, never()).openOrNavigate(anyString(), anyString(), anyString());
        verify(playwright, never()).navigate(anyString());
        verify(playwright, never()).reload();
        assertSame(playwright, runtime.currentPlaywrightDriver());
    }

    @Test
    void testRunOpensTheConfiguredPageWhenNoPageExists() {
        runtime = new ARWebDriver();
        ARPlaywrightDriver playwright = mock(ARPlaywrightDriver.class);
        when(playwright.isOpen()).thenReturn(false);
        runtime.setPlaywrightDriver(playwright);

        assertTrue(runtime.openBrowserPreservingCurrentPage(
                "edge", "https://example.test", "--inprivate"));

        verify(playwright).isOpen();
        verify(playwright).openOrNavigate("edge", "https://example.test", "--inprivate");
    }

    @Test
    void browserCloseKeepsThePlaywrightWrapperAndSingletonReusable() {
        runtime = new ARWebDriver();
        ARPlaywrightDriver playwright = mock(ARPlaywrightDriver.class);
        runtime.setPlaywrightDriver(playwright);
        ARWebDriver.instance = runtime;

        runtime.closeBrowser();

        verify(playwright).close();
        assertSame(playwright, runtime.getPlaywrightDriver());
        assertSame(runtime, ARWebDriver.getInstance());
    }

    @Test
    void applicationShutdownTerminatesThePlaywrightExecutor() {
        runtime = new ARWebDriver();
        ARPlaywrightDriver playwright = mock(ARPlaywrightDriver.class);
        runtime.setPlaywrightDriver(playwright);

        runtime.shutdown();

        verify(playwright).shutdown();
        runtime = null;
    }
}
