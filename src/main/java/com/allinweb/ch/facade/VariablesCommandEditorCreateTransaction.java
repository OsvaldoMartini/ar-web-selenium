package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.VariablesCommandEditorCreateV1;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Create-only transaction boundary for Variables ADD COMMAND. */
public final class VariablesCommandEditorCreateTransaction {
    private final VariablesCommandEditorCopyTransaction insertion;

    public VariablesCommandEditorCreateTransaction() {
        this(new VariablesCommandEditorCopyTransaction());
    }

    VariablesCommandEditorCreateTransaction(VariablesCommandEditorCopyTransaction insertion) {
        this.insertion = Objects.requireNonNull(insertion, "insertion");
    }

    public CreateResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCommandEditorCreateV1.Request request) throws SQLException {
        try {
            VariablesCommandEditorCopyTransaction.CopyResult committed =
                    insertion.executeCreate(connection, owner, request);
            return new CreateResult(
                    committed.owner(),
                    committed.workspaceEpoch(),
                    committed.requestId(),
                    committed.createdInstructionId(),
                    committed.targetBlockId(),
                    committed.instructionOrderNumber(),
                    committed.previousGraphVersion(),
                    committed.committedGraphVersion(),
                    committed.graphRevision(),
                    committed.duplicate());
        } catch (MutationRefusedException refused) {
            throw new MutationRefusedException(
                    createCode(refused.code()),
                    createMessage(refused.getMessage()));
        }
    }

    private static String createCode(String code) {
        if (code == null || code.isBlank()) return "COMMAND_CREATE_REFUSED";
        return code.replace("COMMAND_COPY_", "COMMAND_CREATE_");
    }

    private static String createMessage(String message) {
        if (message == null || message.isBlank()) return "Add Command was refused.";
        return message
                .replace("command-copy", "Add Command")
                .replace("command copy", "Add Command")
                .replace("Command copy", "Add Command")
                .replace("COPY NEW", "Add Command")
                .replace("copied", "added")
                .replace("copy", "command");
    }

    public record CreateResult(
            com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            int createdInstructionId,
            int targetBlockId,
            int instructionOrderNumber,
            long previousGraphVersion,
            long committedGraphVersion,
            String graphRevision,
            boolean duplicate) {
        public CreateResult asDuplicate() {
            return duplicate ? this : new CreateResult(
                    owner,
                    workspaceEpoch,
                    requestId,
                    createdInstructionId,
                    targetBlockId,
                    instructionOrderNumber,
                    previousGraphVersion,
                    committedGraphVersion,
                    graphRevision,
                    true);
        }
    }
}
