package com.allinweb.ch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeUrlDTO {
    private Integer id;
    private String name;
    private String url;
    private Integer homeBankingId;
    private String orgName;

    public HomeUrlDTO(Integer id, String url, Integer homeBankingId, String orgName) {
        this(id, "TEST", url, homeBankingId, orgName);
    }
}
