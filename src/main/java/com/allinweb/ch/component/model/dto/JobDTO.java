package com.allinweb.ch.component.model.dto;

import java.util.List;

public class JobDTO {
    private String name;
    private String description;
    private List<BlockDTO> blocks;

    public JobDTO(String name, String description, List<BlockDTO> blocks) {
        this.name = name;
        this.description = description;
        this.blocks = blocks;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<BlockDTO> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockDTO> blocks) {
        this.blocks = blocks;
    }
}
