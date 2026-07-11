package com.allinweb.ch.facade;

import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.JsonObject;

/** Pane-free read boundary for React license and About surfaces. */
public final class LicenseService {
    private static final LicenseService INSTANCE = new LicenseService();
    private final ARPropertyManager properties = ARPropertyManager.getInstance();

    private LicenseService() {}

    public static LicenseService getInstance() {
        return INSTANCE;
    }

    public JsonObject bootstrap() {
        String path = property(ARPropertyEnum.PATH_LICENSE);
        LicenceVal status;
        try {
            status = LicenseManager.checkLicenseFile(path.isBlank() ? System.getProperty("user.dir") : path);
        } catch (Exception exception) {
            JsonObject response = status(null);
            response.addProperty("error", "License verification failed.");
            return response;
        }
        JsonObject response = status(status);
        response.addProperty("path", path);
        response.addProperty("organization", property(ARPropertyEnum.LICENSE_ORG_NAME));
        response.addProperty("owner", property(ARPropertyEnum.LICENSE_OWNER));
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("request", true);
        capabilities.addProperty("activate", true);
        capabilities.addProperty("useExisting", true);
        capabilities.addProperty("onlineRequest", false);
        capabilities.addProperty("chooseDirectory", true);
        capabilities.addProperty("chooseFile", true);
        response.add("capabilities", capabilities);
        return response;
    }

    public JsonObject about() {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("product", "AR Web");
        response.addProperty("version", property(ARPropertyEnum.VERSION));
        response.addProperty("build", property(ARPropertyEnum.BUILD));
        response.addProperty("expiration", property(ARPropertyEnum.EXPIRATION));
        response.addProperty("copyright", "Allinweb AG");
        response.add("license", bootstrap());
        return response;
    }

    static JsonObject status(LicenceVal value) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", value != null);
        response.addProperty("statusCode", value == null ? "ERROR" : value.name());
        response.addProperty("status", value == null ? "License status unavailable" : value.toString());
        response.addProperty("active", value != null && value.isActive());
        response.addProperty("requiresActivation", value == null || !value.isActive());
        return response;
    }

    private String property(ARPropertyEnum key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value;
    }
}
