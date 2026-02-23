//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

public enum ErrorBehaviour {
    RETURN(0, "Always return result. Rollback to savepoint on failure."),
    THROW(1, "Only return result on success. Raise exception on failure."),
    RETURN_NO_ROLLBACK(2, "Always return result. Do not rollback to savepoint on failure.");

    private int id;
    private String description;

    private ErrorBehaviour(final int id, final String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() {
        return this.id;
    }

    @Override
    public String toString() {
        return "(" + this.id + ") " + this.description;
    }
}
