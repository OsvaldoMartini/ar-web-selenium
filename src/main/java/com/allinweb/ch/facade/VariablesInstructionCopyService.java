package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.VariablesInstructionCopyTransaction.CopyResult;
import com.allinweb.ch.model.VariablesInstructionCopyV1;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Opens the dedicated connection and provides retry-safe request correlation for one authoritative
 * Variables instruction-copy transaction.
 */
public final class VariablesInstructionCopyService {

    private static final int MAX_IDEMPOTENT_RESULTS = 512;
    private static final VariablesInstructionCopyService INSTANCE =
            new VariablesInstructionCopyService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new VariablesInstructionCopyTransaction());

    private final ConnectionProvider connections;
    private final VariablesInstructionCopyTransaction transaction;
    private final Gson gson = new Gson();
    private final LinkedHashMap<String, CompletedRequest> successfulRequests =
            new LinkedHashMap<>(32, 0.75f, true);

    VariablesInstructionCopyService(
            ConnectionProvider connections,
            VariablesInstructionCopyTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static VariablesInstructionCopyService getInstance() {
        return INSTANCE;
    }

    public synchronized CopyResult copy(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesInstructionCopyV1.Request request)
            throws SQLException {
        if (request == null
                || request.requestId() == null
                || request.requestId().trim().isEmpty()) {
            throw new MutationRefusedException(
                    "VARIABLE_COPY_REQUEST_ID_REQUIRED",
                    "A Variables instruction-copy request ID is required.");
        }
        String idempotencyKey =
                homeBankingId + ":" + botJobId + ":" + request.requestId().trim();
        String fingerprint = gson.toJson(request);
        CompletedRequest completed = successfulRequests.get(idempotencyKey);
        if (completed != null) {
            if (!completed.fingerprint().equals(fingerprint)) {
                throw new MutationRefusedException(
                        "VARIABLE_COPY_REQUEST_ID_REUSED",
                        "This request ID was already used for different instruction-copy data.");
            }
            return completed.result().asDuplicate();
        }

        AuthenticatedBotJob owner =
                AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch);
        CopyResult result;
        try (Connection connection = connections.open()) {
            result = transaction.execute(connection, owner, request);
        }
        successfulRequests.put(
                idempotencyKey, new CompletedRequest(fingerprint, result));
        while (successfulRequests.size() > MAX_IDEMPOTENT_RESULTS) {
            String eldest = successfulRequests.keySet().iterator().next();
            successfulRequests.remove(eldest);
        }
        return result;
    }

    int completedRequestCount() {
        return successfulRequests.size();
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private record CompletedRequest(String fingerprint, CopyResult result) {}
}
