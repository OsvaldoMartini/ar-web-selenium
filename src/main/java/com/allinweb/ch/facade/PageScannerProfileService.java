package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerSearchProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Validates and persists the focus profiles used by the detached Page Scanner. */
@Slf4j
public final class PageScannerProfileService {

    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_LABEL_LENGTH = 128;
    private static final int MAX_SEARCH_TERMS_LENGTH = 8_192;
    private static final int MAX_SORT_ORDER = 1_000_000;
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("[A-Za-z_:][A-Za-z0-9_.:-]{0,127}");
    private static final PageScannerProfileService INSTANCE =
            new PageScannerProfileService(PageScannerProfileRepository.getInstance());

    private final PageScannerProfileRepository repository;

    public static PageScannerProfileService getInstance() {
        return INSTANCE;
    }

    public PageScannerProfileService(PageScannerProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Map<String, Object> list(JsonObject body) {
        try {
            return response(body, true, "Page Scanner profiles loaded.", repository.list());
        } catch (SQLException error) {
            log.warn("Unable to list Page Scanner profiles: {}", error.getMessage());
            return response(body, false, "Unable to load Page Scanner profiles.", List.of());
        }
    }

    public Map<String, Object> save(JsonObject body) {
        try {
            JsonObject request = body == null ? new JsonObject() : body;
            Integer id = optionalPositiveInt(request, "id");
            List<ScannerSearchProfile> current = repository.list();
            ScannerSearchProfile existing = id == null
                    ? null
                    : repository.findById(id)
                            .orElseThrow(() -> new ValidationException("Page Scanner profile was not found."));

            String key = normalizeKey(requiredString(request, "key", "Profile key is required."));
            String label = normalizeLabel(requiredString(request, "label", "Profile label is required."));
            String searchTerms = normalizeSearchTerms(string(request, "searchTerms"));
            int sortOrder = request.has("sortOrder")
                    ? boundedSortOrder(request.get("sortOrder"))
                    : nextSortOrder(current);

            if (existing != null && existing.protectedProfile()) {
                throw new ValidationException("The protected Page Scanner profile cannot be modified.");
            }
            rejectDuplicates(current, id, key, label);

            if (existing == null) {
                id = repository.insert(new ScannerSearchProfile(0, key, label, searchTerms, sortOrder, false));
            } else {
                ScannerSearchProfile updated = new ScannerSearchProfile(
                        existing.id(), key, label, searchTerms, sortOrder, existing.protectedProfile());
                if (!repository.update(updated)) {
                    throw new ValidationException("Page Scanner profile was not found.");
                }
            }

            Map<String, Object> response = response(
                    request, true, existing == null ? "Page Scanner profile created." : "Page Scanner profile saved.", repository.list());
            response.put("selectedProfileKey", key);
            return response;
        } catch (ValidationException error) {
            return failure(body, error.getMessage());
        } catch (SQLException error) {
            log.warn("Unable to save Page Scanner profile: {}", error.getMessage());
            return failure(body, duplicateMessage(error));
        }
    }

    public Map<String, Object> delete(JsonObject body) {
        try {
            JsonObject request = body == null ? new JsonObject() : body;
            Optional<ScannerSearchProfile> candidate;
            Integer id = optionalPositiveInt(request, "id");
            if (id != null) {
                candidate = repository.findById(id);
            } else {
                String key = normalizeKey(requiredString(request, "key", "Profile id or key is required."));
                candidate = repository.findByKey(key);
            }

            ScannerSearchProfile profile = candidate
                    .orElseThrow(() -> new ValidationException("Page Scanner profile was not found."));
            if (profile.protectedProfile()) {
                throw new ValidationException("The protected Page Scanner profile cannot be deleted.");
            }
            if (!repository.delete(profile.id())) {
                throw new ValidationException("Page Scanner profile was not found.");
            }
            return response(request, true, "Page Scanner profile deleted.", repository.list());
        } catch (ValidationException error) {
            return failure(body, error.getMessage());
        } catch (SQLException error) {
            log.warn("Unable to delete Page Scanner profile: {}", error.getMessage());
            return failure(body, "Unable to delete the Page Scanner profile.");
        }
    }

    private Map<String, Object> failure(JsonObject body, String message) {
        List<ScannerSearchProfile> authoritative;
        try {
            authoritative = repository.list();
        } catch (SQLException listFailure) {
            log.warn("Unable to reload Page Scanner profiles after failure: {}", listFailure.getMessage());
            authoritative = List.of();
        }
        return response(body, false, message, authoritative);
    }

    private static Map<String, Object> response(
            JsonObject body, boolean ok, String message, List<ScannerSearchProfile> profiles) {
        Map<String, Object> response = new LinkedHashMap<>();
        String requestId = string(body, "requestId");
        if (!requestId.isBlank()) {
            response.put("requestId", requestId);
        }
        response.put("ok", ok);
        response.put("message", message);
        List<Map<String, Object>> values = new ArrayList<>();
        for (ScannerSearchProfile profile : profiles) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", profile.id());
            value.put("key", profile.key());
            value.put("label", profile.label());
            value.put("searchTerms", profile.searchTerms());
            value.put("sortOrder", profile.sortOrder());
            value.put("protected", profile.protectedProfile());
            values.add(value);
        }
        response.put("profiles", values);
        return response;
    }

