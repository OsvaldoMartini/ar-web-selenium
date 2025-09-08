package com.allinweb.ch.component.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data

public class BlockOrderDetailDTO {
    private Integer homeBankId;
    private Integer botJobId;
    private Integer blockId;
    private String blockName;
    private Integer blockOrderNumber;
}
