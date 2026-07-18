package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
