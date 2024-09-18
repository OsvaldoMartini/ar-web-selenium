package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class InstructionReferenceLoadDTO {
    private String referenceType;
    private String value;

    @Override
    public String toString() {
        return "InstructionReferenceLoadDTO{" + "referenceType='"
                + referenceType + '\'' + ", value='"
                + value + '\'' + '}';
    }
}
