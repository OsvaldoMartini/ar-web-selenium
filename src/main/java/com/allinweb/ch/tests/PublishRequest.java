package com.allinweb.ch.tests;

public record PublishRequest(

        Integer kould,
        List<String> additionalTags,
        List<String> additionalLocations,
        List<String> additionalSectors,
        List<String> additionalTopics,
        @NotNull(message = "Gating Enabled is mandatory")
        Boolean gatingEnabled,
        @NotNull(message = "Gating Start Date is mandatory")
        LocalDateTime gatingStartDate

) {

}