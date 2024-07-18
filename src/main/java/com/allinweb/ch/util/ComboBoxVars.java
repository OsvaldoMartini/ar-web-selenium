package com.allinweb.ch.util;

// Helper class to hold text and varID
public class ComboBoxVars {
    private final String text;
    private final Integer varId;
    private final String value;

    public ComboBoxVars(String text, Integer varId, String value) {
        this.text = text;
        this.varId = varId;
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public Integer getVarId() {
        return varId;
    }

    public String getValue() {
        return value;
    }
}
