package com.allinweb.ch.driver;

import com.allinweb.ch.facade.PerformMessage;
import com.google.common.base.Strings;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

@Data
@Slf4j
public class ARWebDriver {

    private static final PerformMessage performMessage = PerformMessage.getInstance();
    // Static final variable to hold the singleton instance
    protected static volatile ARWebDriver instance;

    private WebDriver currentDriver;
    private ARPlaywrightDriver playwrightDriver;

    // Private constructor to prevent instantiation
    public ARWebDriver() {}

    // Public method to access the singleton instance
    public static ARWebDriver getInstance() {
        if (instance == null) {
            synchronized (ARWebDriver.class) {
                if (instance == null) {
                    instance = new ARWebDriver();
                }
            }
        }
        return instance;
    }

    public boolean isPlaywrightEnabled() {
        return true;
    }

    /**
     * Playwright is the only browser runtime. This compatibility method remains while callers are
     * converted from driver-mode branching to direct Playwright operations.
     */
    public boolean isPlaywrightOnly() {
        return true;
    }

    public ARPlaywrightDriver getPlaywrightDriver() {
        if (playwrightDriver == null) {
            synchronized (ARWebDriver.class) {
                if (playwrightDriver == null) {
                    playwrightDriver = new ARPlaywrightDriver();
                }
            }
        }
        return playwrightDriver;
    }

    public ARPlaywrightDriver currentPlaywrightDriver() {
        return playwrightDriver;
    }

    public boolean openBrowser(String browserType, String url, String optionsConfig) {

        if (url == null || Strings.isNullOrEmpty(url.trim())) {
            log.info("URL IS EMPTY");
            performMessage.errorMessage("URL IS EMPTY", "URL Web Browser is Empty", null, null, null, 0);
            return false;
        }

        // Playwright is the only browser launcher. The legacy Selenium field remains temporarily
        // while downstream action APIs are migrated to browser-neutral/Playwright types.
        log.info("Opening or reusing Playwright browser session for {}", url);
        getPlaywrightDriver().openOrNavigate(browserType, url, optionsConfig);
        this.currentDriver = null;
        return true;
    }

    /**
     * Starts a TEST RUN without changing an already-open Playwright page.
     *
     * <p>The normal {@link #openBrowser(String, String, String)} contract intentionally navigates an
     * existing page to the configured URL. TEST RUN is different: an open page may contain the
     * user's authenticated session and current application state, so startup must adopt it exactly
     * as-is. The configured URL is used only when no Playwright page is open yet.
     */
    public boolean openBrowserPreservingCurrentPage(String browserType, String url, String optionsConfig) {
        ARPlaywrightDriver existing = currentPlaywrightDriver();
        if (existing != null && existing.isOpen()) {
            existing.assertBrowserCompatible(browserType);
            log.info("Reusing the current Playwright page for TEST RUN without navigation or reload");
            this.currentDriver = null;
            return true;
        }
        return openBrowser(browserType, url, optionsConfig);
    }

    public void closeBrowser() {
        try {
            if (currentDriver != null) {
                try {
                    currentDriver.quit();
                } catch (Exception e) {
                    log.warn("Error quitting driver: " + e.getMessage());
                }
            }
        } finally {
            currentDriver = null;
            if (playwrightDriver != null) {
                playwrightDriver.close();
            }
        }
    }

    public void shutdown() {
        try {
            if (currentDriver != null) {
                currentDriver.quit();
            }
        } catch (Exception e) {
            log.warn("Error quitting browser during shutdown: " + e.getMessage());
        } finally {
            currentDriver = null;
            if (playwrightDriver != null) {
                playwrightDriver.shutdown();
                playwrightDriver = null;
            }
        }
    }
}
