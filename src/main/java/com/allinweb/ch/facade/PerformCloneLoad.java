package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARPriorities;
import com.allinweb.ch.util.ErrorMessage;
import java.util.Arrays;
import java.util.List;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformCloneLoad {

    private ARPriorities arPriorities;
    private ARWebDriver arWebDriver;
    private static JavascriptExecutor jsExecutor;

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<PerformCloneLoad> instance = () -> new PerformCloneLoad();

    // Private constructor to prevent instantiation
    private PerformCloneLoad() {
        // Initialize if necessary
    }

    public void initializePerformCloneLoad(ARPriorities arPriorities, ARWebDriver arWebDriver) {
        this.arPriorities = arPriorities;
        this.arWebDriver = arWebDriver;
    }

    // Public method to access the singleton instance
    public static PerformCloneLoad getInstance() {
        return instance.get();
    }

    public ErrorMessage dynamicPickOneCloneElementsDTO(
            WebDriver driver,
            String currentUrl,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            int homeBankingId) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(
                    jsHoverPickInject, currentUrl, currentUrl, dataList, searchHiddenFields, port, homeBankingId);
            return null;
        } catch (Exception error) {
            return new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage());
        }
    }

    private String jsHoverPickInject = """
            """;
}
