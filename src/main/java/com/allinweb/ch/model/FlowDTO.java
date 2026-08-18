package com.allinweb.ch.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the {@code flow} table — a named, ordered execution sequence
 * scoped to a bot job. Phase 1b of ROADMAP_9.
 *
 * <p>{@code steps} is populated only when explicitly loaded with the steps
 * (e.g. by an editor opening a flow). List operations on the flow itself
 * (rename, list-by-bot-job) leave it null/empty for performance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowDTO {
    private Integer id;
    private Integer botJobId;
    private String name;
    private String description;
    private String createdAt;
    private String updatedAt;
    private List<FlowStepDTO> steps = new ArrayList<>();
}
