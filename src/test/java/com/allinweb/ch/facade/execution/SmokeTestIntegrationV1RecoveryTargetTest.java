package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.allinweb.ch.model.InstructionLoad;
import com.google.gson.JsonObject;
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

        JsonObject failedTarget = SmokeTestIntegrationV1RecoveryCoordinator.unresolvedTarget(
                target, "CLICK", PAGE_KEY, "TARGET_NOT_FOUND");

        assertEquals("avanti", failedTarget.get("savedCanonicalName").getAsString());
        assertEquals("Continue", failedTarget.get("savedClientName").getAsString());
        assertEquals("//*[@id='avanti']", failedTarget.get("previousXPath").getAsString());
        assertEquals("button#avanti", failedTarget.get("previousCss").getAsString());
        assertEquals("TARGET_NOT_FOUND", failedTarget.get("diagnosticCode").getAsString());
        assertFalse(failedTarget.has("recoveryCandidateId"));
    }
}
