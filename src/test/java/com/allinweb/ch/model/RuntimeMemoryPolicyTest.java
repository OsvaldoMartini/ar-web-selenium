package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimeMemoryPolicyTest {

    @Test
    void missingPolicyKeepsExistingValuesForCompatibility() {
        assertEquals(RuntimeMemoryPolicy.KEEP, RuntimeMemoryPolicy.parse(null));
        assertEquals(RuntimeMemoryPolicy.KEEP, RuntimeMemoryPolicy.parse(""));
        assertEquals(RuntimeMemoryPolicy.KEEP, RuntimeMemoryPolicy.parse("   "));
    }

    @Test
    void parsesExplicitKeepAndResetCaseInsensitively() {
        assertEquals(RuntimeMemoryPolicy.KEEP, RuntimeMemoryPolicy.parse("keep"));
        assertEquals(RuntimeMemoryPolicy.RESET, RuntimeMemoryPolicy.parse(" reset "));
    }

    @Test
    void refusesUnknownPoliciesInsteadOfSilentlyResetting() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeMemoryPolicy.parse("CLEAR"));
    }
}
