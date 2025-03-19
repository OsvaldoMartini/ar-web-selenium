package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementScriptFactory;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.PerformPreLoad;
import com.allinweb.ch.facade.SingletonSupplier;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.Data;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.support.ui.WebDriverWait;

@Data
public class ARWebDriver {

    protected static final SingletonSupplier<ARWebDriver> instance = () -> new ARWebDriver();

    public static ARWebDriver getInstance() {
        return instance.get();
    }

    public ARWebDriver() {}

    private PerformMessage performMessage;
    private PerformPreLoad performPreLoad;
    private final WebElementScriptFactory scriptFactory = new WebElementScriptFactory();

    private static ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    public static WebDriver getDriver() {
        return driverThreadLocal.get();
    }

    public static void setDriver(WebDriver driver) {
        driverThreadLocal.set(driver);
    }

    public static void quitDriver() {
        WebDriver driver = driverThreadLocal.get();
        if (driver != null) {
            driver.quit();
            driverThreadLocal.remove();
        }
    }

    public void initialize(PerformMessage performMessage, PerformPreLoad performPreLoad) {
        this.performMessage = performMessage;
        this.performPreLoad = performPreLoad;
    }

    public WebDriver getDriverEdge(EdgeOptions options) {
        if (getDriver() == null) {
            synchronized (ARWebDriver.class) {
                if (getDriver() == null) {
                    WebDriver driver = options != null ? new EdgeDriver(options) : new EdgeDriver();
                    setDriver(driver);
                }
            }
        }
        return getDriver();
    }

    // Modified version that directly manages ThreadLocal within the method
    public WebDriver getDriverEdgeThreadLocal(EdgeOptions options) {
        if (getDriver() == null) {
            WebDriver driver;
            if (options != null) {
                driver = new EdgeDriver(options);
            } else {
                driver = new EdgeDriver();
            }
            setDriver(driver);
        }
        return getDriver();
    }

    public WebDriver getDriverFireFox(FirefoxOptions options) {
        if (getDriver() == null) {
            synchronized (ARWebDriver.class) {
                if (getDriver() == null) {
                    WebDriver driver = options != null ? new FirefoxDriver(options) : new FirefoxDriver();
                    setDriver(driver);
                }
            }
        }
        return getDriver();
    }

    public WebDriver getDriverChrome(ChromeOptions options) {
        if (getDriver() == null) {
            synchronized (ARWebDriver.class) {
                if (getDriver() == null) {
                    WebDriver driver = options != null ? new ChromeDriver(options) : new ChromeDriver();
                    setDriver(driver);
                }
            }
        }
        return getDriver();
    }

    public static String identifyLineSeparator(String text) {
        if (text.contains("\r\n")) {
            return "\r\n";
        } else if (text.contains("\n")) {
            return "\n";
        } else if (text.contains("\r")) {
            return "\r";
        }
        return System.lineSeparator();
    }

