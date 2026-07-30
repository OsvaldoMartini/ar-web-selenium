package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.CommitResult;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Connection boundary for the additive Bot Job instruction-graph transaction.
 *
 * <p>The caller supplies an already authenticated Bot Job/workspace identity and the complete
 * React-planned mutation. This service opens one database connection and delegates exact
 * inspection or atomic persistence; it contains no drag grouping or relationship inference.
 */
public final class BotJobGraphMutationService {
    private static final BotJobGraphMutationService INSTANCE =
            new BotJobGraphMutationService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new BotJobGraphMutationTransaction());

    private final ConnectionProvider connections;
    private final BotJobGraphMutationTransaction transaction;

    BotJobGraphMutationService(
            ConnectionProvider connections,
            BotJobGraphMutationTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static BotJobGraphMutationService getInstance() {
        return INSTANCE;
    }

    public GraphSnapshot inspect(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = connections.open()) {
            return transaction.inspect(connection, owner);
        }
    }

    public CommitResult mutate(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            InstructionGraphMutationV3.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = connections.open()) {
            return transaction.execute(connection, owner, request);
        }
    }

    public CommitResult mutateVariablesInstructionMove(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            InstructionGraphMutationV3.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = connections.open()) {
            return transaction.executeVariablesInstructionMove(
                    connection, owner, request);
        }
    }

    public CommitResult mutateVariablesInstructionCrossBlockMove(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            InstructionGraphMutationV3.Request request)
            throws SQLException {
        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        try (Connection connection = connections.open()) {
            return transaction.executeVariablesInstructionCrossBlockMove(
                    connection, owner, request);
        }
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }
}
