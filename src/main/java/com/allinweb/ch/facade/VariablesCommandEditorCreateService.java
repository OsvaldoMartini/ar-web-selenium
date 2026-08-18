package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.VariablesCommandEditorCreateTransaction.CreateResult;
import com.allinweb.ch.model.VariablesCommandEditorCreateV1;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Connection and idempotency boundary dedicated to Variables ADD COMMAND. */
public final class VariablesCommandEditorCreateService {
    private static final int MAX_RESULTS = 512;
    private static final VariablesCommandEditorCreateService INSTANCE =
            new VariablesCommandEditorCreateService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new VariablesCommandEditorCreateTransaction());

    private final ConnectionProvider connections;
    private final VariablesCommandEditorCreateTransaction transaction;
    private final Gson gson = new Gson();
    private final LinkedHashMap<String, CompletedRequest> completed =
            new LinkedHashMap<>(32, .75f, true);

    VariablesCommandEditorCreateService(
            ConnectionProvider connections,
            VariablesCommandEditorCreateTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static VariablesCommandEditorCreateService getInstance() {
        return INSTANCE;
    }

    public synchronized CreateResult create(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            VariablesCommandEditorCreateV1.Request request) throws SQLException {
        if (request == null || request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw new MutationRefusedException(
                    "COMMAND_CREATE_REQUEST_ID_REQUIRED",
                    "An Add Command request ID is required.");
        }
        String key = homeBankingId + ":" + botJobId + ":" + request.requestId().trim();
        String fingerprint = gson.toJson(request);
        CompletedRequest previous = completed.get(key);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new MutationRefusedException(
                        "COMMAND_CREATE_REQUEST_ID_REUSED",
                        "This request ID was already used for different Add Command data.");
            }
            return previous.result().asDuplicate();
        }

        CreateResult result;
        try (Connection connection = connections.open()) {
            result = transaction.execute(
                    connection,
                    AuthenticatedBotJob.of(homeBankingId, botJobId, workspaceEpoch),
                    request);
        }
        completed.put(key, new CompletedRequest(fingerprint, result));
        while (completed.size() > MAX_RESULTS) {
            completed.remove(completed.keySet().iterator().next());
        }
        return result;
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private record CompletedRequest(String fingerprint, CreateResult result) {}
}
