package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PageMappingInstructionReference;
import com.google.gson.JsonObject;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryListPageMappingContractTest {

    @Test
    void pageMappingStagingRejectsClientElementAndKeepsOnlySixRevisionFields() {
        JsonObject valid = validPayload();
        PageMappingInstructionReference reference =
                MemoryListWorkspaceService.pageMappingReference(valid);
        JsonObject staged = MemoryListWorkspaceService.pageMappingPayload(reference);

        assertTrue(reference.valid());
        assertEquals(
                Set.of(
                        "captureId",
                        "pageKey",
                        "scannedElementId",
                        "elementHash",
                        "expectedLastScannedAt",
                        "expectedScanCount"),
                staged.keySet());

        JsonObject submitted = valid.deepCopy();
        JsonObject spoofedElement = new JsonObject();
        spoofedElement.addProperty("xPath", "//button[@id='attacker']");
        spoofedElement.addProperty("definedName", "attacker-name");
        submitted.add("elementDTO", spoofedElement);
        submitted.addProperty("xPath", "//button[@id='also-attacker']");

        assertNull(MemoryListWorkspaceService.pageMappingReference(submitted));
    }

    @Test
    void invalidReferenceCannotBecomeAValidStagedContract() {
        JsonObject submitted = validPayload();
        submitted.remove("scannedElementId");

        assertNull(MemoryListWorkspaceService.pageMappingReference(submitted));
    }

    private static JsonObject validPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("captureId", "00000000-0000-0000-0000-000000000001");
        payload.addProperty("pageKey", "bank-login-page");
        payload.addProperty("scannedElementId", 41L);
        payload.addProperty("elementHash", "a".repeat(64));
        payload.addProperty("expectedLastScannedAt", "2026-08-07T10:00:00Z");
        payload.addProperty("expectedScanCount", 3);
        return payload;
    }
}
