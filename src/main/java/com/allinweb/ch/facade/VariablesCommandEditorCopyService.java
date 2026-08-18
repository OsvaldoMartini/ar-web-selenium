package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.VariablesCommandEditorCopyTransaction.CopyResult;
import com.allinweb.ch.model.VariablesCommandEditorCopyV1;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Connection/idempotency boundary for pure Variables Command Editor COPY NEW. */
public final class VariablesCommandEditorCopyService {
    private static final int MAX_RESULTS = 512;
    private static final VariablesCommandEditorCopyService INSTANCE =
            new VariablesCommandEditorCopyService(
                    () -> PerformDataBase.getInstance().getConnection(),
                    new VariablesCommandEditorCopyTransaction());
    private final ConnectionProvider connections;
    private final VariablesCommandEditorCopyTransaction transaction;
    private final Gson gson = new Gson();
    private final LinkedHashMap<String,CompletedRequest> completed = new LinkedHashMap<>(32,.75f,true);

    VariablesCommandEditorCopyService(ConnectionProvider connections, VariablesCommandEditorCopyTransaction transaction) {
        this.connections=Objects.requireNonNull(connections,"connections");
        this.transaction=Objects.requireNonNull(transaction,"transaction");
    }
    public static VariablesCommandEditorCopyService getInstance() { return INSTANCE; }
    public synchronized CopyResult copy(int homeBankingId,int botJobId,long workspaceEpoch,
                                        VariablesCommandEditorCopyV1.Request request) throws SQLException {
        if (request==null || request.requestId()==null || request.requestId().trim().isEmpty()) {
            throw new MutationRefusedException("COMMAND_COPY_REQUEST_ID_REQUIRED","A command-copy request ID is required.");
        }
        String key=homeBankingId+":"+botJobId+":"+request.requestId().trim();
        String fingerprint=gson.toJson(request);
        CompletedRequest previous=completed.get(key);
        if(previous!=null){
            if(!previous.fingerprint().equals(fingerprint)) throw new MutationRefusedException(
                    "COMMAND_COPY_REQUEST_ID_REUSED","This request ID was already used for different command-copy data.");
            return previous.result().asDuplicate();
        }
        CopyResult result;
        try(Connection connection=connections.open()){
            result=transaction.execute(connection,AuthenticatedBotJob.of(homeBankingId,botJobId,workspaceEpoch),request);
        }
        completed.put(key,new CompletedRequest(fingerprint,result));
        while(completed.size()>MAX_RESULTS) completed.remove(completed.keySet().iterator().next());
        return result;
    }
    @FunctionalInterface interface ConnectionProvider { Connection open() throws SQLException; }
    private record CompletedRequest(String fingerprint,CopyResult result) {}
}
