package com.allinweb.ch.facade;

public final class ScannerSupportCaptureResultService {

    public AlertMessage domCapture(SupportCapture.CaptureResult result) {
        if (result.isOk()) {
            return new AlertMessage(true, "DOM capture sent", "Ticket: " + result.ticketId());
        }
        return new AlertMessage(false, "Could not send DOM capture", result.error());
    }

    public AlertMessage elementsReview(SupportCapture.CaptureResult result) {
        if (result.isOk()) {
            return new AlertMessage(true, "Elements review sent", "Ticket: " + result.ticketId());
        }
        return new AlertMessage(false, "Could not send elements review", result.error());
    }

    public record AlertMessage(boolean ok, String header, String content) {}
}
