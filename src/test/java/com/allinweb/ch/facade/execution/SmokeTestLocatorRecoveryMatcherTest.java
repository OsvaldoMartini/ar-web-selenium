package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.facade.RuntimeElementHealingService.Status;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmokeTestLocatorRecoveryMatcherTest {
    private static final String PAGE_KEY = "url-v1:" + "a".repeat(64);

    @Test
    void comparesFrozenRegistryWithFreshScannerEvidenceWithoutMutatingHistory() {
        RegistryCandidate saved = new RegistryCandidate(
                41L, "login", "Client Login", "Login OCR", PAGE_KEY,
                "button", "button", "//*[@id='old-login']", "//*[@data-old='login']",
                "button#old-login", "old-login", "login", "", "", "", "",
                Map.of("role", "button", "data-testid", "login-action"));
        Preparation frozen = new Preparation(
                Status.READY, 13, 29, PAGE_KEY, List.of(), List.of(saved), List.of());
        ElementDTO live = liveElement("//*[@id='new-login']", "button#new-login", "Client Login");

        JsonArray matches = SmokeTestLocatorRecoveryMatcher.match(
                frozen, "login", "Client Login", "CLICK", PAGE_KEY, List.of(live));

        assertEquals(1, matches.size());
        JsonObject row = matches.get(0).getAsJsonObject();
        assertEquals("CURRENT", row.get("origin").getAsString());
        assertEquals(41L, row.get("registryCandidateId").getAsLong());
        assertEquals("//*[@id='old-login']", row.get("previousXPath").getAsString());
        assertEquals("//*[@id='new-login']", row.get("newXPath").getAsString());
        assertEquals("Client Login", row.get("ocrMappedName").getAsString());
        assertFalse(row.getAsJsonObject("matches").get("xpath").getAsBoolean());
        assertTrue(row.get("confidence").getAsDouble() >= .70d);
        assertEquals(64, row.get("recoveryCandidateId").getAsString().length());
    }

    @Test
    void returnsNoCandidateWhenNamesAndSemanticsHaveNoUsefulRelationship() {
        RegistryCandidate saved = new RegistryCandidate(
                7L, "submit", "Submit", "Submit", PAGE_KEY,
                "button", "button", "//*[@id='submit']", "", "button#submit",
                "submit", "", "", "", "", "", Map.of("role", "button"));
        Preparation frozen = new Preparation(
                Status.READY, 2, 32, PAGE_KEY, List.of(saved), List.of(), List.of());
        ElementDTO live = liveElement("//*[@id='account-number']", "input#account-number", "Account Number");
        live.setTagName("input");
        live.setTypeElement("text");

        assertEquals(0, SmokeTestLocatorRecoveryMatcher.match(
                frozen, "submit", "Submit", "CLICK", PAGE_KEY, List.of(live)).size());
    }

    private static ElementDTO liveElement(String xpath, String css, String name) {
        ElementDTO value = new ElementDTO();
        value.setXPath(xpath);
        value.setCssSelector(css);
        value.setTagName("button");
        value.setTypeElement("button");
        value.setClientNamed(name);
        value.setSomeText(name);
        value.setAttribId("new-login");
        value.setAttributeData(new AttributeData[] {
                new AttributeData("role", "button"),
                new AttributeData("data-testid", "login-action"),
                new AttributeData("value", "must-not-be-exposed") });
        return value;
    }
}
