package com.allinweb.ch.model;

/**
 * Discriminator for {@link FlowStepDTO}. Phase 1b of ROADMAP_9 — only the four
 * v1 step types are recognised. Future types (e.g. {@code AUTH}, {@code BRANCH})
 * extend this enum without a schema change since the per-type fields live in
 * {@code flow_step.payload_json}.
 */
public enum FlowStepTypeEnum {
    API, // HTTP call against a loaded ApiSpec operation
    UI, // Bot-job block execution (writes substituted values into instructions)
    WAIT, // Fixed delay
    ASSERT // Pool-variable expression check
}
