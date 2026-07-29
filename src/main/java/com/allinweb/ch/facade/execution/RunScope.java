package com.allinweb.ch.facade.execution;

import java.util.Objects;

/** Exact Block scope requested by Test Run or Launch. */
public record RunScope(Kind kind, Integer selectedBlockId) {

    public RunScope {
        kind = Objects.requireNonNull(kind, "kind");
        if (kind == Kind.ALL) {
            if (selectedBlockId != null) {
                throw new IllegalArgumentException("ALL scope cannot identify a selected Block");
            }
        } else if (selectedBlockId == null || selectedBlockId <= 0) {
            throw new IllegalArgumentException(kind + " scope requires a positive selected Block ID");
        }
    }

    public static RunScope all() {
        return new RunScope(Kind.ALL, null);
    }

    public static RunScope fromBlock(int selectedBlockId) {
        return new RunScope(Kind.FROM_BLOCK, selectedBlockId);
    }

    public static RunScope one(int selectedBlockId) {
        return new RunScope(Kind.ONE, selectedBlockId);
    }

    public enum Kind {
        ALL,
        FROM_BLOCK,
        ONE
    }
}
