package com.allinweb.ch.model;

/** Immutable, persisted identity used by a Bot Job toolbar operation from dispatch to completion. */
public record BotJobToolbarContext(
        long workspaceEpoch,
        int botJobId,
        int homeBankingId,
        int homeUrlId,
        String name,
        String projectType,
        String organizationName,
        String endpointUrl,
        boolean active) {

    public BotJobToolbarContext {
        name = safe(name);
        projectType = safe(projectType);
        organizationName = safe(organizationName);
        endpointUrl = safe(endpointUrl);
    }

    public BotJobLoadDTO executionBotJob() {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(botJobId);
        botJob.setBotJobId(botJobId);
        botJob.setName(name);
        botJob.setPriority(projectType);
        botJob.setHomeBankingId(homeBankingId);
        botJob.setHomeUrlId(homeUrlId);
        botJob.setActive(active);
        return botJob;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
