package com.allinweb.ch.persistence;

import java.util.*;
import javax.persistence.*;
import lombok.Data;

@Entity
@Table(name = "component_reference")
@Data
public class ComponentReferenceDTO extends BaseDTO {
    @Column(name = "value", length = 10000)
    private String value;

    @Column(name = "instruction_id", nullable = false) // Adds non-null constraint on the foreign key
    private Integer instructionId;

    @Column(name = "bot_job_id")
    private Integer botJobId;
}
