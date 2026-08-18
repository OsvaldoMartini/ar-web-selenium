package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.VariablesCheckLeftOperandTransaction.Result;
import com.allinweb.ch.model.VariablesCheckLeftOperandV1;
import java.sql.Connection;
import java.sql.SQLException;

/** Opens the dedicated connection for one CheckValue LEFT-slot mutation. */
public final class VariablesCheckLeftOperandService {
    private static final VariablesCheckLeftOperandService INSTANCE =
            new VariablesCheckLeftOperandService();
    private final VariablesCheckLeftOperandTransaction transaction =
            new VariablesCheckLeftOperandTransaction();

    private VariablesCheckLeftOperandService() {}

    public static VariablesCheckLeftOperandService getInstance() {
        return INSTANCE;
    }

    public Result mutate(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesCheckLeftOperandV1.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            return transaction.execute(connection, owner, request);
        }
    }
}
