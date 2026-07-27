package com.allinweb.ch.socket;

import java.util.Set;

/**
 * Selects the realtime snapshot path after an instruction mutation.
 *
 * <p>Component mutations must never fall back to the generic snapshot publisher. That publisher
 * serializes the process-wide nested component cache, which may still describe the graph from
 * before the database transaction. The authoritative component path reloads that graph first.
 */
final class InstructionMutationSnapshotPolicy {

    private static final Set<String> COMPONENT_GRID_MUTATIONS = Set.of(
            "ACTIONS_UPDATE",
            "BLOCK_CREATE",
            "BLOCK_MOVE",
            "BLOCK_ORDER",
            "BLOCK_ROLLBACK",
            "BLOCKS_SPLITTER",
            "BLOCK_STATUS",
            "BLOCK_UPDATE",
            "COMPONENT_ROW_MOVE",
            "CREATE_BLOCK",
            "DELETE_BLOCK",
            "DELETE_INSTRUCTION",
            "EDIT_OPERATION",
            "FORCE_COORDINATES_UPDATE",
            "INSERT_AFTER",
            "INSERT_AFTER_ELSEIF",
            "INSERT_BEFORE",
            "INSERT_BEFORE_ELSEIF",
            "INSERT_NEW",
            "INSTRUCTION_STATUS",
            "ROW_MOVE",
            "ROW_UPDATE");

    private InstructionMutationSnapshotPolicy() {}

    static boolean isGridMutation(String mutationType) {
        return mutationType != null && COMPONENT_GRID_MUTATIONS.contains(mutationType);
    }

    /**
     * A component grid mutation always needs a database-backed snapshot, including when the
     * mutation is refused. The frontend applies some graph changes optimistically; reloading on
     * failure is what restores empty-block metadata and the authoritative row order.
     */
    static boolean shouldReloadAuthoritativeComponentSnapshot(
            boolean componentSession, String mutationType) {
        return componentSession && isGridMutation(mutationType);
    }

    static boolean requiresAuthoritativeComponentSnapshot(
            boolean mutationSucceeded, boolean componentSession, String mutationType) {
        return mutationSucceeded
                && shouldReloadAuthoritativeComponentSnapshot(componentSession, mutationType);
    }

    static boolean shouldPublishGenericSnapshot(
            boolean alreadySent,
            boolean authoritativeBotJobSnapshotPublished,
            boolean authoritativeComponentSnapshotRequired) {
        return !alreadySent
                && !authoritativeBotJobSnapshotPublished
                && !authoritativeComponentSnapshotRequired;
    }
}
