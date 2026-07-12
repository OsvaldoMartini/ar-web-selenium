package com.allinweb.ch.model;

import java.util.List;

/** Immutable state published to every React surface in the active Bot Job Details workspace. */
public record BotJobDetailsState(
        long revision,
        int botJobId,
        String name,
        String description,
        String projectType,
        boolean active,
        int homeBankingId,
        String organizationName,
        int homeUrlId,
        String environmentName,
        String environmentUrl,
        int navigationTimeSeconds,
        List<Environment> environments,
        List<Block> blocks,
        Capabilities capabilities,
        String executionState,
        String activeSurface,
        boolean componentsVisible) {

    public BotJobDetailsState {
        environments = environments == null ? List.of() : List.copyOf(environments);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public record Environment(int id, String name, String url, int homeBankingId, String organizationName) {}

    public record Block(
            int id, int order, String name, String description, int typeId, boolean active, int waitSeconds) {}

    public record Capabilities(
            boolean canUseWorkspaceActions,
            boolean canEditMetadata,
            boolean canUsePreScan,
            boolean canShowComponents,
            boolean canExecute,
            boolean canLaunch,
            boolean canOpenOrganizations) {}
}
