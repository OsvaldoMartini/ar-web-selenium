package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.VariablesInstructionStatusTransaction.Result;
import com.allinweb.ch.model.VariablesWorkspaceInstructionStatus;
import java.sql.Connection;
import java.sql.SQLException;

/** Opens the dedicated database transaction for one Variables command-status change. */
public final class VariablesInstructionStatusService {
    private static final VariablesInstructionStatusService INSTANCE =
            new VariablesInstructionStatusService();
    private final VariablesInstructionStatusTransaction transaction =
            new VariablesInstructionStatusTransaction();

    private VariablesInstructionStatusService() {}

    public static VariablesInstructionStatusService getInstance() {
        return INSTANCE;
    }

    public Result update(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesWorkspaceInstructionStatus.Request request)
            throws SQLException {
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            return transaction.execute(
                    connection,
                    AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch),
                    request);
        }
    }
}
