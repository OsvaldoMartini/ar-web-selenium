package com.allinweb.ch.ai;

/** User-facing GEN FLOW failure: {@code title} for the dialog header, message for the body. */
public class GenFlowException extends Exception {

    private final String title;

    public GenFlowException(String title, String message) {
        super(message);
        this.title = title;
    }

    public GenFlowException(String title, String message, Throwable cause) {
        super(message, cause);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
