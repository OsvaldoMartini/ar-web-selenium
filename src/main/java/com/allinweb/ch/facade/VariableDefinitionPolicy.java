package com.allinweb.ch.facade;

import java.util.Set;

/**
 * Canonical variable semantics shared by authoring, graph validation, and Memory List apply.
 *
 * <p>A GET produces a runtime variable value. Extract and Check commands consume that value.
 * SET currently writes its configured literal value and only carries variable metadata; it must
 * not be treated as a GET consumer until the engine exposes an explicit variable-source mode.
 */
public final class VariableDefinitionPolicy {
    private static final Set<String> CONSUMER_ACTIONS =
            Set.of("E", "CK", "PDF CHECK", "CSV CHECK");
    private static final Set<String> VARIABLE_ACTIONS =
            Set.of("GET", "SET", "E", "CK", "PDF CHECK", "CSV CHECK");

    private VariableDefinitionPolicy() {}

    public static boolean isProducer(String action) {
        return "GET".equals(CommandRegistry.canonicalize(action));
    }

    public static boolean isConsumer(String action) {
        return CONSUMER_ACTIONS.contains(CommandRegistry.canonicalize(action));
    }

    public static boolean isVariableCommand(String action) {
        return VARIABLE_ACTIONS.contains(CommandRegistry.canonicalize(action));
    }

    /**
     * Stable default name for a newly declared variable.
     *
     * <p>The instruction id supplies owner uniqueness. The readable suffix is normalized without
     * relying on a browser-provided variable name.
     */
    public static String generatedName(int instructionId, String instructionName) {
        String normalized = instructionName == null ? "" : instructionName.trim();
        normalized = normalized
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) normalized = "Instruction";
        return "VAR-" + instructionId + "-" + normalized;
    }
}
