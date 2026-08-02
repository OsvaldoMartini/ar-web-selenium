package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.VariablesCommandDeleteTransaction.DeleteResult;
import com.allinweb.ch.model.VariablesWorkspaceCommandDelete;
import java.sql.Connection;
import java.sql.SQLException;

/** Opens the dedicated connection used by one Variables command-delete transaction. */
public final class VariablesCommandDeleteService {
    private static final VariablesCommandDeleteService INSTANCE =
            new VariablesCommandDeleteService();
    private final VariablesCommandDeleteTransaction transaction =
            new VariablesCommandDeleteTransaction();

    private VariablesCommandDeleteService() {}

    public static VariablesCommandDeleteService getInstance() {
        return INSTANCE;
    }

    public DeleteResult delete(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesWorkspaceCommandDelete.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            return transaction.execute(connection, owner, request);
        }
    }
}
