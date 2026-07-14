package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.openqa.selenium.WebDriver;

final class ScannerBrowserOperations implements ScannerWorkspaceService.BrowserOperations {

    @Override
    public ScannerWorkspaceState.Browser browserState() {
        WebDriver driver = PerformActions.getInstance().getCurrentDriver();
        if (driver == null) {
            return new ScannerWorkspaceState.Browser("CLOSED", "", "", 0, false);
        }
        try {
            List<String> handles = new ArrayList<>(driver.getWindowHandles());
            String activeUrl = safeDriverValue(driver::getCurrentUrl);
            String activeTitle = safeDriverValue(driver::getTitle);
            return new ScannerWorkspaceState.Browser("OPEN", activeUrl, activeTitle, handles.size(), true);
        } catch (RuntimeException error) {
            return new ScannerWorkspaceState.Browser("CLOSED", "", "", 0, false);
        }
    }

    @Override
    public void refreshPage() {
        PerformPreLoad.reloadAllPlugins();
        PerformActions.getInstance().refreshPage();
        try {
            PerformActions.getInstance().onHoldInSeconds(2);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void switchTab(int direction) {
        PerformActions actions = PerformActions.getInstance();
        WebDriver driver = actions.getCurrentDriver();
        if (driver == null || direction == 0) {
            return;
        }
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        if (handles.size() < 2) {
            return;
        }
        actions.windowHandlesList = handles;
        int nextIndex = Math.max(0, Math.min(handles.size() - 1, actions.currentTabIndex + direction));
        if (nextIndex == actions.currentTabIndex) {
            return;
        }
        actions.currentTabIndex = nextIndex;
        driver.switchTo().window(handles.get(nextIndex));
    }

    @Override
    public List<ElementDTO> scanPage(String[] searchTerms, int homeBankingId, int botJobId) {
        PerformActions actions = PerformActions.getInstance();
        WebDriver driver = actions.getCurrentDriver();
        PerformListElements.ScanResult scan = PerformListElements.getInstance().scanElements(
                actions.getCurrentARWebDriver(),
                driver,
                searchTerms,
                false,
                54525,
                "scannerTool",
                "scannerGrid",
                "searchTerms",
                homeBankingId,
                botJobId,
                List.of());
        if (scan.error != null) {
            throw new IllegalStateException(scan.error.getErrorTitle() + " - " + scan.error.getErrorHeader());
        }
        return scan.elements == null ? List.of() : scan.elements;
    }

    private String safeDriverValue(Supplier<String> supplier) {
        try {
            String value = supplier.get();
            return value == null ? "" : value;
        } catch (RuntimeException error) {
            return "";
        }
    }
}
