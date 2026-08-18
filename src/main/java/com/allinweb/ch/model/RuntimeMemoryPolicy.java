package com.allinweb.ch.model;

import java.util.Locale;

/**
 * Controls the durable runtime-variable values used when an execution starts.
 *
 * <p>Compatibility is deliberately conservative: an omitted policy keeps the last committed
 * values. Resetting runtime memory must always be an explicit client choice.
 */
public enum RuntimeMemoryPolicy {
    KEEP,
    RESET;

    public static RuntimeMemoryPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return KEEP;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidValue) {
            throw new IllegalArgumentException(
                    "runtimeMemoryPolicy must be KEEP or RESET", invalidValue);
        }
    }
}
