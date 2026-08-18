package com.allinweb.ch.facade;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed context object passed to scanner plugins as a single JSON argument.
 * Replaces positional arguments[0..8].
 *
 * Usage:
 *   PluginContext ctx = PluginContext.forPageScanner(dataList, searchHiddenFields, port, ...);
 *   executor.executeScript(script, ctx.toJson());
 */
public class PluginContext {

    private static final Gson GSON = new Gson();
    private static final String FIELD_PLUGIN_ID = "pluginId";
    private static final String FIELD_API_VERSION = "apiVersion";
    private static final String FIELD_HIDDEN_FIELDS = "hiddenFields";
    private static final String FIELD_PORT = "port";
    private static final String FIELD_SESSION_ID = "sessionId";
    private static final String FIELD_DESTINATION = "destination";
    private static final String FIELD_OPERATION_ID = "operationId";
    private static final String FIELD_HOME_BANKING_ID = "homeBankingId";
    private static final String FIELD_BOT_JOB_ID = "botJobId";
    private static final String FIELD_TARGET_ORIGIN_URL = "targetOriginURL";
    private static final String FIELD_TRUSTED_ORIGIN_URL = "trustedOriginURL";

    private final Map<String, Object> fields = new HashMap<>();

    private PluginContext(String pluginId) {
        fields.put(FIELD_PLUGIN_ID, pluginId);
        fields.put(FIELD_API_VERSION, 2);
    }

    public static PluginContext forPageScanner(
            List<String> searchTerms,
            boolean hiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        PluginContext ctx = new PluginContext("pageScanner");
        ctx.putScannerFields(
                searchTerms, hiddenFields, port, sessionId, destination, operationId, homeBankingId, botJobId);
        return ctx;
    }

    public static PluginContext forHoverPick(
            boolean hiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            String targetOriginURL,
            String trustedOriginURL) {
        PluginContext ctx = new PluginContext("hoverPick");
        ctx.putScannerFields(
                null, hiddenFields, port, sessionId, destination, operationId, homeBankingId, botJobId);
        ctx.fields.put(FIELD_TARGET_ORIGIN_URL, targetOriginURL);
        ctx.fields.put(FIELD_TRUSTED_ORIGIN_URL, trustedOriginURL);
        return ctx;
    }

    public static PluginContext forSearchList(
            List<String> searchTerms,
            boolean hiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        PluginContext ctx = new PluginContext("searchList");
        ctx.putScannerFields(
                searchTerms, hiddenFields, port, sessionId, destination, operationId, homeBankingId, botJobId);
        return ctx;
    }

    public static PluginContext forSearchListAsync(
            List<String> searchTerms,
            boolean hiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        PluginContext ctx = new PluginContext("searchListAsync");
        ctx.putScannerFields(
                searchTerms, hiddenFields, port, sessionId, destination, operationId, homeBankingId, botJobId);
        return ctx;
    }

    private void putScannerFields(
            List<String> searchTerms,
            boolean hiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        if (searchTerms != null) {
            fields.put(ScannerWorkspacePayloads.searchTermsFieldName(), searchTerms);
        }
        fields.put(FIELD_HIDDEN_FIELDS, hiddenFields);
        fields.put(FIELD_PORT, port);
        fields.put(FIELD_SESSION_ID, sessionId);
        fields.put(FIELD_DESTINATION, destination);
        fields.put(FIELD_OPERATION_ID, operationId);
        fields.put(FIELD_HOME_BANKING_ID, homeBankingId);
        fields.put(FIELD_BOT_JOB_ID, botJobId);
    }

    /**
     * Serialize to a Map that Selenium's executeScript accepts as a single argument.
     * Selenium converts Maps to JavaScript objects automatically.
     */
    public Map<String, Object> toJsContext() {
        return fields;
    }

    public String toJson() {
        return GSON.toJson(fields);
    }
}
