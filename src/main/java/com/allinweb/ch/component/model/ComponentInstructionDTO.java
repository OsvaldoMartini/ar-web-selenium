package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class ComponentInstructionDTO {

    private Integer homeBankingId;

    private int instructionOrderNumber;

    private String actions;

    private String name;

    private String xpath;

    private String coordinates;

    private Boolean forceCoordinates;

    private String iFrameXPath;

    private String tagName;

    private String shadowHost;

    private String shadowRoot;

    private String cssSelector;

    private String description;

    private String operation;

    private Boolean optional;

    private Boolean blockMarked;

    private String defaultValue;

    private Integer actionCustomMaxWaitSec;

    private Integer onHoldSeconds;

    private Boolean codified;

    private Boolean exportToABR;

    private Boolean active;

    private Integer blockId;

    private Boolean executed;

    private String priority;

    private Integer variableId;

    private Integer parentId;
}
