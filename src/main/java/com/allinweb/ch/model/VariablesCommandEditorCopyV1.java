package com.allinweb.ch.model;

import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Placement;

/** Pure fresh-ID COPY NEW contract used only by the Variables Command Editor modal. */
public final class VariablesCommandEditorCopyV1 {
    public static final int CONTRACT_VERSION = 1;

    private VariablesCommandEditorCopyV1() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            Integer sourceInstructionId,
            Integer targetBlockId,
            Placement placement,
            Configuration configuration) {}
}
