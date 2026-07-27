package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstructionMutationSnapshotPolicyTest {

    @Test
    void componentRowMoveSelectsAuthoritativeRefreshAndSuppressesStaleGenericSnapshot() {
        boolean authoritativeComponentRefresh =
                InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                        true, true, "COMPONENT_ROW_MOVE");

        assertTrue(authoritativeComponentRefresh);
        assertFalse(InstructionMutationSnapshotPolicy.shouldPublishGenericSnapshot(
                false, false, authoritativeComponentRefresh));
    }

    @Test
    void legacyComponentRowMoveUsesTheSameAuthoritativeRefresh() {
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "ROW_MOVE"));
    }

    @Test
    void failedOrNonComponentMovesDoNotSelectTheComponentRefresh() {
        assertFalse(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                false, true, "COMPONENT_ROW_MOVE"));
        assertFalse(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, false, "COMPONENT_ROW_MOVE"));
        assertFalse(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "TEST_RUN"));
    }

    @Test
    void failedComponentMutationStillReloadsAuthoritativeStateAndSuppressesGenericSnapshot() {
        boolean reload =
                InstructionMutationSnapshotPolicy.shouldReloadAuthoritativeComponentSnapshot(
                        true, "DELETE_BLOCK");

        assertTrue(reload);
        assertFalse(InstructionMutationSnapshotPolicy.shouldPublishGenericSnapshot(
                false, false, reload));
    }

    @Test
    void gridMutationClassificationIsExact() {
        assertTrue(InstructionMutationSnapshotPolicy.isGridMutation("ROW_MOVE"));
        assertTrue(InstructionMutationSnapshotPolicy.isGridMutation("COMPONENT_ROW_MOVE"));
        assertFalse(InstructionMutationSnapshotPolicy.isGridMutation("ROW_MOVE_FORGED"));
        assertFalse(InstructionMutationSnapshotPolicy.isGridMutation(null));
    }

    @Test
    void everyComponentGridMutationUsesAnAuthoritativeRefresh() {
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "CREATE_BLOCK"));
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "BLOCK_MOVE"));
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "BLOCKS_SPLITTER"));
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "DELETE_INSTRUCTION"));
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "BLOCK_ROLLBACK"));
        assertTrue(InstructionMutationSnapshotPolicy.requiresAuthoritativeComponentSnapshot(
                true, true, "EDIT_OPERATION"));
    }

    @Test
    void ordinaryMutationsKeepTheGenericSnapshotPath() {
        assertTrue(InstructionMutationSnapshotPolicy.shouldPublishGenericSnapshot(
                false, false, false));
    }
}
