package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.openqa.selenium.WebDriver;

final class ScannerBrowserOperations implements ScannerWorkspaceService.BrowserOperations {
    private final Runtime runtime;

    ScannerBrowserOperations() {
        this(new SingletonRuntime());
    }

    ScannerBrowserOperations(Runtime runtime) {
        this.runtime = runtime;
    }

    @Override
    public ScannerWorkspaceState.Browser browserState() {
        try {
            WebDriver driver = runtime.seleniumDriver();
            if (driver == null) {
                return playwrightBrowserState();
            }
            List<String> handles = new ArrayList<>(driver.getWindowHandles());
            String activeUrl = safeDriverValue(driver::getCurrentUrl);
            String activeTitle = safeDriverValue(driver::getTitle);
            return new ScannerWorkspaceState.Browser("OPEN", activeUrl, activeTitle, handles.size(), true);
        } catch (RuntimeException error) {
            return new ScannerWorkspaceState.Browser("CLOSED", "", "", 0, false);
        }
    }

    private ScannerWorkspaceState.Browser playwrightBrowserState() {
        if (!runtime.playwrightOpen()) {
            return new ScannerWorkspaceState.Browser("CLOSED", "", "", 0, false);
        }
        String activeUrl = safeDriverValue(runtime::playwrightCurrentUrl);
        String activeTitle = safeDriverValue(runtime::playwrightTitle);
        int openTabs = Math.max(1, safeDriverInt(runtime::playwrightPageCount));
        return new ScannerWorkspaceState.Browser("OPEN", activeUrl, activeTitle, openTabs, true);
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
                ScannerWorkspaceSessions.SCANNER_GRID,
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

    private int safeDriverInt(Supplier<Integer> supplier) {
        try {
            Integer value = supplier.get();
            return value == null ? 0 : value;
        } catch (RuntimeException error) {
            return 0;
        }
    }

    interface Runtime {
        WebDriver seleniumDriver();

        boolean playwrightOpen();

        String playwrightCurrentUrl();

        String playwrightTitle();

        int playwrightPageCount();
    }

    private static final class SingletonRuntime implements Runtime {
        @Override
        public WebDriver seleniumDriver() {
            return PerformActions.getInstance().getCurrentDriver();
        }

        @Override
        public boolean playwrightOpen() {
            try {
                return PerformActions.getInstance().getCurrentARWebDriver().getPlaywrightDriver().isOpen();
            } catch (java.lang.RuntimeException error) {
                return false;
            }
        }

        @Override
        public String playwrightCurrentUrl() {
            return PerformActions.getInstance().getCurrentARWebDriver().getPlaywrightDriver().currentUrl();
        }

        @Override
        public String playwrightTitle() {
            return PerformActions.getInstance().getCurrentARWebDriver().getPlaywrightDriver().title();
        }

        @Override
        public int playwrightPageCount() {
            return PerformActions.getInstance().getCurrentARWebDriver().getPlaywrightDriver().pageCount();
        }
    }
}
