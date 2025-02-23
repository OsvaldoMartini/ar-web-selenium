package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class ElementSplitDTO {
    private String type;
    private ElementDTO[] details;
}
