package com.allinweb.ch.util;

// Helper class to hold text and varID
public class ComboBoxVars {
    private final String text;
    private final Integer varId;

    public ComboBoxVars(String text, Integer varId) {
        this.text = text;
        this.varId = varId;
    }

    public String getText() {
        return text;
    }

    public Integer getVarId() {
        return varId;
    }
}
