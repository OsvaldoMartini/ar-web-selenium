package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.VariablesVariableAutoResolveTransaction.AutoResolveResult;
import com.allinweb.ch.model.VariablesVariableAutoResolveV1;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Connection and idempotency boundary for the Variables variable auto-resolution. */
public final class VariablesVariableAutoResolveService {
    private static final int MAX_RESULTS = 512;
    private static final VariablesVariableAutoResolveService INSTANCE =
            new VariablesVariableAutoResolveService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new VariablesVariableAutoResolveTransaction());
    private final ConnectionProvider connections;
    private final VariablesVariableAutoResolveTransaction transaction;
    private final Gson gson = new Gson();
    private final LinkedHashMap<String, CompletedRequest> completed =
            new LinkedHashMap<>(32, .75f, true);

    VariablesVariableAutoResolveService(
            ConnectionProvider connections,
            VariablesVariableAutoResolveTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static VariablesVariableAutoResolveService getInstance() { return INSTANCE; }

    public synchronized AutoResolveResult resolve(
            int homeBankingId, int botJobId, long workspaceEpoch,
            VariablesVariableAutoResolveV1.Request request) throws SQLException {
        if (request == null || request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw new MutationRefusedException(
                    "VARIABLE_AUTO_RESOLVE_REQUEST_ID_REQUIRED",
                    "A variable-resolution request ID is required.");
        }
        String key = homeBankingId + ":" + botJobId + ":" + request.requestId().trim();
        String fingerprint = gson.toJson(request);
        CompletedRequest previous = completed.get(key);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new MutationRefusedException(
                        "VARIABLE_AUTO_RESOLVE_REQUEST_ID_REUSED",
                        "This request ID was already used for different variable-resolution data.");
            }
            return previous.result().asDuplicate();
        }
        AutoResolveResult result;
        try (Connection connection = connections.open()) {
            result = transaction.execute(
                    connection,
                    AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch),
                    request);
        }
        completed.put(key, new CompletedRequest(fingerprint, result));
        while (completed.size() > MAX_RESULTS) completed.remove(completed.keySet().iterator().next());
        return result;
    }

    @FunctionalInterface interface ConnectionProvider { Connection open() throws SQLException; }
    private record CompletedRequest(String fingerprint, AutoResolveResult result) {}
}
