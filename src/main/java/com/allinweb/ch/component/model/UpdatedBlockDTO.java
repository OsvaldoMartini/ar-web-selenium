package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class UpdatedBlockDTO {
    private int blockId;
    private int botJobId;
    private String blockName;
    private int blockOrderNumber;
}
