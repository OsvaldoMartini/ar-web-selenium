//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

public enum CompletionCode {
    OK(0, "Processing succeded."),
    PARTIAL(1, "Processing partially successful."),
    FAILED(2, "Processing failed.");

    private int id;
    private String description;

    private CompletionCode(final int id, final String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() {
        return this.id;
    }

    public static CompletionCode toCompletionCode(final Integer completionCode) {
        if (completionCode == null) {
            return null;
        }
        for (final CompletionCode completion : values()) {
            if (completion.getId() == completionCode) {
                return completion;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "(" + this.id + ") " + this.description;
    }
}