    public WebDriver openDriver(
            String browserType,
            String webDriverPath,
            String url,
            String optionsConfig,
            String[] dataArray,
            boolean searchHiddenFields,
            int port) {

        if (Strings.isNullOrEmpty(url.trim())) {
            ARLogger.getInstance(ARWebDriver.class).fine("URL IS EMPTY");
            performMessage.errorMessage("URL IS EMPTY", "URL Web Browser is Empty", null, null, null, 0);
            return null;
        }

        String lineSeparator = identifyLineSeparator(optionsConfig);
        String[] optionsConfigLines = new String[0];
        try {
            optionsConfigLines = optionsConfig.split(lineSeparator);
        } catch (Exception ex) {
            ARLogger.getInstance(ARWebDriver.class).severe("Error WebDriver config Options : \n" + ex.getMessage());
        }

        ARLogger.getInstance(ARWebDriver.class).fine("Going to call WebDriver for \n" + url);
        ARPropertyManager managerProps = ARPropertyManager.getInstance();

        if (Strings.isNullOrEmpty(webDriverPath)) {
            ARLogger.getInstance(ARWebDriver.class).fine("URL IS EMPTY");
        }

        String logFolder = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_LOG);
        try {
            switch (browserType) {
                case ARConstants.CHROME -> {
                    if (!(new File(webDriverPath)).exists()) {
                        ARLogger.getInstance(ARWebDriver.class).fine("Web Driver NOT EXIST \n" + webDriverPath);
                    }
                    System.setProperty("webdriver.chrome.driver", webDriverPath);
                    ChromeOptions optionsChrome = buildOptionsChrome(optionsConfigLines, logFolder);

                    if (optionsChrome != null) {
                        getDriverChrome(optionsChrome);
                    } else {
                        getDriverChrome(null);
                    }
                }
                case ARConstants.EDGE -> {
                    if (!(new File(webDriverPath)).exists()) {
                        ARLogger.getInstance(ARWebDriver.class).fine("Web Driver NOT EXIST \n" + webDriverPath);
                    }
                    System.setProperty("webdriver.edge.driver", webDriverPath);
                    String userDataDir = System.getProperty("java.io.tmpdir") + File.separator + "edge-user-data-"
                            + UUID.randomUUID();
                    EdgeOptions options = new EdgeOptions();
                    options.addArguments("--user-data-dir=" + userDataDir);
                    options = buildOptionsEdge(optionsConfigLines, logFolder);

                    if (options != null) {
                        getDriverEdge(options);
                    } else {
                        getDriverEdge(null);
                    }
                }
                case ARConstants.FIREFOX -> {
                    if (!(new File(webDriverPath)).exists()) {
                        ARLogger.getInstance(ARWebDriver.class).fine("Web Driver NOT EXIST \n" + webDriverPath);
                    }
                    System.setProperty("webdriver.gecko.driver", webDriverPath);
                    FirefoxOptions options = new FirefoxOptions();

                    if (options != null) {
                        getDriverFireFox(options);
                    } else {
                        getDriverFireFox(null);
                    }
                }
            }
        } catch (Exception error) {
            throw new UnsupportedOperationException(error.getMessage());
        }

        getDriver().manage().window().maximize();

