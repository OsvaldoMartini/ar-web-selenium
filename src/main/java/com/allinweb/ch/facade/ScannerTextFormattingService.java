package com.allinweb.ch.facade;

public class ScannerTextFormattingService {

    public String truncate(String text, int limit) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (text.length() <= limit) {
            return text;
        }

        return text.substring(0, limit) + "...";
    }
}
