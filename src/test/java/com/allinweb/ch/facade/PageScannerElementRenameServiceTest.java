package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.model.ScannedElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PageScannerElementRenameServiceTest {

    @Test
    void returnsOnlyAnAuthoritativeOneRowAcknowledgement() {
        AtomicReference<String> requestedAlias = new AtomicReference<>();
        PageScannerElementRenameService service = new PageScannerElementRenameService(
                (homeBankingId, botJobId, pageUrl, identity, clientNamed) -> {
                    assertEquals(2, homeBankingId);
                    assertEquals(29, botJobId);
                    assertEquals("https://bank.test/accounts", pageUrl);
                    assertEquals("//input[@id='account']", identity.getXPath());
                    assertEquals("account", identity.getAttribId());
                    requestedAlias.set(clientNamed);
                    ScannedElement saved = saved("Primary account");
                    return new ScannedElementRepository.ClientNamedMutationResult(1, saved);
                });

        JsonObject response = service.rename(
                body("Primary account"), 2, 29, "https://bank.test/accounts");

        assertTrue(response.get("ok").getAsBoolean());
        assertTrue(response.get("persisted").getAsBoolean());
        assertEquals(1, response.get("affectedRows").getAsInt());
        assertEquals("rename-1", response.get("requestId").getAsString());
        assertEquals("7||//input[@id='account']||input", response.get("elementKey").getAsString());
        assertEquals("Primary account", response.get("clientNamed").getAsString());
        assertEquals("Primary account", requestedAlias.get());
    }

    @Test
    void serializesAnAuthoritativeClearedAliasAsJsonNull() {
        PageScannerElementRenameService service = new PageScannerElementRenameService(
                (homeBankingId, botJobId, pageUrl, identity, clientNamed) ->
                        new ScannedElementRepository.ClientNamedMutationResult(1, saved(null)));

        JsonObject body = body(null);
        body.add("clientNamed", JsonNull.INSTANCE);
        JsonObject response = service.rename(body, 2, 29, "https://bank.test/accounts");

        assertTrue(response.get("clientNamed").isJsonNull());
    }

    @Test
    void failsClosedForStaleAmbiguousOrMalformedMutations() {
        PageScannerElementRenameService stale = new PageScannerElementRenameService(
                (homeBankingId, botJobId, pageUrl, identity, clientNamed) ->
                        new ScannedElementRepository.ClientNamedMutationResult(0, null));
        assertThrows(
                IllegalStateException.class,
                () -> stale.rename(body("Alias"), 2, 29, "https://bank.test/accounts"));

        JsonObject oversized = body("x".repeat(257));
        assertThrows(
                IllegalArgumentException.class,
                () -> stale.rename(oversized, 2, 29, "https://bank.test/accounts"));

        JsonObject missingIdentity = body("Alias");
        missingIdentity.remove("identity");
        assertThrows(
                IllegalArgumentException.class,
                () -> stale.rename(missingIdentity, 2, 29, "https://bank.test/accounts"));

        JsonObject unsupported = body("Alias");
        unsupported.addProperty("contractVersion", 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> stale.rename(unsupported, 2, 29, "https://bank.test/accounts"));
    }

    private static JsonObject body(String clientNamed) {
        JsonObject body = new JsonObject();
        body.addProperty("contractVersion", 1);
        body.addProperty("requestId", "rename-1");
        body.addProperty("elementKey", "7||//input[@id='account']||input");
        JsonObject identity = new JsonObject();
        identity.addProperty("xPath", "//input[@id='account']");
        identity.addProperty("iFrameXPath", "");
        identity.addProperty("attribId", "account");
        identity.addProperty("cssSelector", "input#account");
        body.add("identity", identity);
        if (clientNamed == null) body.add("clientNamed", JsonNull.INSTANCE);
        else body.addProperty("clientNamed", clientNamed);
        return body;
    }

    private static ScannedElement saved(String clientNamed) {
        ScannedElement saved = new ScannedElement();
        saved.setId(18L);
        saved.setClientNamed(clientNamed);
        saved.setPageKey("bank.test/accounts");
        saved.setLastScannedAt("2026-08-07 10:00:00");
        return saved;
    }
}