        try {
            getDriver().get(url);
            Thread.sleep(3000);
            WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(5));
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")
                    .equals("complete"));

        } catch (Exception e) {
            String errorMessage = e.getMessage();
            ARLogger.getInstance(ARWebDriver.class)
                    .fine("An error has occurred during driver.get(url) Load " + errorMessage);
            int maxLength = 100;
            int messageLength = errorMessage.length();
            int parts = (int) Math.ceil((double) messageLength / maxLength);
            String[] messageChunks = new String[parts];

            for (int i = 0; i < parts; i++) {
                int startIndex = i * maxLength;
                int endIndex = Math.min(startIndex + maxLength, messageLength);
                messageChunks[i] = errorMessage.substring(startIndex, endIndex);
            }
            performMessage.errorMessage(
                    "Error Open URL", messageChunks[0], messageChunks[1], messageChunks[2], messageChunks[3], 0);
            for (String chunk : messageChunks) {
                ARLogger.getInstance(ARWebDriver.class).fine("Error chunk: " + chunk);
            }
            return null;
        }
        return getDriver();
    }

    private EdgeOptions buildOptionsEdge(String[] optionsConfigLines, String logFolder) {
        EdgeOptions optionsEdge = new EdgeOptions();
        optionsEdge.addArguments("--user-data-dir=" + System.getProperty("java.io.tmpdir") + "/edge-profile-"
                + System.currentTimeMillis());

        optionsEdge.addArguments("--remote-allow-origins=*"); // Required for some Edge versions
        optionsEdge.addArguments("--start-maximized"); // Opens browser in full-screen
        optionsEdge.addArguments("--disable-gpu"); // Fixes potential rendering issues
        optionsEdge.addArguments("--no-sandbox"); // Bypass OS security model
        optionsEdge.addArguments("--disable-dev-shm-usage"); // Prevents resource exhaustion
        
        for (String line : optionsConfigLines) {
            if (line.startsWith("#")) {
                ARLogger.getInstance(ARWebDriver.class).fine("COMMENTED OPTIONS: " + line);
                continue;
            }

            ARLogger.getInstance(ARWebDriver.class).fine("WebDriver config: \n" + line);
            String[] config = line.split(":");
            if (config.length > 1) {
                if (config[0].equalsIgnoreCase("proxy")) {
                    if (config.length > 2) {
                        String proxyAddress = String.format("%s:%s", config[1], config[2]);
                        Proxy proxy = new Proxy();
                        proxy.setHttpProxy(proxyAddress)
                                .setFtpProxy(proxyAddress)
                                .setSslProxy(proxyAddress);
                        optionsEdge.setProxy(proxy);
                    } else {
                        ARLogger.getInstance(ARWebDriver.class).severe("Error Check Options Config for Proxy is wrong");
                    }
                } else if (config[0].equalsIgnoreCase("browser_log")) {
                    LoggingPreferences logs = new LoggingPreferences();
                    logs.enable(LogType.BROWSER, Level.ALL);
                    String logFilePath = logFolder + "_edge_browser.log";
                    logs.enable(LogType.BROWSER, Level.ALL);
                    optionsEdge.setCapability(
                            "ms:edgeOptions",
                            "{verbose: true, loggingPrefs: {" + "\"browser\": \"ALL\", \"driver\": \"ALL\"}}");
                } else if (config[0].startsWith("arg")) {
                    optionsEdge.addArguments(config[1]);
                }
            }
        }
        return optionsEdge;
    }

    private ChromeOptions buildOptionsChrome(String[] optionsConfigLines, String logFolder) {
        ChromeOptions optionsChrome = new ChromeOptions();
        optionsChrome.addArguments("--user-data-dir=" + System.getProperty("java.io.tmpdir") + "/edge-profile-"
                + System.currentTimeMillis());

        for (String line : optionsConfigLines) {
            if (line.startsWith("#")) {
                ARLogger.getInstance(ARWebDriver.class).fine("COMMENTED OPTIONS: " + line);
                continue;
            }

            ARLogger.getInstance(ARWebDriver.class).fine("WebDriver config: \n" + line);
            String[] config = line.split(":");
            if (config.length > 1) {
                if (config[0].equalsIgnoreCase("proxy")) {
                    if (config.length > 2) {
                        String proxyAddress = String.format("%s:%s", config[1], config[2]);
                        Proxy proxy = new Proxy();
                        proxy.setHttpProxy(proxyAddress)
                                .setFtpProxy(proxyAddress)
                                .setSslProxy(proxyAddress);
                        optionsChrome.setProxy(proxy);
                    } else {
                        ARLogger.getInstance(ARWebDriver.class).severe("Error Check Options Config for Proxy is wrong");
                    }
                } else if (config[0].equalsIgnoreCase("browser_log")) {
                    System.setProperty("webdriver.chrome.verboseLogging", "true");
                    System.setProperty("webdriver.chrome.logfile", logFolder + "\\_chrome_browser.log");
                } else if (config[0].equalsIgnoreCase("argument")) {
                    optionsChrome.addArguments(config[1]);
                }
            }
        }
        return optionsChrome;
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
        if (getDriver() == null) {
            throw new ARWebDriverNotStartedException();
        }
        JavascriptExecutor executor = (JavascriptExecutor) getDriver();
        return (T) executor.executeScript(script);
    }

    public WebDriver getDriverInstance() {
        return getDriver();
    }

    public boolean isBrowserClosed(ARWebDriver arWebDriver) {
        try {
            getDriver().getTitle();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
