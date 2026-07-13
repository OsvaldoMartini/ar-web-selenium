package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.ExcelUtils;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** UI-independent Clone Job workflow used by the React manager. */
public final class CloneJobService {

    private static final CloneJobService INSTANCE = new CloneJobService();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final PerformLists lists = PerformLists.getInstance();

    private CloneJobService() {}

    public static CloneJobService getInstance() { return INSTANCE; }

    public synchronized Map<String, Object> bootstrap(JsonObject body) {
        BotJobLoadDTO source = source(intValue(body, "sourceBotJobId"));
        if (source == null) return failure("Source Bot Job was not found");
        ErrorMessage error = loadEnvironments();
        if (error != null) return failure("Environments could not be loaded", error);
        Map<String, Object> response = ok("Clone Job loaded");
        response.put("sourceBotJob", sourceRow(source));
        response.put("environments", environmentRows(source.getHomeBankingId()));
        return response;
    }

    public synchronized Map<String, Object> environments(JsonObject body) {
        BotJobLoadDTO source = source(intValue(body, "sourceBotJobId"));
        if (source == null) return failure("Source Bot Job was not found");
        ErrorMessage error = engine.loadHomeUrls(null);
        if (error != null) return failure("Environments could not be refreshed", error);
        Map<String, Object> response = ok("Environments refreshed");
        response.put("environments", environmentRows(source.getHomeBankingId()));
        return response;
    }

    public synchronized Map<String, Object> validateName(JsonObject body) {
        String name = text(body, "name");
        String validation = nameError(name);
        return validation == null ? ok("Name is available") : failure(validation);
    }

    public synchronized Map<String, Object> create(JsonObject body) {
        int sourceId = intValue(body, "sourceBotJobId");
        BotJobLoadDTO source = source(sourceId);
        if (source == null) return failure("Source Bot Job was not found");

        String name = text(body, "name");
        String description = text(body, "description");
        String url = text(body, "url");
        String validation = nameError(name);
        if (validation != null) return failure(validation);
        if (url.isBlank()) return failure("Target environment URL is required");
        if (intValue(body, "homeBankingId") != source.getHomeBankingId()) {
            return failure("Target environment must belong to the source organization");
        }

        ErrorMessage error = loadEnvironments();
        if (error != null) return failure("Environments could not be loaded", error);
        HomeUrlDTO environment = findEnvironment(source.getHomeBankingId(), intValue(body, "homeUrlId"), url);
        if (environment == null) {
            error = database.createNewHomeUrl(source.getHomeBankingId(), url);
            if (error != null) return failure("Target environment could not be created", error);
            environment = new HomeUrlDTO();
            environment.setId(database.getNewHomeUrlId());
            environment.setHomeBankingId(source.getHomeBankingId());
            environment.setUrl(url);
        }

        Integer clonedId = null;
        error = database.cloneBotJob(environment, sourceId, name, description);
        if (error == null) clonedId = database.getNewBotBojId(sourceId);
        if (error == null) error = database.cloneBlock(sourceId);
        if (error == null) error = database.cloneInstructions(sourceId);
        if (error == null) error = database.cloneVariables(sourceId);
        if (error == null) error = database.cloneUpdateInstruction(sourceId);
        if (error == null) error = database.cloneReferences(sourceId);
        if (error != null) {
            if (clonedId != null && clonedId > 0) database.deleteBotJobData(clonedId);
            return failure("Clone Job failed and partial database data was removed", error);
        }
        if (clonedId == null || clonedId <= 0) return failure("Clone completed without a new Bot Job id");

        // File work occurs only after every request and database validation has succeeded.
        if (booleanValue(body, "createExcelDataFile", true)) ExcelUtils.createExcelDataFile(source, name);
        error = database.loadQuickBotJobs();
        if (error != null) return failure("Bot Job cloned but dashboard refresh failed", error);

        Map<String, Object> response = ok("Bot Job cloned successfully");
        response.put("sourceBotJobId", sourceId);
        response.put("clonedBotJobId", clonedId);
        response.put("clonedBotJob", source(clonedId) == null ? null : sourceRow(source(clonedId)));
        response.put("botJobs", MainDashboardService.getInstance().dashboardRows());
        return response;
    }

    private ErrorMessage loadEnvironments() {
        ErrorMessage error = lists.getListHomeBanking().isEmpty() ? engine.loadHomeBanking(null) : null;
        return error == null && lists.getListHomeUrl().isEmpty() ? engine.loadHomeUrls(null) : error;
    }

    private BotJobLoadDTO source(int id) {
        if (id <= 0) return null;
        if (lists.getQuickBotJobs().isEmpty()) database.loadQuickBotJobs();
        return lists.getQuickBotJobs().stream()
                .filter(job -> Objects.equals(job.getId(), id)).findFirst().orElse(null);
    }

    private String nameError(String name) {
        if (name.isBlank()) return "New Bot Job name is required";
        if (lists.getQuickBotJobs().isEmpty()) database.loadQuickBotJobs();
        return lists.getQuickBotJobs().stream().anyMatch(job -> name.equalsIgnoreCase(job.getName()))
                ? "Bot Job name already exists" : null;
    }

    private HomeUrlDTO findEnvironment(int organizationId, int environmentId, String url) {
        return lists.getHomeUrlsByBankId(organizationId).stream()
                .filter(item -> (environmentId > 0 && Objects.equals(item.getId(), environmentId))
                        || url.equals(item.getUrl()))
                .findFirst().orElse(null);
    }

    private List<Map<String, Object>> environmentRows(int organizationId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HomeUrlDTO item : lists.getHomeUrlsByBankId(organizationId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId()); row.put("homeBankingId", item.getHomeBankingId());
            row.put("orgName", item.getOrgName()); row.put("name", item.getName()); row.put("url", item.getUrl());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> sourceRow(BotJobLoadDTO source) {
        Map<String, Object> row = new LinkedHashMap<>();
        HomeBankingLoadDTO org = source.getHomeBankingLoadDTO();
        row.put("id", source.getId()); row.put("name", source.getName());
        row.put("description", source.getDescription()); row.put("priority", source.getPriority());
        row.put("homeBankingId", source.getHomeBankingId()); row.put("homeUrlId", source.getHomeUrlId());
        row.put("organizationName", org == null ? "" : org.getName());
        HomeUrlDTO environment = findEnvironment(source.getHomeBankingId(), source.getHomeUrlId(), "");
        row.put("environmentName", environment == null ? "" : environment.getName());
        row.put("environmentUrl", environment == null ? (org == null ? "" : org.getUrl()) : environment.getUrl());
        return row;
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("ok", true); result.put("message", message); return result;
    }
    private static Map<String, Object> failure(String message) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("ok", false); result.put("message", message); return result;
    }
    private static Map<String, Object> failure(String message, ErrorMessage error) {
        Map<String, Object> result = failure(message); result.put("error", error); return result;
    }
    private static String text(JsonObject body, String field) {
        try { return body != null && body.has(field) && !body.get(field).isJsonNull() ? body.get(field).getAsString().trim() : ""; }
        catch (RuntimeException ignored) { return ""; }
    }
    private static int intValue(JsonObject body, String field) {
        try { return body != null && body.has(field) && !body.get(field).isJsonNull() ? body.get(field).getAsInt() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }
    private static boolean booleanValue(JsonObject body, String field, boolean fallback) {
        try { return body != null && body.has(field) && !body.get(field).isJsonNull() ? body.get(field).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
}
