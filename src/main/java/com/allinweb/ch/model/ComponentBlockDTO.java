package com.allinweb.ch.model;

import lombok.Data;

@Data
public class ComponentBlockDTO {
    private Integer homeBankingId;
    private int blockOrderNumber;
    private String name;
    private String description;
    private Integer typeId;
    private String exportFile;
    private Boolean active;
    private Integer wait;
}
