package com.allinweb.ch.component.model;

import java.util.List;
import lombok.Data;

@Data
public class BotJobLoadDTO {
    private int id;
    private String name;
    private int botJobId;
    private String description;
    private String priority;
    private int blockOrderNumber;
    private int homeBankingId;
    private int typeId;
    private String botJobName;
    private List<BlockLoadDTO> blockLoadDTOList;

    @Override
    public String toString() {
        return "BotJobLoadDTO{" + "id="
                + id + ", name='"
                + name + '\'' + ", blockLoadDTOList="
                + blockLoadDTOList + '}';
    }
}
