package com.allinweb.ch.facade;

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
public class PerformPreLoad {

    private static JavascriptExecutor jsExecutor;

    protected static final SingletonSupplier<PerformPreLoad> instance = () -> new PerformPreLoad();

    private PerformPreLoad() {
        // Initialize if necessary
    }

    public void initializePerformPreLoad() {}

    public static PerformPreLoad getInstance() {
        return instance.get();
    }

    // "scannerTool", "scannerGrid", "searchTerms"
    public ErrorMessage dynamicLoadElementsDTO(
            WebDriver driver,
            String currentUrl,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            jsExecutor = (JavascriptExecutor) driver;
            // "scannerTool", "scannerGrid", "searchTerms"
            jsExecutor.executeScript(
                    jsSearchInUse,
                    dataList,
                    searchHiddenFields,
                    port,
                    sessionId,
                    destination,
                    operationId,
                    homeBankingId);
            return null;
        } catch (Exception error) {
            return new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage());
        }
    }

    private String jsSearchInUse = """
            """;
}
