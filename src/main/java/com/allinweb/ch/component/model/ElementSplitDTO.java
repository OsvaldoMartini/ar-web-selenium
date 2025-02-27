package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class ElementSplitDTO {
    private String type;
    private String sessionId;
    private String operationId;
    private ElementDTO[] details;
}
