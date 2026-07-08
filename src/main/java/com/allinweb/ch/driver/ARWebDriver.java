package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementScriptFactory;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

@Data
@Slf4j
public class ARWebDriver {

    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;
    // Static final variable to hold the singleton instance
    protected static volatile ARWebDriver instance;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    private final WebElementScriptFactory scriptFactory = new WebElementScriptFactory();
    private List<WebDriver> webDriverList = new ArrayList<>();
    private WebDriver currentDriver;
    private String edgeVersion;
    private String webDriverEdgeVersion;
    private String webDriverPath;
    private EdgeOptions optionsEdge;
    private ChromeOptions optionsChrome;
    private FirefoxOptions optionsFirefox;
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

    public static String identifyLineSeparator(String text) {
        if (text.contains("\r\n")) {
            return "\r\n"; // Windows style
        } else if (text.contains("\n")) {
            return "\n"; // Unix/Linux style
        } else if (text.contains("\r")) {
            return "\r"; // Old Mac style
        }
        return System.lineSeparator(); // Default line separator if none found
    }

    public boolean isPlaywrightEnabled() {
        String configured = arPropertyManager.getProperty(ARPropertyEnum.USE_PLAYWRIGHT);
        return configured != null && Boolean.parseBoolean(configured.trim());
    }

    public boolean isSeleniumFallbackEnabled() {
        String configured = arPropertyManager.getProperty(ARPropertyEnum.PLAYWRIGHT_SELENIUM_FALLBACK);
        return configured != null && Boolean.parseBoolean(configured.trim());
    }

    /**
     * Playwright-only mode: Playwright is on AND the Selenium fallback is off. In this mode
     * {@link #openDriver} launches ONLY the Playwright browser (one browser), and the Selenium
     * {@code currentDriver} stays null.
     */
    public boolean isPlaywrightOnly() {
        return isPlaywrightEnabled() && !isSeleniumFallbackEnabled();
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

    public void initialize(List<WebDriver> webDriverList) {
        this.webDriverList = webDriverList;
    }

    // Method to add WebDriver instances
    public void addWebDriver(WebDriver driver) {
        webDriverList.add(driver);
    }

    public WebDriver openDriver(
            String browserType,
            String webDriverPath,
            String url,
            String optionsConfig,
            String[] dataArray,
            boolean searchHiddenFields,
            int port) {

        this.webDriverPath = webDriverPath;

        if (url == null || Strings.isNullOrEmpty(url.trim())) {
            log.info("URL IS EMPTY");
            performMessage.errorMessage("URL IS EMPTY", "URL Web Browser is Empty", null, null, null, 0);
            return null;
        }

        // Playwright-only: a single Playwright browser is the only backend (Selenium removed).
        // Returns null (no Selenium WebDriver); callers treat null as success.
        log.info("Opening or reusing Playwright browser session for {}", url);
        getPlaywrightDriver().openOrNavigate(browserType, url, optionsConfig);
        this.currentDriver = null;
        return null;
    }

    public void highlightElement(WebElement element) {
        applyCssToElement(element, "background-color:red");
    }

    public void dehighlightElement(WebElement element) {
        applyCssToElement(element, "background-color:");
    }

    public void applyCssToElement(WebElement element, String cssToApply) {
        String script = scriptFactory.forElement(element).createSetStyleScript(cssToApply);
        runScript(element, script);
    }

    public List<String> extractAttributes(WebElement element, WebElementAttributeEnum... attributes) {
        return extractAttributes(element).stream()
                .filter(s -> Arrays.stream(attributes)
                        .anyMatch(attr -> attr.getValue().equals(s)))
                .collect(Collectors.toList());
    }

    public List<String> extractAttributes(WebElement element) {
        String script = scriptFactory.forElement(element).extractAttributesScript();
        return runScript(element, script);
    }

    private <T> T runScript(WebElement element, String script) {
        if (elementExists(element)) {
            return runScript(script);
        }
        return null;
    }

    private boolean elementExists(WebElement element) {
        String reference = scriptFactory.forElement(element).elementReferenceScript();
        return runScript(reference) != null;
    }

    private <T> T runScript(String script) {
        if (this.currentDriver == null) {
            throw new ARWebDriverNotStartedException();
        }
        JavascriptExecutor executor = (JavascriptExecutor) this.currentDriver;
        return (T) executor.executeScript(script);
    }

    public boolean isBrowserClosed(ARWebDriver arWebDriver) {
        try {
            this.currentDriver.getTitle(); // Try accessing a property
            return false; // If no exception, browser is open
        } catch (Exception e) {
            return true; // If exception occurs, browser is closed
        }
    }

    public void closeAllDrivers() {
        try {
            for (WebDriver driver : webDriverList) {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception e) {
                        log.warn("Error quitting driver: " + e.getMessage());
                    }
                }
            }
        } finally {
            webDriverList.clear();
            currentDriver = null;
            if (playwrightDriver != null) {
                playwrightDriver.close();
                playwrightDriver = null;
            }
            edgeVersion = null;
            webDriverEdgeVersion = null;
            optionsEdge = null;
            optionsChrome = null;
            optionsFirefox = null;
            instance = null; // reset the singleton
        }
    }

    public void closeCurrentDriver() {
        if (currentDriver != null) {
            try {
                currentDriver.quit();
            } catch (Exception e) {
                log.warn("Error quitting current driver: " + e.getMessage());
            } finally {
                currentDriver = null;
            }
        }
        if (playwrightDriver != null) {
            playwrightDriver.close();
            playwrightDriver = null;
        }
    }
}
