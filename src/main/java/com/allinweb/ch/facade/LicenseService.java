package com.allinweb.ch.facade;

import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/** Pane-free read boundary for React license and About surfaces. */
public final class LicenseService {
    private static final Set<String> UNRESTRICTED_OPERATIONS = Set.of(
            "echo", "license.bootstrap", "license.status", "license.startup", "license.request",
            "license.activate", "license.useExisting", "about.bootstrap");
    private static final LicenseService INSTANCE = new LicenseService();
    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private final CompletedRequestCache completedRequests = new CompletedRequestCache(128);

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

    public JsonObject startup() {
        JsonObject license = bootstrap();
        boolean allowed = license.has("active") && license.get("active").getAsBoolean();
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("allowed", allowed);
        response.addProperty("activationRequired", !allowed);
        response.addProperty("targetSessionId", allowed ? "mainDashboard" : "activationRequired");
        response.add("license", license);
        return response;
    }

    public boolean permits(String operation) {
        JsonObject state = bootstrap();
        boolean active = state.has("active") && state.get("active").getAsBoolean();
        return permits(operation, active);
    }

    static boolean permits(String operation, boolean active) {
        return operation != null && (UNRESTRICTED_OPERATIONS.contains(operation) || active);
    }

    public JsonObject request(JsonObject body) {
        return mutation(body, () -> requestOnce(body));
    }

    public JsonObject activate(JsonObject body) {
        return mutation(body, () -> activateOnce(body));
    }

    public JsonObject useExisting(JsonObject body) {
        return mutation(body, () -> useExistingOnce(body));
    }

    private JsonObject requestOnce(JsonObject body) {
        String organization = text(body, "organization").trim();
        String owner = text(body, "owner").trim();
        String email = text(body, "email").trim();
        if (!booleanValue(body, "agreementAccepted")) return failure("Accept the software license agreement.");
        if (!safeLabel(organization)) return failure("Enter a valid organization name.");
        if (!owner.isEmpty() && !safeLabel(owner)) return failure("Enter a valid license owner.");
        if (!email.isEmpty() && !LicenseManager.isEmail(email)) return failure("Enter a valid email address.");
        Path directory = configuredDirectory();
        if (directory == null) return failure("The configured license directory is unavailable or not writable.");
        try {
            LicenseManager.generateRequestFile(directory.toString(), organization, owner, email);
            String label = !owner.isEmpty() ? owner : (!email.isEmpty() ? email : "request");
            Path generated = directory.resolve(organization + "-" + label + ".request").normalize();
            if (!generated.startsWith(directory) || !Files.isRegularFile(generated)) {
                return failure("The license request file could not be generated.");
            }
            properties.setProperty(ARPropertyEnum.LICENSE_ORG_NAME.getValue(), organization);
            properties.setProperty(ARPropertyEnum.LICENSE_OWNER.getValue(), owner);
            JsonObject response = bootstrap();
            response.addProperty("message", "License request file generated.");
            response.addProperty("requestFile", generated.toString());
            return response;
        } catch (Exception exception) {
            return failure("The license request file could not be generated.");
        }
    }

    private JsonObject activateOnce(JsonObject body) {
        Path file = allowedFile(text(body, "responseFile"));
        if (!booleanValue(body, "agreementAccepted")) return failure("Accept the software license agreement.");
        if (file == null || !Files.isRegularFile(file)) return failure("Select a response file in the configured license directory.");
        try {
            if (!LicenseManager.importResponseFile(file.toString())) return failure("The response file could not be imported.");
            JsonObject response = bootstrap();
            if (!response.get("active").getAsBoolean()) return failure("The imported license is not valid for this installation.");
            response.addProperty("message", "License activated.");
            return response;
        } catch (Exception exception) {
            return failure("License activation failed.");
        }
    }

    private JsonObject useExistingOnce(JsonObject body) {
        Path file = allowedFile(text(body, "licenseFile"));
        if (file == null || !"ARWeb.lic".equals(file.getFileName().toString())) {
            return failure("Select ARWeb.lic in the configured license directory.");
        }
        try {
            LicenceVal value = LicenseManager.checkLicenseFile(file.getParent().toString());
            if (!value.isActive()) return failure("The selected license is not valid for this installation.");
            properties.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), file.getParent().toString());
            JsonObject response = bootstrap();
            response.addProperty("message", "Existing license selected.");
            return response;
        } catch (Exception exception) {
            return failure("The selected license could not be verified.");
        }
    }

    private JsonObject mutation(JsonObject body, java.util.function.Supplier<JsonObject> action) {
        String requestId = text(body, "requestId").trim();
        if (requestId.isEmpty()) return failure("License mutation request ID is required.");
        return completedRequests.execute(requestId, action, true);
    }

    private Path configuredDirectory() {
        try {
            String configured = property(ARPropertyEnum.PATH_LICENSE);
            Path directory = Paths.get(configured.isBlank() ? System.getProperty("user.dir") : configured)
                    .toAbsolutePath().normalize();
            return Files.isDirectory(directory) && Files.isWritable(directory) ? directory : null;
        } catch (Exception ignored) { return null; }
    }

    private Path allowedFile(String supplied) {
        try {
            Path directory = configuredDirectory();
            if (directory == null || supplied.isBlank()) return null;
            Path file = Paths.get(supplied).toAbsolutePath().normalize();
            return file.startsWith(directory) ? file : null;
        } catch (Exception ignored) { return null; }
    }

    private boolean safeLabel(String value) {
        return !value.isBlank() && value.length() <= 100 && value.matches("[A-Za-z0-9 ._@-]+");
    }

    private JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message);
        return response;
    }

    private String text(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : "";
    }

    private boolean booleanValue(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() && body.get(key).getAsBoolean();
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
