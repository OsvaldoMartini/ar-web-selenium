package com.allinweb.ch.component.pane;

final class ScannerBrowserNotAttachedMessageService {

    Message message(String webDriverPath) {
        return new Message(
                "The Browser attached with this Web Scanner is Not Active",
                "<span style='font-style: italic;'>Session deleted as the browser has closed the connection!</span>",
                "<span style='color: #E65100; font-weight: bold;'>WebDriver path:</span> <span style='font-weight: bold;'>"
                        + webDriverPath + "</span>",
                "<span style='font-style: italic;'>Please close and Re-Open the Scanner Tool.</span>",
                "<span style='font-style: italic;'>Details: Web Browser was closed before the Scanner Tool</span>",
                0);
    }

    record Message(String title, String header, String detail, String action, String cause, int timeoutSeconds) {}
}
