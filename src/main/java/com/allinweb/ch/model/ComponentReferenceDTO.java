package com.allinweb.ch.model;

import lombok.Data;

@Data
public class ComponentReferenceDTO {
    private String referenceType;

    private String value;

    private Integer instructionId;

    private Integer homeBankingId;
}
