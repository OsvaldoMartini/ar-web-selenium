package com.allinweb.ch.tests;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ArticleStatus {

    DRAFT("Draft"),
    NEW("New"),
    ACTIVE("Active"),
    IN_ACTIVE("In active");

    private final String label;
    public static ArticleStatus from(String status) {

        for (ArticleStatus articleStatus : ArticleStatus.values()) {
            if (articleStatus.getLabel().equals(status)) {
                return articleStatus;
            }
        }
        return ArticleStatus.NEW;
    }
}