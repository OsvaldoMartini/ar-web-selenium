package com.allinweb.ch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the {@code flow_step} table. Phase 1b of ROADMAP_9.
 *
 * <p>{@code payloadJson} carries the type-specific shape:
 * <ul>
 *   <li>{@code API}: {@code {method, path, headers, body, captures: [{var, jsonpath}],
 *       substitutes: [{var, target}]}}</li>
 *   <li>{@code UI}: {@code {refBlockId, useCaseId, captures: [...], substitutes: [{botInstructionId, var}]}}</li>
 *   <li>{@code WAIT}: {@code {seconds}}</li>
 *   <li>{@code ASSERT}: {@code {expr, expected}}</li>
 * </ul>
 *
 * <p>The Java executor (Phase 3) parses {@code payloadJson} per
 * {@code stepType}; v1 (Phase 2a/b/c) only persists and renders it without
 * dispatching anything.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowStepDTO {
    private Long id;
    private Integer flowId;
    private Integer stepOrder;
    private String name;
    private String stepType; // matches FlowStepTypeEnum.name()
    private String payloadJson;
    private String createdAt;
    private String updatedAt;
}
