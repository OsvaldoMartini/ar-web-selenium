package com.allinweb.ch.facade;

import com.allinweb.ch.component.pane.ARNewBotJobManagerPane;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NewBotJobService {

    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final MainDashboardService mainDashboardService = MainDashboardService.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final Gson gson = new Gson();

    protected static volatile NewBotJobService instance;

    private NewBotJobService() {}

    public static NewBotJobService getInstance() {
        if (instance == null) {
            synchronized (NewBotJobService.class) {
                if (instance == null) {
                    instance = new NewBotJobService();
                }
            }
        }
        return instance;
    }

    public Map<String, Object> bootstrap() {
        ErrorMessage error = reloadEnvironments();
        if (error != null) {
            return failure("Failed to load environments", error);
        }
        return environmentResponse("New Bot Job data loaded");
    }

    public Map<String, Object> environments() {
        ErrorMessage error = reloadEnvironments();
        if (error != null) {
            return failure("Failed to refresh environments", error);
        }
        return environmentResponse("Environments refreshed");
    }

    public Map<String, Object> create(JsonObject body) {
        String rawName = str(body, "name");
        String description = str(body, "description");
        String priority = normalizePriority(str(body, "priority"));
        int homeBankingId = intVal(body, "homeBankingId");
        int homeUrlId = intVal(body, "homeUrlId");
        boolean openAfterCreate = boolVal(body, "openAfterCreate", true);

        if (Strings.isNullOrEmpty(rawName)) {
            return failure("Bot Job name cannot be empty");
        }
        ErrorMessage error = reloadEnvironments();
        if (error != null) {
            return failure("Failed to load environments", error);
        }

        String safeName = sanitizeName(rawName);
        if (nameExists(safeName)) {
            return failure("Bot Job name already exists");
        }

        HomeUrlDTO homeUrl = performLists.getHomeUrlByBankId(homeBankingId, homeUrlId);
        if (homeUrl == null || homeUrl.getId() == null || homeUrl.getId() <= 0) {
            return failure("Select a valid Organization Environment");
        }

        HomeBankingLoadDTO homeBanking = performLists.getHomeBankingById(homeBankingId);
        BotJobLoadDTO createdBotJob = new BotJobLoadDTO();
        createdBotJob.setName(safeName);
        createdBotJob.setDescription(description);
        createdBotJob.setPriority(priority);
        createdBotJob.setHomeBankingId(homeBankingId);
        createdBotJob.setHomeUrlId(homeUrlId);
        createdBotJob.setHomeBankingLoadDTO(homeBanking);

        error = performDataBase.createNewBotJob(createdBotJob);
        int newBotJobId = performDataBase.getNewBotJobId();
        if (error != null || newBotJobId <= 0) {
            return failure("Create New Bot Job Failed", error);
        }

        createdBotJob.setId(newBotJobId);
        performDataBase.loadQuickBotJobs();
        BotJobLoadDTO fresh = findQuickBotJob(newBotJobId);
        if (fresh != null) {
            createdBotJob = fresh;
        }

        if (openAfterCreate) {
            ARNewBotJobManagerPane.getInstance().openBotJobAndClose(createdBotJob);
        }

        Map<String, Object> dashboard = mainDashboardService.list();
        webSocketSessionManager.sendMessageJson(-1, "mainDashboard", gson.toJson(dashboard), "mainDashboard.listResponse");

        Map<String, Object> response = ok("Bot Job created");
        response.put("botJob", toCreatedBotJob(createdBotJob));
        response.put("botJobs", dashboard.get("botJobs"));
        return response;
    }

    public Map<String, Object> openOrganizations() {
        ARNewBotJobManagerPane.getInstance().openOrganizations();
        Map<String, Object> response = environmentResponse("Organizations opened");
        return response;
    }

    public Map<String, Object> cancel() {
        ARNewBotJobManagerPane.getInstance().closeModal();
        return ok("Cancelled");
    }

    private ErrorMessage reloadEnvironments() {
        ErrorMessage error = performDBEngine.loadHomeBanking(null);
        if (error == null) {
            error = performDBEngine.loadHomeUrls(null);
        }
        if (error == null) {
            performDataBase.loadQuickBotJobs();
        }
        return error;
    }

    private Map<String, Object> environmentResponse(String message) {
        Map<String, Object> response = ok(message);
        response.put("appTypes", Arrays.asList("Web App", "Android", "iOS"));
        response.put("environments", environmentRows());
        return response;
    }

    private List<Map<String, Object>> environmentRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HomeUrlDTO env : performLists.getListHomeUrl()) {
            if (env.getId() == null || env.getId() <= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", env.getId());
            row.put("name", defaultEnvironmentName(env.getName()));
            row.put("url", env.getUrl());
            row.put("homeBankingId", env.getHomeBankingId());
            row.put("orgName", env.getOrgName());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toCreatedBotJob(BotJobLoadDTO botJob) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", botJob.getId());
        row.put("name", botJob.getName());
        row.put("description", botJob.getDescription());
        row.put("priority", botJob.getPriority());
        row.put("homeBankingId", botJob.getHomeBankingId());
        row.put("homeUrlId", botJob.getHomeUrlId());
        return row;
    }

    private BotJobLoadDTO findQuickBotJob(int botJobId) {
        return performLists.getQuickBotJobs().stream()
                .filter(row -> row.getId() != null && row.getId() == botJobId)
                .findFirst()
                .orElse(null);
    }

    private boolean nameExists(String name) {
        return performLists.getQuickBotJobs().stream()
                .anyMatch(row -> row.getName() != null && row.getName().equalsIgnoreCase(name));
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
        response.put("environments", environmentRows());
        return response;
    }

    private Map<String, Object> failure(String message, ErrorMessage error) {
        Map<String, Object> response = failure(message);
        if (error != null) {
            response.put("error", error);
        }
        return response;
    }

    private String normalizePriority(String priority) {
        if ("Android".equalsIgnoreCase(priority)) {
            return "Android";
        }
        if ("iOS".equalsIgnoreCase(priority)) {
            return "iOS";
        }
        if ("Rest Api".equalsIgnoreCase(priority)) {
            return "Rest Api";
        }
        return "Web App";
    }

    private String sanitizeName(String rawName) {
        String safeName = rawName.replaceAll("[\\\\/:*?\"<>|]", "");
        safeName = safeName.replaceAll("[\\p{Cntrl}]", "").trim();
        if (safeName.isEmpty()) {
            safeName = "default_name";
        }
        if (safeName.length() > 100) {
            safeName = safeName.substring(0, 100);
        }
        return safeName;
    }

    private String defaultEnvironmentName(String value) {
        return Strings.isNullOrEmpty(value) ? "TEST" : value;
    }

    private String str(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
            return "";
        }
        return body.get(field).getAsString().trim();
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

    private boolean boolVal(JsonObject body, String field, boolean defaultValue) {
        try {
            if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
                return defaultValue;
            }
            return body.get(field).getAsBoolean();
        } catch (Exception ignore) {
            return defaultValue;
        }
    }
}
