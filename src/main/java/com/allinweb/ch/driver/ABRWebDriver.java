package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementScriptFactory;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

public class ABRWebDriver {

    private WebDriver driver = null;
    private final WebElementScriptFactory scriptFactory = new WebElementScriptFactory();

    public void openDriver(String url) {
        if (driver == null) {
            String browser = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.BROWSER);
            switch (browser) {
                case ABRConstants.CHROME -> {
                    ChromeOptions options = new ChromeOptions();
                    options.setBinary(ABRConstants.CURRENT_PATH + "\\chrome\\chrome.exe");
                    options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
                    options.setBinary("C:/Program Files (x86)/Google/Chrome/Application/chrome.exe");
                    options.setExperimentalOption("useAutomationExtension", false);
                    options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
                    driver = new ChromeDriver(options);
                }
                case ABRConstants.EDGE -> {
                    System.setProperty("webdriver.edge.driver", "D:/Projects/AllinWeb/abr-web-selenium-archive/abr-web-selenium-files/ProgramFiles/edgedriver-versions/msedgedriver_64-(129.0.2792.65).exe");
                    EdgeOptions options = new EdgeOptions();
                    // options.setBinary(ABRConstants.CURRENT_PATH + "\\msedgedriver.exe");
                    options.setExperimentalOption("useAutomationExtension", false);
                    options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
                    driver = new EdgeDriver(options);
                }
                case ABRConstants.FIREFOX -> {
                    FirefoxOptions options = new FirefoxOptions();
                    options.setBinary(ABRConstants.CURRENT_PATH + "\\geckodriver.exe");
                    driver = new FirefoxDriver(options);
                }
            }
        }
        driver.manage().window().maximize();
        driver.get(url);
    }

    public List<WebElement> scan(By byRule) {
        if (driver == null) {
            throw new ABRWebDriverNotStartedException();
        }
        return driver.findElements(byRule);
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
        if (driver == null) {
            throw new ABRWebDriverNotStartedException();
        }
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        return (T) executor.executeScript(script);
    }

    public void closeDriver() {
        driver.quit();
        driver = null;
    }

    public WebDriver getDriver() {
        return driver;
    }
}
