package com.allinweb.ch.persistence;

import java.io.Serializable;
import javax.persistence.*;
import lombok.Data;

@Entity()
@Table(name = "home_banking")
@Data
public class HomeBankingDTO extends BaseDTO implements Serializable {
    @Column(name = "url", length = 10000)
    private String url;

    @Column(name = "name")
    @OrderBy("name DESC")
    private String name;

    @Column(name = "priority", length = 10000)
    private String priority;

    @Column(name = "search_config", length = 10000)
    private String searchConfig;

    @Column(name = "options_config", length = 10000)
    private String optionsConfig;

    @Column(name = "cookies", length = 10000)
    private String cookies;

    @Column(name = "driver_session", length = 10000)
    private String driverSession;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;
}
