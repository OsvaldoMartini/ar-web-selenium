package com.allinweb.ch.model;

import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Placement;

/** Dedicated ADD COMMAND contract. It cannot reference or copy an existing instruction. */
public final class VariablesCommandEditorCreateV1 {
    public static final int CONTRACT_VERSION = 1;

    private VariablesCommandEditorCreateV1() {}

    public record Request(
            Integer contractVersion,
            String requestId,
            String bindingEpoch,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision,
            Integer targetBlockId,
            Placement placement,
            Configuration configuration,
            String targetAction) {}
}
