package com.allinweb.ch.facade;

public final class ScannerSupportResponseActionService {

    public Action actionOf(String action) {
        if ("send".equals(action)) {
            return Action.SEND;
        }
        if ("save".equals(action)) {
            return Action.SAVE;
        }
        if ("cancel".equals(action)) {
            return Action.CANCEL;
        }
        return Action.UNKNOWN;
    }

    public boolean isDomReviewCancelled(String html, String action) {
        return html == null || actionOf(action) == Action.CANCEL;
    }

    public boolean isElementsReviewCancelled(String action, String message) {
        return actionOf(action) == Action.CANCEL || message == null || message.isBlank();
    }

    public enum Action {
        SEND,
        SAVE,
        CANCEL,
        UNKNOWN
    }
}
