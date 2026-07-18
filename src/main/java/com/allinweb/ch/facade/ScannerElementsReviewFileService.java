package com.allinweb.ch.facade;

public final class ScannerElementsReviewFileService {
    private final ScannerSupportFileService supportFileService;

    public ScannerElementsReviewFileService() {
        this(new ScannerSupportFileService());
    }

    ScannerElementsReviewFileService(ScannerSupportFileService supportFileService) {
        this.supportFileService = supportFileService;
    }

    public ScannerSupportFileService.SupportFile elementsReview(
            SupportCapture.Browser browser, String elementDetailsJson, String message) {
        return supportFileService.elementsReview(browser, elementDetailsJson, message);
    }
}
