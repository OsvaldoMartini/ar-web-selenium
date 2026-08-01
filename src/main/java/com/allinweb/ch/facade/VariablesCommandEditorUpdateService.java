package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.VariablesCommandEditorUpdateTransaction.UpdateResult;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Connection and retry boundary for the isolated Variables Command Editor UPDATE. */
public final class VariablesCommandEditorUpdateService {
    private static final int MAX_RESULTS = 512;
    private static final VariablesCommandEditorUpdateService INSTANCE =
            new VariablesCommandEditorUpdateService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new VariablesCommandEditorUpdateTransaction());
    private final ConnectionProvider connections;
    private final VariablesCommandEditorUpdateTransaction transaction;
    private final Gson gson = new Gson();
    private final LinkedHashMap<String, CompletedRequest> completed =
            new LinkedHashMap<>(32, .75f, true);

    VariablesCommandEditorUpdateService(
            ConnectionProvider connections,
            VariablesCommandEditorUpdateTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static VariablesCommandEditorUpdateService getInstance() { return INSTANCE; }

    public synchronized UpdateResult update(
            int homeBankingId, int botJobId, long workspaceEpoch,
            VariablesCommandEditorUpdateV1.Request request) throws SQLException {
        if (request == null || request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw new MutationRefusedException(
                    "COMMAND_UPDATE_REQUEST_ID_REQUIRED", "A command-update request ID is required.");
        }
        String key = homeBankingId + ":" + botJobId + ":" + request.requestId().trim();
        String fingerprint = gson.toJson(request);
        CompletedRequest previous = completed.get(key);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new MutationRefusedException(
                        "COMMAND_UPDATE_REQUEST_ID_REUSED",
                        "This request ID was already used for different command-update data.");
            }
            return previous.result().asDuplicate();
        }
        UpdateResult result;
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
    private record CompletedRequest(String fingerprint, UpdateResult result) {}
}
