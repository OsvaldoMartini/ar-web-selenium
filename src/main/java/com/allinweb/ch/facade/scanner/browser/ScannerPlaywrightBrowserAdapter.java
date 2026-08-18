package com.allinweb.ch.facade.scanner.browser;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.facade.ScannerBrowserUrlService;
import com.allinweb.ch.facade.ScannerDomReviewSnapshotService;
import com.allinweb.ch.facade.ScannerPageReviewFileService;
import com.allinweb.ch.facade.SupportCapture;
import java.util.function.Supplier;

public final class ScannerPlaywrightBrowserAdapter
        implements ScannerBrowserUrlService.Browser,
                ScannerDomReviewSnapshotService.Browser,
                ScannerPageReviewFileService.Browser,
                SupportCapture.Browser {
    private final Supplier<ARPlaywrightDriver> browserSupplier;

    public ScannerPlaywrightBrowserAdapter(ARPlaywrightDriver browser) {
        this(() -> browser);
    }

    public ScannerPlaywrightBrowserAdapter(Supplier<ARPlaywrightDriver> browserSupplier) {
        this.browserSupplier = browserSupplier;
    }

    @Override
    public boolean hasCurrentDriver() {
        ARPlaywrightDriver browser = browser();
        if (browser == null) {
            return false;
        }
        try {
            return browser.isOpen();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public String pageSource() {
        ARPlaywrightDriver browser = activeBrowser();
        return browser == null ? "" : browser.content();
    }

    @Override
    public String currentUrl() {
        ARPlaywrightDriver browser = activeBrowser();
        return browser == null ? "(unknown)" : browser.currentUrl();
    }

    @Override
    public String title() {
        ARPlaywrightDriver browser = activeBrowser();
        return browser == null ? "" : browser.title();
    }

    @Override
    public int[] viewportSize() {
        ARPlaywrightDriver browser = activeBrowser();
        return browser == null ? null : browser.viewportSize();
    }

    @Override
    public SupportCapture.ElementSnapshot inspectElement(String xPath) {
        ARPlaywrightDriver browser = activeBrowser();
        if (browser == null) {
            return SupportCapture.ElementSnapshot.notFound("no-active-browser");
        }
        ARPlaywrightDriver.BrowserElementSnapshot snapshot = browser.inspectElement(xPath);
        if (snapshot == null) {
            return SupportCapture.ElementSnapshot.notFound("invalid-result");
        }
        return new SupportCapture.ElementSnapshot(
                snapshot.found(),
                snapshot.matchCount(),
                snapshot.displayed(),
                snapshot.enabled(),
                snapshot.selected(),
                snapshot.x(),
                snapshot.y(),
                snapshot.width(),
                snapshot.height(),
                snapshot.text(),
                snapshot.outerHtml(),
                snapshot.innerHtml(),
                snapshot.parentHtml(),
                snapshot.reason());
    }

    private ARPlaywrightDriver activeBrowser() {
        return hasCurrentDriver() ? browser() : null;
    }

    private ARPlaywrightDriver browser() {
        return browserSupplier == null ? null : browserSupplier.get();
    }
}
