package com.allinweb.ch.facade;

import com.allinweb.ch.model.OcrConfigDefaults;
import com.allinweb.ch.model.OcrConfigMeta;
import com.allinweb.ch.model.OcrConfigParam;
import com.allinweb.ch.model.OcrConfigProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pane-free OCR profile/configuration API for the React editor. */
public final class OcrManagerService {
    private static final OcrManagerService INSTANCE = new OcrManagerService();
    private static final OcrConfigRepository repository = OcrConfigRepository.getInstance();

    private OcrManagerService() {}

    public static OcrManagerService getInstance() {
        return INSTANCE;
    }

    public Map<String, Object> bootstrap(JsonObject body) {
        Integer homeBankingId = nullablePositive(body, "homeBankingId");
        Integer homeUrlId = nullablePositive(body, "homeUrlId");
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (OcrConfigProfile profile : repository.listProfiles()) profiles.add(profile(profile));
        OcrConfigProfile active = repository.resolveActive(homeBankingId, homeUrlId);
        Map<String, Object> response = ok("OCR configuration loaded");
        response.put("homeBankingId", homeBankingId);
        response.put("homeUrlId", homeUrlId);
        response.put("profiles", profiles);
        response.put("activeProfileId", active == null ? null : active.getId());
        response.put("categories", OcrConfigDefaults.CATEGORIES_IN_ORDER);
        response.put("parameters", active == null ? canonicalParameters() : mergedParameters(active.getId()));
        return response;
    }

    public Map<String, Object> profile(JsonObject body) {
        int profileId = integer(body, "profileId");
        OcrConfigProfile profile = repository.findProfileById(profileId);
        if (profile == null) return failure("OCR profile was not found.");
        Map<String, Object> response = ok("OCR profile loaded");
        response.put("profile", profile(profile));
        response.put("categories", OcrConfigDefaults.CATEGORIES_IN_ORDER);
        response.put("parameters", mergedParameters(profileId));
        return response;
    }

