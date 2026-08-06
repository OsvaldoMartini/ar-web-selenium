package com.allinweb.ch.facade;

import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainDashboardService {

    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();

    protected static volatile MainDashboardService instance;

    private MainDashboardService() {}

    public static MainDashboardService getInstance() {
        if (instance == null) {
            synchronized (MainDashboardService.class) {
                if (instance == null) {
                    instance = new MainDashboardService();
                }
            }
        }
        return instance;
    }

    public Map<String, Object> list() {
        ErrorMessage error = reload();
        if (error != null) {
            return failure("Failed to load Bot Jobs", error);
        }
        return success("Bot Jobs loaded");
    }

    public Map<String, Object> deleteBotJob(JsonObject body) {
        int botJobId = intVal(body, "botJobId");
        if (botJobId <= 0) {
            return failure("Select a Bot Job first");
        }

        ErrorMessage error = performDataBase.deleteBotJobData(botJobId);
        if (error != null) {
            return failure("Delete Bot Job Failed", error);
        }
        RuntimeVariableMemoryRegistry.getInstance().removeBotJob(botJobId);
        error = reload();
        if (error != null) {
            return failure("Bot Job deleted but refresh failed", error);
        }
        return success("Bot Job deleted");
    }

    /** Deletes one correlated dashboard selection as a single database transaction. */
    public Map<String, Object> deleteBotJobs(JsonObject body) {
        String requestId = textVal(body, "requestId");
        if (intVal(body, "contractVersion") != 1) {
            return bulkFailure(requestId, "Unsupported Bot Job deletion contract.");
        }
        if (requestId.isBlank() || requestId.length() > 200) {
            return bulkFailure(requestId, "Bot Job deletion requires a valid request ID.");
        }

        final List<Integer> botJobIds;
        try {
            botJobIds = botJobIds(body);
        } catch (IllegalArgumentException invalidRequest) {
            return bulkFailure(requestId, invalidRequest.getMessage());
        }

        ErrorMessage error = performDataBase.deleteBotJobsData(botJobIds);
        if (error != null) {
            Map<String, Object> response = bulkFailure(
                    requestId, "The selected Bot Jobs were not deleted.");
            response.put("error", error);
            return response;
        }
        for (Integer botJobId : botJobIds) {
            RuntimeVariableMemoryRegistry.getInstance().removeBotJob(botJobId);
        }
        error = reload();
        if (error != null) {
            Map<String, Object> response = bulkFailure(
                    requestId, "Bot Jobs were deleted but the dashboard refresh failed.");
            response.remove("botJobs");
            response.put("committed", true);
            response.put("deletedBotJobIds", botJobIds);
            response.put("deletedCount", botJobIds.size());
            response.put("error", error);
            return response;
        }

        Map<String, Object> response = bulkBase(requestId);
        response.put("ok", true);
        response.put("message", botJobIds.size() == 1
                ? "1 Bot Job deleted"
                : botJobIds.size() + " Bot Jobs deleted");
        response.put("committed", true);
        response.put("deletedBotJobIds", botJobIds);
        response.put("deletedCount", botJobIds.size());
        response.put("botJobs", dashboardRows());
        return response;
    }

    public Map<String, Object> openOrganizations() {
        presentation().openOrganizations();
        return successWithCurrentRows("Organizations opened");
    }

    public Map<String, Object> newBotJob() {
        presentation().openNewBotJob();
        return successWithCurrentRows("New Bot Job opened");
    }

    public Map<String, Object> cloneBotJob(JsonObject body) {
        BotJobLoadDTO botJob = findBotJob(intVal(body, "botJobId"));
        if (botJob == null) {
            return failure("Select a Bot Job first");
        }
        presentation().openCloneBotJob(botJob);
        return successWithCurrentRows("Clone Job opened", botJob.getId());
    }

    public Map<String, Object> openBotJob(JsonObject body) {
        BotJobLoadDTO botJob = findBotJob(intVal(body, "botJobId"));
        if (botJob == null) {
            return failure("Select a Bot Job first");
        }
        presentation().openBotJob(botJob);
        return successWithCurrentRows("Bot Job details opened", botJob.getId());
    }

    public Map<String, Object> openConfig() {
        presentation().openConfig();
        return successWithCurrentRows("Configuration opened");
    }

    public Map<String, Object> openTemplate() {
        presentation().openTemplate();
        return successWithCurrentRows("TEMP opened");
    }

    public Map<String, Object> openInfo() {
        presentation().openInfo();
        return successWithCurrentRows("Info opened");
    }

    public Map<String, Object> openLicense() {
        presentation().openLicense();
        return ok("License Manager opened");
    }

    public Map<String, Object> exit() {
        presentation().exitApplication();
        return ok("Exit requested");
    }

    public Map<String, Object> launchBotJob(JsonObject body) {
        BotJobLoadDTO botJob = findBotJob(intVal(body, "botJobId"));
        if (botJob == null) {
            return failure("Select a Bot Job first");
        }
        if (!isLaunchable(botJob)) {
            return failure("Mobile Bot Jobs can only be executed from AR Mobile");
        }
        presentation().launchBotJob(botJob);
        return successWithCurrentRows("Launch requested", botJob.getId());
    }

    private ErrorMessage reload() {
        return performDataBase.loadQuickBotJobs();
    }

    private Map<String, Object> success(String message) {
        Map<String, Object> response = ok(message);
        response.put("botJobs", dashboardRows());
        return response;
    }

    private Map<String, Object> successWithCurrentRows(String message) {
        Map<String, Object> response = ok(message);
        response.put("botJobs", dashboardRows());
        return response;
    }

    private Map<String, Object> successWithCurrentRows(String message, int selectedBotJobId) {
        Map<String, Object> response = successWithCurrentRows(message);
        response.put("selectedBotJobId", selectedBotJobId);
        return response;
    }

    private Map<String, Object> ok(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("message", message);
        return response;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("message", message);
        response.put("botJobs", dashboardRows());
        return response;
    }

    private Map<String, Object> failure(String message, ErrorMessage error) {
        Map<String, Object> response = failure(message);
        response.put("error", error);
        return response;
    }

    private Map<String, Object> bulkBase(String requestId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("contractVersion", 1);
        response.put("requestId", requestId == null ? "" : requestId);
        return response;
    }

    private Map<String, Object> bulkFailure(String requestId, String message) {
        Map<String, Object> response = bulkBase(requestId);
        response.put("ok", false);
        response.put("committed", false);
        response.put("deletedBotJobIds", List.of());
        response.put("deletedCount", 0);
        response.put("message", message);
        response.put("botJobs", dashboardRows());
        return response;
    }

    private List<Integer> botJobIds(JsonObject body) {
        if (body == null || !body.has("botJobIds") || !body.get("botJobIds").isJsonArray()) {
            throw new IllegalArgumentException("Select at least one Bot Job to delete.");
        }
        if (body.getAsJsonArray("botJobIds").size() > 1000) {
            throw new IllegalArgumentException("At most 1000 Bot Jobs may be deleted at once.");
        }
        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>();
        for (JsonElement value : body.getAsJsonArray("botJobIds")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Bot Job IDs must be positive integers.");
            }
            final int botJobId;
            try {
                botJobId = value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException invalidNumber) {
                throw new IllegalArgumentException("Bot Job IDs must be positive integers.");
            }
            if (botJobId <= 0) {
                throw new IllegalArgumentException("Bot Job IDs must be positive integers.");
            }
            uniqueIds.add(botJobId);
        }
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one Bot Job to delete.");
        }
        return List.copyOf(uniqueIds);
    }

    public List<Map<String, Object>> dashboardRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BotJobLoadDTO botJob : performLists.getQuickBotJobs()) {
            rows.add(toRow(botJob));
        }
        return rows;
    }

    private Map<String, Object> toRow(BotJobLoadDTO botJob) {
        Map<String, Object> row = new LinkedHashMap<>();
        HomeBankingLoadDTO org = botJob.getHomeBankingLoadDTO();
        List<BlockLoadDTO> blocks = botJob.getBlockLoadDTOList();
        row.put("id", botJob.getId());
        row.put("name", botJob.getName());
        row.put("description", botJob.getDescription());
        row.put("priority", botJob.getPriority());
        row.put("active", botJob.isActive());
        row.put("homeBankingId", botJob.getHomeBankingId());
        row.put("homeUrlId", botJob.getHomeUrlId());
        row.put("organizationName", org != null ? org.getName() : "");
        row.put("environmentName", "");
        row.put("environmentUrl", org != null ? org.getUrl() : "");
        row.put("blockCount", blocks != null ? blocks.size() : 0);
        row.put("launchable", isLaunchable(botJob));
        return row;
    }

    private BotJobLoadDTO findBotJob(int botJobId) {
        if (botJobId <= 0) {
            return null;
        }
        if (performLists.getQuickBotJobs().isEmpty()) {
            reload();
        }
        return performLists.getQuickBotJobs().stream()
                .filter(row -> row.getId() != null && row.getId() == botJobId)
                .findFirst()
                .orElse(null);
    }

    private boolean isLaunchable(BotJobLoadDTO botJob) {
        String priority = botJob.getPriority();
        return priority != null
                && (priority.equalsIgnoreCase("Web App") || priority.equalsIgnoreCase("Rest Api"));
    }

    private int intVal(JsonObject body, String field) {
        try {
            if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
                return 0;
            }
            return body.get(field).getAsInt();
        } catch (Exception ignore) {
            return 0;
        }
    }

    private String textVal(JsonObject body, String field) {
        try {
            if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
                return "";
            }
            return body.get(field).getAsString().trim();
        } catch (Exception ignore) {
            return "";
        }
    }

    private MainDashboardPresentation presentation() {
        return MainDashboardPresentationRegistry.getInstance().current();
    }
}
