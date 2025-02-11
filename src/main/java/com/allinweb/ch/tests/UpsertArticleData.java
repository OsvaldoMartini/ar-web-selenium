package com.allinweb.ch.tests;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record UpsertArticleData(
        Integer articleId,
        Integer kould,
        List<String> additionalTags,
        List<String> additionalLocations,
        List<String> additionalSectors,
        List<String> additionalTopics,
        Boolean gatingEnabled,
        LocalDateTime gatingStartDate
) {

}