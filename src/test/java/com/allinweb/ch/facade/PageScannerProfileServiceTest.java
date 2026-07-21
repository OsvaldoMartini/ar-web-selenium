package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260721_PageScannerProfile;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PageScannerProfileServiceTest {

    private Connection anchor;
    private PageScannerProfileService service;

    @BeforeEach
    void setUp() throws Exception {
        String databaseUrl = "jdbc:sqlite:file:page-scanner-profile-service-"
                + UUID.randomUUID() + "?mode=memory&cache=shared";
        anchor = DriverManager.getConnection(databaseUrl);
        new M20260721_PageScannerProfile().apply(anchor, "TEXT");
        PageScannerProfileRepository repository =
                new PageScannerProfileRepository(() -> DriverManager.getConnection(databaseUrl));
        service = new PageScannerProfileService(repository);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (anchor != null) {
            anchor.close();
        }
    }

    @Test
    void listReturnsTheAuthoritativeWireShapeAndEchoesRequestId() {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", "profile-list-1");

        Map<String, Object> response = service.list(request);

        assertEquals(true, response.get("ok"));
        assertEquals("profile-list-1", response.get("requestId"));
        List<Map<String, Object>> profiles = profiles(response);
        assertEquals(7, profiles.size());
        assertEquals(
                List.of("id", "key", "label", "searchTerms", "sortOrder", "protected"),
                List.copyOf(profiles.get(0).keySet()));
        assertEquals("factory-default", profiles.get(0).get("key"));
        assertEquals(true, profiles.get(0).get("protected"));
    }

    @Test
    void createsUpdatesAndDeletesAProfileWithAuthoritativeResponses() {
        JsonObject create = saveRequest(
                "profile-save-1", null, "CUSTOM-PROFILE", "Custom profile", "button, attr:QA-hook, [role='tab']");
        Map<String, Object> created = service.save(create);

        assertEquals(true, created.get("ok"));
        assertEquals("custom-profile", created.get("selectedProfileKey"));
        assertEquals(8, profiles(created).size());
        Map<String, Object> saved = profile(created, "custom-profile");
        assertEquals("button, attr:qa-hook, [role='tab']", saved.get("searchTerms"));
        assertEquals(80, saved.get("sortOrder"));
        int id = ((Number) saved.get("id")).intValue();

        JsonObject update = saveRequest(
                "profile-save-2", id, "custom-profile", "Custom profile updated", "input, attr:data-hook");
        update.addProperty("sortOrder", 25);
        Map<String, Object> updated = service.save(update);
        assertEquals(true, updated.get("ok"));
        assertEquals("Custom profile updated", profile(updated, "custom-profile").get("label"));
        assertEquals(25, profile(updated, "custom-profile").get("sortOrder"));

        JsonObject delete = new JsonObject();
        delete.addProperty("requestId", "profile-delete-1");
        delete.addProperty("id", id);
        Map<String, Object> deleted = service.delete(delete);
        assertEquals(true, deleted.get("ok"));
        assertEquals("profile-delete-1", deleted.get("requestId"));
        assertEquals(7, profiles(deleted).size());
        assertFalse(profiles(deleted).stream().anyMatch(profile -> "custom-profile".equals(profile.get("key"))));
    }

    @Test
    void rejectsInvalidAttributesAndCaseInsensitiveDuplicatesWithoutLosingAuthoritativeState() {
        Map<String, Object> invalidAttribute = service.save(saveRequest(
                "invalid-attribute", null, "invalid-attribute", "Invalid attribute", "button, attr:data id"));
        assertEquals(false, invalidAttribute.get("ok"));
        assertTrue(String.valueOf(invalidAttribute.get("message")).contains("Invalid attribute"));
        assertEquals(7, profiles(invalidAttribute).size());

        Map<String, Object> duplicateLabel = service.save(saveRequest(
                "duplicate-label", null, "another-key", "all interactive controls", "button"));
        assertEquals(false, duplicateLabel.get("ok"));
        assertTrue(String.valueOf(duplicateLabel.get("message")).contains("label"));
        assertEquals(7, profiles(duplicateLabel).size());
    }

    @Test
    void refusesToDeleteTheProtectedFactoryDefault() {
        int factoryId = ((Number) profile(service.list(new JsonObject()), "factory-default").get("id")).intValue();
        JsonObject delete = new JsonObject();
        delete.addProperty("requestId", "delete-factory");
        delete.addProperty("id", factoryId);

        Map<String, Object> response = service.delete(delete);

        assertEquals(false, response.get("ok"));
        assertEquals("delete-factory", response.get("requestId"));
        assertTrue(String.valueOf(response.get("message")).contains("protected"));
        assertEquals(7, profiles(response).size());
        assertNotNull(profile(response, "factory-default"));
    }

    @Test
    void refusesToModifyTheProtectedFactoryDefault() {
        Map<String, Object> before = service.list(new JsonObject());
        int factoryId = ((Number) profile(before, "factory-default").get("id")).intValue();
        JsonObject update = saveRequest(
                "update-factory",
                factoryId,
                "factory-default",
                "Changed default",
                "button");

        Map<String, Object> response = service.save(update);

        assertEquals(false, response.get("ok"));
        assertTrue(String.valueOf(response.get("message")).contains("protected"));
        assertEquals("All page scanner controls", profile(response, "factory-default").get("label"));
        assertEquals("", profile(response, "factory-default").get("searchTerms"));
    }

    private static JsonObject saveRequest(
            String requestId, Integer id, String key, String label, String searchTerms) {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", requestId);
        if (id != null) {
            request.addProperty("id", id);
        }
        request.addProperty("key", key);
        request.addProperty("label", label);
        request.addProperty("searchTerms", searchTerms);
        return request;
    }

    private static Map<String, Object> profile(Map<String, Object> response, String key) {
        return profiles(response).stream()
                .filter(profile -> key.equals(profile.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> profiles(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("profiles");
    }
}
