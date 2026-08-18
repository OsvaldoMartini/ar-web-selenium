package com.allinweb.ch.facade.scanner.browser;

public final class ScannerBrowserNotAttachedMessageService {

    public Message message() {
        return new Message(
                "The Browser attached with this Web Scanner is Not Active",
                "<span style='font-style: italic;'>Session deleted as the browser has closed the connection!</span>",
                "<span style='color: #E65100; font-weight: bold;'>The Playwright browser session is no longer active.</span>",
                "<span style='font-style: italic;'>Please close and Re-Open the Scanner Tool.</span>",
                "<span style='font-style: italic;'>Details: Web Browser was closed before the Scanner Tool</span>",
                0);
    }

    public record Message(String title, String header, String detail, String action, String cause, int timeoutSeconds) {}
}
