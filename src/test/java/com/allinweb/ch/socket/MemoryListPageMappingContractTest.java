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

    @Test
    void forwardedPageMappingCommandsCarryTheCurrentSourceBindingEpoch() {
        JsonObject firstVisit = new JsonObject();
        firstVisit.addProperty("bindingEpoch", "owner-a-first");
        JsonObject firstForward = new JsonObject();
        MemoryListWorkspaceService.addSourceCorrelation(
                firstForward, "PAGE_MAPPINGS", firstVisit, 41);
        assertEquals("owner-a-first", firstForward.get("sourceBindingEpoch").getAsString());
        assertEquals(41, firstForward.get("workspaceEpoch").getAsLong());

        JsonObject secondVisit = new JsonObject();
        secondVisit.addProperty("bindingEpoch", "owner-a-second");
        JsonObject secondForward = new JsonObject();
        MemoryListWorkspaceService.addSourceCorrelation(
                secondForward, "PAGE_MAPPINGS", secondVisit, 42);
        assertEquals("owner-a-second", secondForward.get("sourceBindingEpoch").getAsString());
        assertEquals(42, secondForward.get("workspaceEpoch").getAsLong());

        JsonObject unrelatedForward = new JsonObject();
        MemoryListWorkspaceService.addSourceCorrelation(
                unrelatedForward, "BOT_JOB", secondVisit, 43);
        assertTrue(!unrelatedForward.has("sourceBindingEpoch"));
        assertEquals(43, unrelatedForward.get("workspaceEpoch").getAsLong());
    }

    @Test
    void everyStaticSourceCommandCarriesTheCanonicalWorkspaceEpoch() {
        for (String sourceKind : Set.of("BOT_JOB", "COMPONENT", "PAGE_SCANNER")) {
            JsonObject forwarded = new JsonObject();

            MemoryListWorkspaceService.addSourceCorrelation(
                    forwarded, sourceKind, new JsonObject(), 91);

            assertEquals(91, forwarded.get("workspaceEpoch").getAsLong());
            assertTrue(!forwarded.has("sourceBindingEpoch"));
        }
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
