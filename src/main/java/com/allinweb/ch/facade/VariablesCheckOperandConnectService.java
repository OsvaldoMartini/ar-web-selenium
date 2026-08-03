package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.VariablesCheckOperandConnectTransaction.ConnectResult;
import com.allinweb.ch.model.VariablesCheckOperandConnectV1;
import java.sql.Connection;
import java.sql.SQLException;

/** Opens the dedicated connection used by one CheckValue right-operand connection. */
public final class VariablesCheckOperandConnectService {
    private static final VariablesCheckOperandConnectService INSTANCE =
            new VariablesCheckOperandConnectService();
    private final VariablesCheckOperandConnectTransaction transaction =
            new VariablesCheckOperandConnectTransaction();

    private VariablesCheckOperandConnectService() {}

    public static VariablesCheckOperandConnectService getInstance() {
        return INSTANCE;
    }

    public ConnectResult connect(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesCheckOperandConnectV1.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            return transaction.execute(connection, owner, request);
        }
    }
}
