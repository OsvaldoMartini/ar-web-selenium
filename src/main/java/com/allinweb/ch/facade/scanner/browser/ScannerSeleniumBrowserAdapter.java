package com.allinweb.ch.facade.scanner.browser;

import com.allinweb.ch.facade.ScannerBrowserUrlService;
import com.allinweb.ch.facade.ScannerDomReviewSnapshotService;
import com.allinweb.ch.facade.ScannerPageReviewFileService;
import java.util.function.Supplier;
import org.openqa.selenium.WebDriver;

public final class ScannerSeleniumBrowserAdapter
        implements ScannerBrowserUrlService.Browser,
                ScannerDomReviewSnapshotService.Browser,
                ScannerPageReviewFileService.Browser {
    private final Supplier<WebDriver> driverSupplier;

    public ScannerSeleniumBrowserAdapter(WebDriver driver) {
        this(() -> driver);
    }

    public ScannerSeleniumBrowserAdapter(Supplier<WebDriver> driverSupplier) {
        this.driverSupplier = driverSupplier;
    }

    @Override
    public boolean hasCurrentDriver() {
        return driver() != null;
    }

    @Override
    public String pageSource() {
        WebDriver driver = driver();
        return driver == null ? "" : driver.getPageSource();
    }

    @Override
    public String currentUrl() {
        WebDriver driver = driver();
        return driver == null ? "(unknown)" : driver.getCurrentUrl();
    }

    @Override
    public String title() {
        WebDriver driver = driver();
        return driver == null ? "" : driver.getTitle();
    }

    private WebDriver driver() {
        return driverSupplier == null ? null : driverSupplier.get();
    }
}
