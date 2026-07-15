package com.allinweb.ch.facade;

/** UI-agnostic receiver for scanner support responses emitted by the React workspace. */
public interface ScannerSupportRequestHandler {
    void handleDomReviewResponse(String action);

    void handleSupportRequestResponse(String action, String message);

    void requestSupportElements();

    void handleSupportRequestElementsResponse(String action, String message, String elementDetailsJson);
}
