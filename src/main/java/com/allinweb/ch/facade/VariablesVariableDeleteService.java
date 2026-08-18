package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.VariablesVariableDeleteTransaction.DeleteResult;
import com.allinweb.ch.model.VariablesWorkspaceVariableDelete;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Opens the dedicated connection used by one authoritative Variables deletion transaction. */
public final class VariablesVariableDeleteService {

    private static final VariablesVariableDeleteService INSTANCE =
            new VariablesVariableDeleteService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new VariablesVariableDeleteTransaction());

    private final ConnectionProvider connections;
    private final VariablesVariableDeleteTransaction transaction;

    VariablesVariableDeleteService(
            ConnectionProvider connections,
            VariablesVariableDeleteTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static VariablesVariableDeleteService getInstance() {
        return INSTANCE;
    }

    public DeleteResult delete(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesWorkspaceVariableDelete.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = connections.open()) {
            return transaction.execute(connection, owner, request);
        }
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }
}
