package com.allinweb.ch.tests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@EqualsAndHashCode(callsuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "cmt_locations")
public class Location extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "country_cd")
    private String countryCd;

    @Column(name = "country_name")
    private String countryName;

    @Column(name = "country_iso3")
    private String countryIso3;

}