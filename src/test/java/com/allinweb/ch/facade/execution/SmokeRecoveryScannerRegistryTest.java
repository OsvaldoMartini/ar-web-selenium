package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SmokeRecoveryScannerRegistryTest {
    private static final String RUN_ONE = "registry-run-one";
    private static final String RUN_TWO = "registry-run-two";
    private final SmokeRecoveryScannerRegistry registry =
            SmokeRecoveryScannerRegistry.getInstance();

    @AfterEach
    void tearDown() {
        registry.clear(RUN_ONE);
        registry.clear(RUN_TWO);
    }

    @Test
    void grantsOnlyTheExactRuntimeOwnerAndWorkspaceGeneration() {
        registry.register(RUN_ONE, RuntimeMode.JAVA_V1, 2, 32, 7L);

        assertTrue(registry.permits(RuntimeMode.JAVA_V1, 2, 32, 7L));
        assertEquals(RUN_ONE, registry.requireRunId(RuntimeMode.JAVA_V1, 2, 32, 7L));
        assertFalse(registry.permits(RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2, 2, 32, 7L));
        assertFalse(registry.permits(RuntimeMode.JAVA_V1, 13, 32, 7L));
        assertFalse(registry.permits(RuntimeMode.JAVA_V1, 2, 29, 7L));
        assertFalse(registry.permits(RuntimeMode.JAVA_V1, 2, 32, 8L));

        registry.clear(RUN_ONE);
        assertFalse(registry.permits(RuntimeMode.JAVA_V1, 2, 32, 7L));
    }

    @Test
    void refusesAmbiguousRunsForTheSameExactOwner() {
        registry.register(RUN_ONE, RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2, 2, 32, 7L);
        registry.register(RUN_TWO, RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2, 2, 32, 7L);

        assertThrows(
                IllegalStateException.class,
                () -> registry.requireRunId(
                        RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2, 2, 32, 7L));
    }

    @Test
    void rejectsMalformedAuthorityInsteadOfCreatingABroadGrant() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("", RuntimeMode.JAVA_V1, 2, 32, 7L));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(RUN_ONE, null, 2, 32, 7L));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(RUN_ONE, RuntimeMode.JAVA_V1, 0, 32, 7L));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(RUN_ONE, RuntimeMode.JAVA_V1, 2, 32, 0L));
    }
}
