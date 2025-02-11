package com.allinweb.ch.tests;

import org.hibernate.annotations.Table;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@EqualsAndHashCode(callsuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "cmt_tags")
public class Topic extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

}