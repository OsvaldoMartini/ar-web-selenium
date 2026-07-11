package com.allinweb.ch.facade;

import com.allinweb.ch.model.OcrConfigDefaults;
import com.allinweb.ch.model.OcrConfigMeta;
import com.allinweb.ch.model.OcrConfigParam;
import com.allinweb.ch.model.OcrConfigProfile;
import com.google.gson.JsonObject;
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
}
