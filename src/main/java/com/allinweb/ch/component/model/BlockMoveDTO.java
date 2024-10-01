package com.allinweb.ch.component.model;

import lombok.Data;

import java.util.List;

@Data
public class BlockMoveDTO {
    private String type;
    private List<BlockOrderDetailDTO> updatedBlocks;
}