    private static void rejectDuplicates(
            List<ScannerSearchProfile> profiles, Integer currentId, String key, String label) {
        for (ScannerSearchProfile profile : profiles) {
            if (currentId != null && profile.id() == currentId) {
                continue;
            }
            if (profile.key().equalsIgnoreCase(key)) {
                throw new ValidationException("Profile key is already in use.");
            }
            if (profile.label().equalsIgnoreCase(label)) {
                throw new ValidationException("Profile label is already in use.");
            }
        }
    }

    private static String normalizeKey(String value) {
        String key = value.trim().toLowerCase(Locale.ROOT);
        if (key.length() > MAX_KEY_LENGTH || !KEY_PATTERN.matcher(key).matches()) {
            throw new ValidationException(
                    "Profile key must start with a letter and contain only lowercase letters, numbers, or hyphens.");
        }
        return key;
    }

    private static String normalizeLabel(String value) {
        String label = value.trim();
        int length = label.codePointCount(0, label.length());
        if (length == 0 || length > MAX_LABEL_LENGTH || label.codePoints().anyMatch(Character::isISOControl)) {
            throw new ValidationException("Profile label must contain 1 to 128 displayable characters.");
        }
        return label;
    }

    private static String normalizeSearchTerms(String value) {
        String terms = value == null ? "" : value.trim();
        if (terms.length() > MAX_SEARCH_TERMS_LENGTH) {
            throw new ValidationException("Search terms cannot exceed 8192 characters.");
        }
        if (terms.isEmpty()) {
            return "";
        }

        List<String> normalized = new ArrayList<>();
        for (String part : terms.split(",", -1)) {
            String term = part.trim();
            if (term.isEmpty()) {
                continue;
            }
            if (term.regionMatches(true, 0, "attr:", 0, 5)) {
                String attribute = term.substring(5).trim();
                if (!ATTRIBUTE_PATTERN.matcher(attribute).matches()) {
                    throw new ValidationException("Invalid attribute search term: " + term);
                }
                term = "attr:" + attribute.toLowerCase(Locale.ROOT);
            }
            normalized.add(term);
        }
        return String.join(", ", normalized);
    }

    private static int nextSortOrder(List<ScannerSearchProfile> profiles) {
        int maximum = 0;
        for (ScannerSearchProfile profile : profiles) {
            maximum = Math.max(maximum, profile.sortOrder());
        }
        return Math.min(MAX_SORT_ORDER, maximum + 10);
    }

    private static int boundedSortOrder(JsonElement value) {
        try {
            int sortOrder = value.getAsInt();
            if (sortOrder < 0 || sortOrder > MAX_SORT_ORDER) {
                throw new ValidationException("Profile sort order must be between 0 and 1000000.");
            }
            return sortOrder;
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException invalid) {
            throw new ValidationException("Profile sort order must be a whole number.");
        }
    }

    private static Integer optionalPositiveInt(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        try {
            int value = body.get(field).getAsInt();
            if (value <= 0) {
                throw new ValidationException("Profile id must be positive.");
            }
            return value;
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException invalid) {
            throw new ValidationException("Profile id must be a positive whole number.");
        }
    }

    private static String requiredString(JsonObject body, String field, String message) {
        String value = string(body, field);
        if (value.isBlank()) {
            throw new ValidationException(message);
        }
        return value;
    }

    private static String string(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
            return "";
        }
        try {
            return body.get(field).getAsString();
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    private static String duplicateMessage(SQLException error) {
        String message = Objects.toString(error.getMessage(), "").toLowerCase(Locale.ROOT);
        if (message.contains("unique") || message.contains("duplicate") || message.contains("constraint")) {
            return "Profile key and label must be unique.";
        }
        return "Unable to save the Page Scanner profile.";
    }

    private static final class ValidationException extends RuntimeException {
        private ValidationException(String message) {
            super(message);
        }
    }
}
