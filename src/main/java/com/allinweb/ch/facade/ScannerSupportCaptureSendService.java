package com.allinweb.ch.facade;

public final class ScannerSupportCaptureSendService {
    private final SupportCapture supportCapture;

    public ScannerSupportCaptureSendService() {
        this(new SupportCapture());
    }

    ScannerSupportCaptureSendService(SupportCapture supportCapture) {
        this.supportCapture = supportCapture;
    }

    public SupportCapture.CaptureResult sendDomCapture() {
        return supportCapture.captureAndSend(null, null, null, null);
    }

    public SupportCapture.CaptureResult sendElementsReview(String elementDetailsJson, String message) {
        return supportCapture.captureElementsAndSend(elementDetailsJson, message, null);
    }
}
