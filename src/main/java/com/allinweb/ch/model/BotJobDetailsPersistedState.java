package com.allinweb.ch.model;

import java.util.List;

/** Allowlisted database projection used to build Bot Job Details state without shared list mutation. */
public record BotJobDetailsPersistedState(
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
        List<Environment> environments,
        List<Block> blocks) {

    public BotJobDetailsPersistedState {
        environments = environments == null ? List.of() : List.copyOf(environments);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public record Environment(int id, String name, String url, int homeBankingId) {}

    public record Block(
            int id, int order, String name, String description, int typeId, boolean active, int waitSeconds) {}
}
