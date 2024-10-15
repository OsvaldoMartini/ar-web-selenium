package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class BotJobLoadDTO {
    private int id;
    private String name;
    private String description;
    private String priority;
    private int homeBankingId;
    private List<BlockLoadDTO> blockLoadDTOList;

    @Override
    public String toString() {
        return "BotJobLoadDTO{" + "id="
                + id + ", name='"
                + name + '\'' + ", blockLoadDTOList="
                + blockLoadDTOList + '}';
    }
}
