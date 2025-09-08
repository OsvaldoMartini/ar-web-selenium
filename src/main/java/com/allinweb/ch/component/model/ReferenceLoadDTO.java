package com.allinweb.ch.component.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data

public class ReferenceLoadDTO {
    private Integer id;
    private Integer homeBankingId;
    private Integer botJobId;
    private String referenceType;
    private String value;
    private Integer instructionId;
}
