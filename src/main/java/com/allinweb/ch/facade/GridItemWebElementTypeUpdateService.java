package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.MutationRefusedException;
import com.allinweb.ch.facade.GridItemWebElementTypeUpdateTransaction.UpdateResult;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.Request;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Connection and idempotency boundary for the isolated GridItem Web Element type update. */
public final class GridItemWebElementTypeUpdateService {
    private static final int MAX_RESULTS = 512;
    private static final GridItemWebElementTypeUpdateService INSTANCE =
            new GridItemWebElementTypeUpdateService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new GridItemWebElementTypeUpdateTransaction());

    private final ConnectionProvider connections;
    private final GridItemWebElementTypeUpdateTransaction transaction;
    private final Gson gson = new Gson();
    private final LinkedHashMap<String, CompletedRequest> completed =
            new LinkedHashMap<>(32, .75f, true);

    GridItemWebElementTypeUpdateService(
            ConnectionProvider connections,
            GridItemWebElementTypeUpdateTransaction transaction) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public static GridItemWebElementTypeUpdateService getInstance() {
        return INSTANCE;
    }

    public synchronized UpdateResult update(Request request) throws SQLException {
        Objects.requireNonNull(request, "request");
        String key = request.homeBankingId()
                + ":" + request.botJobId()
                + ":" + request.requestId();
        String fingerprint = gson.toJson(request);
        CompletedRequest previous = completed.get(key);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new MutationRefusedException(
                        "WEB_ELEMENT_TYPE_REQUEST_ID_REUSED",
                        "This request ID was already used for different Web Element type data.");
            }
            return previous.result().asDuplicate();
        }

        UpdateResult result;
        try (Connection connection = connections.open()) {
            result = transaction.execute(
                    connection,
                    AuthenticatedBotJob.of(
                            request.homeBankingId(),
                            request.botJobId(),
                            request.workspaceEpoch()),
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

    private record CompletedRequest(String fingerprint, UpdateResult result) {}
}
