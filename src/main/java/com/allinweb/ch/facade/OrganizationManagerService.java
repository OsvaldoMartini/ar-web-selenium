package com.allinweb.ch.facade;

import com.allinweb.ch.model.DatabaseUserDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrganizationManagerService {

    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();

    protected static volatile OrganizationManagerService instance;

    private OrganizationManagerService() {}

    public static OrganizationManagerService getInstance() {
        if (instance == null) {
            synchronized (OrganizationManagerService.class) {
                if (instance == null) {
                    instance = new OrganizationManagerService();
                }
            }
        }
        return instance;
    }

    public Map<String, Object> list() {
        ErrorMessage error = reload();
        if (error != null) {
            return failure("Failed to load organizations", error);
        }
        return success("Organizations loaded");
    }

    public Map<String, Object> template() {
        Map<String, Object> response = ok("Template loaded");
        response.put("priority", fillUpTemplatePriority());
        response.put("searchConfig", fillUpTemplateScanConfig());
        response.put("optionsConfig", fillUpTemplateWebDriver());
        return response;
    }

    public Map<String, Object> createOrganization(JsonObject body) {
        String name = str(body, "name");
        String url = str(body, "url");
        if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(url)) {
            return failure("Name and URL cannot be empty");
        }

        ErrorMessage error = reload();
        if (error != null) {
            return failure("Failed to load organizations", error);
        }
        if (nameExists(name, 0)) {
            return failure("Environment name already exists");
        }

        DatabaseUserDTO user = new DatabaseUserDTO(
                null,
                name,
                url,
                defaultIfBlank(str(body, "priority"), fillUpTemplatePriority()),
                defaultIfBlank(str(body, "searchConfig"), fillUpTemplateScanConfig()),
                defaultIfBlank(str(body, "optionsConfig"), fillUpTemplateWebDriver()));

        error = performDataBase.createNewHomeBanking(user);
        int newHomeBankId = performDataBase.getNewHomeBankId();
        if (error == null && newHomeBankId > 0) {
            error = performDataBase.createHomeUrlChild(newHomeBankId, user.getUrl());
        }
        if (error != null) {
            return failure("Insert New Organization Failed", error);
        }
        error = reload();
        if (error != null) {
            return failure("Organization created but refresh failed", error);
        }
        return success("Organization created");
    }

    public Map<String, Object> updateOrganization(JsonObject body) {
        int id = intVal(body, "id");
        String name = str(body, "name");
        String url = str(body, "url");
        if (id <= 0) {
            return failure("Select an organization first");
        }
        if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(url)) {
            return failure("Name and URL cannot be empty");
        }

        ErrorMessage error = reload();
        if (error != null) {
            return failure("Failed to load organizations", error);
        }
        if (nameExists(name, id)) {
            return failure("Environment name already exists");
        }

        DatabaseUserDTO user = new DatabaseUserDTO(
                String.valueOf(id),
                name,
                url,
                str(body, "priority"),
                str(body, "searchConfig"),
                str(body, "optionsConfig"));
        error = performDataBase.updateUserData(String.valueOf(id), user);
        if (error != null) {
            return failure("Update Organization Failed", error);
        }
        error = reload();
        if (error != null) {
            return failure("Organization updated but refresh failed", error);
        }
        return success("Organization updated");
    }

    public Map<String, Object> deleteOrganization(JsonObject body) {
        int id = intVal(body, "id");
        if (id <= 0) {
            return failure("Select an organization first");
        }
        ErrorMessage error = reload();
        if (error != null) {
            return failure("Failed to load organizations", error);
        }
        HomeBankingLoadDTO org = findOrg(id);
        if (org == null) {
            return failure("Organization not found");
        }
        if (org.getJobs() != null && org.getJobs() > 0) {
            return failure("Please delete the bot job(s) attached to this organization first");
        }
        error = performDataBase.deleteUserData(String.valueOf(id));
        if (error != null) {
            return failure("Delete Organization Failed", error);
        }
        error = reload();
        if (error != null) {
            return failure("Organization deleted but refresh failed", error);
        }
        return success("Organization deleted");
    }

    public Map<String, Object> listHomeUrls(JsonObject body) {
        int homeBankingId = intVal(body, "homeBankingId");
        ErrorMessage error = performDBEngine.loadHomeUrls(homeBankingId > 0 ? homeBankingId : null);
        if (error != null) {
            return failure("Failed to load environment URLs", error);
        }
        Map<String, Object> response = ok("Environment URLs loaded");
        response.put("homeBankingId", homeBankingId);
        response.put("homeUrls", performLists.getListHomeUrl());
        return response;
    }

    public Map<String, Object> createHomeUrl(JsonObject body) {
        int homeBankingId = intVal(body, "homeBankingId");
        String url = str(body, "url");
        if (homeBankingId <= 0 || Strings.isNullOrEmpty(url)) {
            return failure("You must select an Organization and fill the Environment field");
        }
        ErrorMessage error = performDataBase.createNewHomeUrl(homeBankingId, url);
        if (error != null) {
            return failure("Insert Environment Failed", error);
        }
        error = reload();
        if (error != null) {
            return failure("Environment created but refresh failed", error);
        }
        return success("Environment created");
    }

    public Map<String, Object> updateHomeUrl(JsonObject body) {
        int homeBankingId = intVal(body, "homeBankingId");
        int homeUrlId = firstInt(body, "homeUrlId", "id");
        String url = str(body, "url");
        if (homeBankingId <= 0 || homeUrlId <= 0 || Strings.isNullOrEmpty(url)) {
            return failure("Please select an Organization and an Environment row");
        }
        try {
            ErrorMessage error = performDataBase.updateHomeUrl(homeUrlId, homeBankingId, url);
            if (error != null) {
                return failure("Update Environment Failed", error);
            }
            error = reload();
            if (error != null) {
                return failure("Environment updated but refresh failed", error);
            }
            return success("Environment updated");
        } catch (SQLException e) {
            return failure("Update Environment Failed: " + e.getMessage());
        }
    }

    public Map<String, Object> deleteHomeUrl(JsonObject body) {
        int homeBankingId = intVal(body, "homeBankingId");
        int homeUrlId = firstInt(body, "homeUrlId", "id");
        if (homeBankingId <= 0 || homeUrlId <= 0) {
            return failure("You must select an Environment row to proceed");
        }
        ErrorMessage error = performDBEngine.loadHomeUrls(homeBankingId);
        if (error != null) {
            return failure("Failed to load environment URLs", error);
        }
        List<HomeUrlDTO> urls = performLists.getHomeUrlsByBankId(homeBankingId);
        if (urls.size() <= 1) {
            return failure("This organization must have at least one Environment");
        }
        try {
            error = performDataBase.deleteHomeUrl(homeUrlId);
            if (error != null) {
                return failure("Delete Environment Failed", error);
            }
            error = reload();
            if (error != null) {
                return failure("Environment deleted but refresh failed", error);
            }
            return success("Environment deleted");
        } catch (SQLException e) {
            return failure("Delete Environment Failed: " + e.getMessage());
        }
    }

    private ErrorMessage reload() {
        ErrorMessage error = performDataBase.loadAllDataUsers();
        if (error == null) {
            error = performDBEngine.loadHomeBanking(null);
        }
        if (error == null) {
            error = performDBEngine.loadHomeUrls(null);
        }
        return error;
    }

    private Map<String, Object> success(String message) {
        Map<String, Object> response = ok(message);
        response.put("organizations", performLists.getListHomeBanking());
        response.put("homeUrls", performLists.getListHomeUrl());
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
        return response;
    }

    private Map<String, Object> failure(String message, ErrorMessage error) {
        Map<String, Object> response = failure(message);
        response.put("error", error);
        return response;
    }

    private boolean nameExists(String name, int excludeId) {
        return performLists.getListHomeBanking().stream()
                .anyMatch(row -> row.getName() != null
                        && row.getName().trim().equalsIgnoreCase(name.trim())
                        && (row.getId() == null || row.getId() != excludeId));
    }

    private HomeBankingLoadDTO findOrg(int id) {
        return performLists.getListHomeBanking().stream()
                .filter(row -> row.getId() != null && row.getId() == id)
                .findFirst()
                .orElse(null);
    }

    private String str(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
            return "";
        }
        return body.get(field).getAsString().trim();
    }

    private int firstInt(JsonObject body, String... fields) {
        for (String field : fields) {
            int value = intVal(body, field);
            if (value > 0) {
                return value;
            }
        }
        return 0;
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

    private String defaultIfBlank(String value, String defaultValue) {
        return Strings.isNullOrEmpty(value) ? defaultValue : value;
    }

    private String fillUpTemplatePriority() {
        return "#numero priorita, categoria, identificativo" + System.lineSeparator()
                + "1,xpath,currentXPath" + System.lineSeparator()
                + "2,attributeID,attributeID" + System.lineSeparator()
                + "3,attributeName,attributeName" + System.lineSeparator()
                + "4,searchAttribute,searchAttribute" + System.lineSeparator()
                + "5,coordinates,coordinates" + System.lineSeparator()
                + "6,attribute,test-id" + System.lineSeparator();
    }

    private String fillUpTemplateScanConfig() {
        return "1,ByAttribute,test-id" + System.lineSeparator();
    }

    private String fillUpTemplateWebDriver() {
        return "arg:-disable-web-security" + System.lineSeparator()
                + "arg:-disable-site-isolation-trials" + System.lineSeparator()
                + "arg:-allow-running-insecure-content" + System.lineSeparator()
                + "arg:-disable-features=IsolateOrigins,site-per-process" + System.lineSeparator()
                + "arg:-disable-infobars" + System.lineSeparator()
                + "#arg:-disable-dev-shm-usage" + System.lineSeparator()
                + "#proxy:proxy_address:proxy_port" + System.lineSeparator();
    }
}
