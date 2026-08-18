package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannedElement;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Objects;

/** Owner-scoped persistence contract for one detached Page Scanner display-name change. */
public final class PageScannerElementRenameService {

    private static final int MAX_ELEMENT_KEY_LENGTH = 2_048;
    private static final int MAX_CLIENT_NAMED_LENGTH = 256;
    private static final PageScannerElementRenameService INSTANCE =
            new PageScannerElementRenameService(new DatabasePersistence());

    private final Persistence persistence;

    PageScannerElementRenameService(Persistence persistence) {
        this.persistence = Objects.requireNonNull(persistence, "Page Scanner rename persistence is required");
    }

    public static PageScannerElementRenameService getInstance() {
        return INSTANCE;
    }

    /** Body: {@code {requestId, elementKey, identity:{xPath,iFrameXPath,attribId,cssSelector}, clientNamed}}. */
    public JsonObject rename(
            JsonObject body,
            int homeBankingId,
            int botJobId,
            String pageUrl) {
        if (homeBankingId <= 0 || botJobId <= 0) {
            throw new IllegalArgumentException("An active Page Scanner Bot Job is required");
        }
        if (body == null
                || !body.has("contractVersion")
                || !body.get("contractVersion").isJsonPrimitive()
                || body.get("contractVersion").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported Page Scanner rename contract");
        }
        String requestId = requiredString(body, "requestId", 160, "Page Scanner requestId is required");
        String elementKey = requiredString(
                body, "elementKey", MAX_ELEMENT_KEY_LENGTH, "Select a Page Scanner element first");
        JsonObject identityBody = body != null
                        && body.has("identity")
                        && body.get("identity").isJsonObject()
                ? body.getAsJsonObject("identity")
                : null;
        if (identityBody == null) {
            throw new IllegalArgumentException("Page Scanner element identity is required");
        }
        ElementDTO identity = new ElementDTO();
        identity.setXPath(optionalString(identityBody, "xPath"));
        identity.setIFrameXPath(optionalString(identityBody, "iFrameXPath"));
        identity.setAttribId(optionalString(identityBody, "attribId"));
        identity.setCssSelector(optionalString(identityBody, "cssSelector"));
        if (blank(identity.getXPath())
                && blank(identity.getIFrameXPath())
                && blank(identity.getAttribId())
                && blank(identity.getCssSelector())) {
            throw new IllegalArgumentException("Page Scanner element has no stable locator identity");
        }

        String requestedAlias = nullableString(body, "clientNamed");
        if (requestedAlias != null && requestedAlias.trim().length() > MAX_CLIENT_NAMED_LENGTH) {
            throw new IllegalArgumentException("clientNamed is too long");
        }

        final ScannedElementRepository.ClientNamedMutationResult persisted;
        try {
            persisted = persistence.rename(
                    homeBankingId, botJobId, pageUrl, identity, requestedAlias);
        } catch (Exception failure) {
            throw new IllegalStateException("The Page Scanner name could not be saved", failure);
        }
        if (persisted == null || persisted.affectedRows() != 1 || persisted.element() == null) {
            throw new IllegalStateException("The selected Page Scanner element is stale or unavailable");
        }

        ScannedElement authoritative = persisted.element();
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("requestId", requestId);
        response.addProperty("elementKey", elementKey);
        response.addProperty("persisted", true);
        response.addProperty("affectedRows", persisted.affectedRows());
        response.addProperty("scannedElementId", authoritative.getId());
        if (authoritative.getClientNamed() == null) {
            response.add("clientNamed", JsonNull.INSTANCE);
        } else {
            response.addProperty("clientNamed", authoritative.getClientNamed());
        }
        response.addProperty("pageKey", authoritative.getPageKey());
        response.addProperty("lastScannedAt", authoritative.getLastScannedAt());
        response.addProperty("message", "Page Scanner name saved.");
        return response;
    }

    private static String optionalString(JsonObject object, String field) {
        String value = nullableString(object, field);
        return value == null ? "" : value;
    }

    private static String nullableString(JsonObject object, String field) {
        if (object == null || !object.has(field)) return null;
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsString();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String requiredString(
            JsonObject object, String field, int maximum, String message) {
        String value = optionalString(object, field).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        if (value.length() > maximum) throw new IllegalArgumentException(field + " is too long");
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    interface Persistence {
        ScannedElementRepository.ClientNamedMutationResult rename(
                int homeBankingId,
                int botJobId,
                String pageUrl,
                ElementDTO identity,
                String requestedClientNamed)
                throws Exception;
    }

    private static final class DatabasePersistence implements Persistence {
        @Override
        public ScannedElementRepository.ClientNamedMutationResult rename(
                int homeBankingId,
                int botJobId,
                String pageUrl,
                ElementDTO identity,
                String requestedClientNamed)
                throws Exception {
            return PerformDataBase.getInstance().updateScannedElementClientNamedStrict(
                    homeBankingId, botJobId, pageUrl, identity, requestedClientNamed);
        }
    }
}
