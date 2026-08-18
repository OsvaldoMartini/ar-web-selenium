package com.allinweb.ch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the {@code use_case} table — a named group of field mappings
 * within a single bot job. Phase 1a of ROADMAP_9.
 *
 * <p>One bot job has many use cases. Each use case has many
 * {@link FieldMappingDTO} rows. The "Default" use case is auto-created by
 * migration M20260511 for any bot job that already had ungrouped mappings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UseCaseDTO {
    private Integer id;
    private Integer botJobId;
    private String name;
    private String description;
    private String createdAt;
    private String updatedAt;
}
