package com.allinweb.ch.facade;

import org.openqa.selenium.WebDriver;

public final class ScannerSupportCaptureSendService {
    private final SupportCapture supportCapture;

    public ScannerSupportCaptureSendService() {
        this(new SupportCapture());
    }

    ScannerSupportCaptureSendService(SupportCapture supportCapture) {
        this.supportCapture = supportCapture;
    }

    public SupportCapture.CaptureResult sendDomCapture(WebDriver driver) {
        return supportCapture.captureAndSend(driver, null, null, null, null);
    }

    public SupportCapture.CaptureResult sendElementsReview(
            WebDriver driver, String elementDetailsJson, String message) {
        return supportCapture.captureElementsAndSend(driver, elementDetailsJson, message, null);
    }
}
