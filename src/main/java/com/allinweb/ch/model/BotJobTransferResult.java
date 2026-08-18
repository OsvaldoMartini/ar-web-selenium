package com.allinweb.ch.model;

/** UI-independent result for a Bot Job import/export operation. */
public record BotJobTransferResult(boolean ok, String message, String fileName) {
    public static BotJobTransferResult success(String message, String fileName) {
        return new BotJobTransferResult(true, message, fileName);
    }

    public static BotJobTransferResult failure(String message) {
        return new BotJobTransferResult(false, message, "");
    }
}
