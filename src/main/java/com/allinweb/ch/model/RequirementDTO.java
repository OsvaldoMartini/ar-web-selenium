package com.allinweb.ch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the {@code requirement} table — a high-context description of
 * a functional case that must be executed. Many-to-many linkable to Use
 * Cases (Functional Cases) and Flows (Flow Tests) via the
 * {@code requirement_use_case} / {@code requirement_flow} tables.
 *
 * <p>{@code priority} is a free-text field today (LOW/MEDIUM/HIGH/CRITICAL
 * by convention; not a DB enum so future values don't need a migration).
 * {@code status} defaults to {@code "ACTIVE"}; ARCHIVED requirements are
 * excluded from coverage rollups in v1.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequirementDTO {
    private Integer id;
    private Integer botJobId;
    private String externalRef;
    private String title;
    private String description;
    private String priority;
    private String status;
    private String createdAt;
    private String updatedAt;
    /** Number of Functional Cases (use_case rows) linked to this requirement. */
    private Integer linkedUseCaseCount;
    /** Number of Flow Tests linked to this requirement. */
    private Integer linkedFlowCount;
}
