package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeTestIntegrationV1RecoveryTargetTest {
    private static final String PAGE_KEY = "url-v1:" + "a".repeat(64);

    @Test
    void returnsTheUnmatchedAuthoredTargetSeparatelyFromDatabaseCandidates() {
        InstructionLoad target = new InstructionLoad();
        target.setName("avanti");
        target.setClientNamed("Continue");
        target.setXpath("//*[@id='avanti']");
        target.setCssSelector("button#avanti");
        target.setTagName("button");
        target.setReferenceLoadDTOList(List.of(
                reference("AttrData:data-testid", "continue-action"),
                reference("AttrData:automation.test-id.attribute", "qa-hook"),
                reference("AttrData:qa-hook", "client-next"),
                reference("AttrData:unconfigured-hook", "ignored")));

        JsonObject failedTarget = SmokeTestIntegrationV1RecoveryCoordinator.unresolvedTarget(
                target, "CLICK", PAGE_KEY, "TARGET_NOT_FOUND");

        assertEquals("BOT_JOB", failedTarget.get("origin").getAsString());
        assertEquals("avanti", failedTarget.get("savedCanonicalName").getAsString());
        assertEquals("Continue", failedTarget.get("savedClientName").getAsString());
        assertEquals("//*[@id='avanti']", failedTarget.get("previousXPath").getAsString());
        assertEquals("button#avanti", failedTarget.get("previousCss").getAsString());
        assertEquals("TARGET_NOT_FOUND", failedTarget.get("diagnosticCode").getAsString());
        JsonObject attributes = failedTarget.getAsJsonObject("previousStableAttributes");
        assertEquals("continue-action", attributes.get("data-testid").getAsString());
        assertEquals("qa-hook", attributes.get("automation.test-id.attribute").getAsString());
        assertEquals("client-next", attributes.get("qa-hook").getAsString());
        assertFalse(attributes.has("unconfigured-hook"));
        assertFalse(failedTarget.has("recoveryCandidateId"));
    }

    @Test
    void selectedRecoveryEvidenceBecomesV1ReferencesWithoutCoercingNonStrings() throws Exception {
        JsonObject candidate = new JsonObject();
        JsonObject attributes = new JsonObject();
        attributes.addProperty("automation.test-id.attribute", "qa-hook");
        attributes.addProperty("qa-hook", "client-next");
        attributes.addProperty("data-testid", "standard-next");
        attributes.addProperty("numeric-evidence", 7);
        attributes.add("null-evidence", JsonNull.INSTANCE);
        candidate.add("newStableAttributes", attributes);

        List<ReferenceLoadDTO> references = recoveryReferences(candidate);

        assertEquals(List.of(
                        "AttrData:automation.test-id.attribute=qa-hook",
                        "AttrData:qa-hook=client-next",
                        "AttrData:data-testid=standard-next"),
                references.stream()
                        .map(value -> value.getReferenceType() + "=" + value.getValue())
                        .toList());
        assertEquals(List.of(), recoveryReferences(new JsonObject()));
    }

    @SuppressWarnings("unchecked")
    private static List<ReferenceLoadDTO> recoveryReferences(JsonObject candidate) throws Exception {
        Method method = SmokeTestIntegrationV1RecoveryCoordinator.class
                .getDeclaredMethod("recoveryReferences", JsonObject.class);
        method.setAccessible(true);
        return (List<ReferenceLoadDTO>) method.invoke(null, candidate);
    }

    private static ReferenceLoadDTO reference(String type, String value) {
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType(type);
        reference.setValue(value);
        return reference;
    }
}
