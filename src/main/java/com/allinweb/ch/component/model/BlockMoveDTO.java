package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data

public class BlockMoveDTO {
    private String type;
    private Integer homeBankingId;
    private Integer botJobId;
    private String sessionId;
    private List<BlockOrderDetailDTO> updatedBlocks;
}
