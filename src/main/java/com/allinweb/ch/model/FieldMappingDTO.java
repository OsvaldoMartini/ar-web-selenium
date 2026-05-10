package com.allinweb.ch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the {@code use_case_field_mapping} table — a single pair between
 * an API spec field (left side of the Functional Test tab) and a bot-job
 * INPUT instruction (right side).
 *
 * <p>{@code apiKey} is the React-side composite identifier
 * ({@code <specFile>::<fieldName>}). {@code apiSpecFile} and {@code apiFieldName}
 * are denormalised so the UI can show meaningful labels even when the spec
 * file is no longer loaded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMappingDTO {
    private Long id;
    private Integer botJobId;
    private Integer useCaseId; // Phase 1a (ROADMAP_9) — group mappings under a named Use Case
    private String apiKey;
    private String apiSpecFile;
    private String apiFieldName;
    private Integer botInstructionId;
    private String createdAt;
    private String updatedAt;
}
