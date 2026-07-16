package com.allinweb.ch.facade;

import org.openqa.selenium.WebDriver;

public final class ScannerElementsReviewFileService {
    private final ScannerSupportFileService supportFileService;

    public ScannerElementsReviewFileService() {
        this(new ScannerSupportFileService());
    }

    ScannerElementsReviewFileService(ScannerSupportFileService supportFileService) {
        this.supportFileService = supportFileService;
    }

    public ScannerSupportFileService.SupportFile elementsReview(
            WebDriver driver, String elementDetailsJson, String message) {
        return supportFileService.elementsReview(driver, elementDetailsJson, message);
    }
}