    public Map<String, Object> save(JsonObject body) {
        String name = string(body, "name");
        if (name.isBlank()) return failure("Profile name is required.");
        int requestedId = integer(body, "profileId");
        boolean asNew = bool(body, "asNew") || requestedId <= 0;
        OcrConfigProfile current = requestedId > 0 ? repository.findProfileById(requestedId) : null;
        if (requestedId > 0 && current == null) return failure("OCR profile was not found.");

        OcrConfigProfile clash = repository.findProfileByName(name);
        if (clash != null && (asNew || current == null || !clash.getId().equals(current.getId()))) {
            if (!asNew) return failure("Profile Name cannot be repeated.");
            name = nextVersion(name);
        }

        try {
            int profileId;
            if (asNew) {
                OcrConfigProfile created = new OcrConfigProfile();
                created.setName(name);
                created.setDescription(string(body, "description"));
                created.setHomebankingId(nullablePositive(body, "homeBankingId"));
                created.setHomeUrlId(nullablePositive(body, "homeUrlId"));
                created.setDefault(false);
                profileId = repository.insertProfile(created);
            } else {
                current.setName(name);
                current.setDescription(string(body, "description"));
                current.setHomebankingId(nullablePositive(body, "homeBankingId"));
                current.setHomeUrlId(nullablePositive(body, "homeUrlId"));
                repository.updateProfile(current);
                profileId = current.getId();
            }
            List<OcrConfigParam> params = parameters(body, profileId);
            if (params.isEmpty() && asNew) {
                OcrConfigProfile defaults = repository.findProfileByName("default");
                params = defaults == null ? List.of() : repository.listParamsForProfile(defaults.getId());
            }
            for (OcrConfigParam param : params) {
                repository.upsertParam(new OcrConfigParam(null, profileId, param.getCategory(), param.getName(),
                        param.getValueType(), param.getValue()));
            }
            repository.touchProfile(profileId);
            OcrConfigService.getInstance().invalidateAll();
            Map<String, Object> response = ok("OCR profile saved.");
            response.put("profileId", profileId);
            response.put("name", name);
            return response;
        } catch (SQLException ex) {
            return failure(ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")
                    ? "Profile Name cannot be repeated." : "Save failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> delete(JsonObject body) {
        int profileId = integer(body, "profileId");
        OcrConfigProfile profile = repository.findProfileById(profileId);
        if (profile == null) return failure("OCR profile was not found.");
        if (profile.isDefault()) return failure("Cannot delete the default profile.");
        if (!bool(body, "confirmed")) return failure("Profile deletion requires confirmation.");
        try {
            repository.deleteProfile(profileId);
            OcrConfigService.getInstance().invalidateAll();
            return ok("OCR profile deleted.");
        } catch (SQLException ex) {
            return failure("Delete failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> previewCleanup(JsonObject body) {
        Integer homeBankingId = nullablePositive(body, "homeBankingId");
        Integer homeUrlId = nullablePositive(body, "homeUrlId");
        LocatorCleanupService.ScanReport report = LocatorCleanupService.getInstance().scan(homeBankingId, homeUrlId);
        Map<String, Object> response = ok(report.candidates.isEmpty()
                ? "No orphan locators found." : "Orphan locator cleanup preview ready.");
        response.put("totalRows", report.totalRows);
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (LocatorCleanupService.OrphanCandidate candidate : report.candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", candidate.locator.getId());
            item.put("definedName", candidate.locator.getDefinedName());
            item.put("reason", candidate.reason);
            item.put("detail", candidate.detail);
            candidates.add(item);
        }
        response.put("candidates", candidates);
        return response;
    }

    public Map<String, Object> applyCleanup(JsonObject body) {
        if (!bool(body, "confirmed")) return failure("Locator cleanup requires confirmation.");
        Integer homeBankingId = nullablePositive(body, "homeBankingId");
        Integer homeUrlId = nullablePositive(body, "homeUrlId");
        LocatorCleanupService.ScanReport fresh = LocatorCleanupService.getInstance().scan(homeBankingId, homeUrlId);
        int deleted = LocatorCleanupService.getInstance().deleteCandidates(fresh.candidates);
        Map<String, Object> response = ok("Locator cleanup complete.");
        response.put("deleted", deleted);
        response.put("candidateCount", fresh.candidates.size());
        return response;
    }

    static List<Map<String, Object>> canonicalParameters() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OcrConfigParam param : OcrConfigDefaults.CANONICAL) result.add(parameter(param));
        return result;
    }

    private List<Map<String, Object>> mergedParameters(int profileId) {
        Map<String, OcrConfigParam> values = new LinkedHashMap<>();
        for (OcrConfigParam param : repository.listParamsForProfile(profileId)) {
            values.put(param.getCategory() + "." + param.getName(), param);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (OcrConfigParam canonical : OcrConfigDefaults.CANONICAL) {
            String key = canonical.getCategory() + "." + canonical.getName();
            result.add(parameter(values.getOrDefault(key, canonical)));
            values.remove(key);
        }
        for (OcrConfigParam extra : values.values()) result.add(parameter(extra));
        return result;
    }

    private static Map<String, Object> parameter(OcrConfigParam param) {
        String key = param.getCategory() + "." + param.getName();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", param.getId());
        value.put("category", param.getCategory());
        value.put("name", param.getName());
        value.put("valueType", param.getValueType());
        value.put("value", param.getValue());
        value.put("description", OcrConfigMeta.DESCRIPTIONS.getOrDefault(key, ""));
        value.put("options", OcrConfigMeta.ENUMS.getOrDefault(key, List.of()));
        OcrConfigMeta.Range range = OcrConfigMeta.RANGES.get(key);
        if (range != null) {
            value.put("min", range.min); value.put("max", range.max); value.put("step", range.step);
        }
        return value;
    }

    private Map<String, Object> profile(OcrConfigProfile profile) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", profile.getId()); value.put("name", profile.getName());
        value.put("description", profile.getDescription()); value.put("homeBankingId", profile.getHomebankingId());
        value.put("homeUrlId", profile.getHomeUrlId()); value.put("default", profile.isDefault());
        value.put("createdAt", profile.getCreatedAt() == null ? null : profile.getCreatedAt().toInstant().toString());
        value.put("updatedAt", profile.getUpdatedAt() == null ? null : profile.getUpdatedAt().toInstant().toString());
        return value;
    }

    private Map<String, Object> ok(String message) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("ok", true); result.put("message", message); return result;
    }
    private Map<String, Object> failure(String error) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("ok", false); result.put("error", error); return result;
    }
    private Integer nullablePositive(JsonObject body, String key) { int value = integer(body, key); return value > 0 ? value : null; }
    private int integer(JsonObject body, String key) {
        try { return body != null && body.has(key) ? body.get(key).getAsInt() : -1; }
        catch (Exception ignored) { return -1; }
    }
    private String string(JsonObject body, String key) {
        try { return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString().trim() : ""; }
        catch (Exception ignored) { return ""; }
    }
    private boolean bool(JsonObject body, String key) {
        try { return body != null && body.has(key) && body.get(key).getAsBoolean(); }
        catch (Exception ignored) { return false; }
    }
    private List<OcrConfigParam> parameters(JsonObject body, int profileId) {
        List<OcrConfigParam> result = new ArrayList<>();
        if (body == null || !body.has("parameters") || !body.get("parameters").isJsonArray()) return result;
        JsonArray values = body.getAsJsonArray("parameters");
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String category = string(value, "category");
            String name = string(value, "name");
            if (category.isBlank() || name.isBlank()) continue;
            result.add(new OcrConfigParam(null, profileId, category, name,
                    string(value, "valueType"), string(value, "value")));
        }
        return result;
    }
    private String nextVersion(String requestedName) {
        String base = requestedName.replaceFirst(" v\\d+$", "");
        int max = 0;
        for (OcrConfigProfile profile : repository.listProfiles()) {
            String name = profile.getName();
            if (name == null || !name.matches(java.util.regex.Pattern.quote(base) + " v\\d+")) continue;
            try { max = Math.max(max, Integer.parseInt(name.substring(name.lastIndexOf('v') + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return base + " v" + (max + 1);
    }
}
