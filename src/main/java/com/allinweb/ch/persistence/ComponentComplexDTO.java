package com.allinweb.ch.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "component_complex")
@Data
public class ComponentComplexDTO extends BaseDTO {

    @Column(name = "instruction_id")
    private Integer instructionId;

    @Column(name = "order_number")
    private int orderNumber;

    @Column(name = "instruction")
    private String instruction;

    @Column(name = "way")
    private String way;

    @Column(name = "bot_job_id")
    private Integer botJobId;
}
