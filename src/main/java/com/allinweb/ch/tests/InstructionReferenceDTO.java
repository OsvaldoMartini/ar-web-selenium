package com.allinweb.ch.tests;

class InstructionReferenceDTO {
    private String referenceType;
    private String value;

    public InstructionReferenceDTO(String referenceType, String value) {
        this.referenceType = referenceType;
        this.value = value;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getValue() {
        return value;
    }
}
