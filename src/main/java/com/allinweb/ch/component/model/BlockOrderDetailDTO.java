package com.allinweb.ch.component.model;

import lombok.Data;

@Data
public class BlockOrderDetailDTO {
    private Integer homeBankId;
    private Integer blockId;
    private Integer botJobId;
    private Integer blockOrderNumber;
    private String blockName;
}
