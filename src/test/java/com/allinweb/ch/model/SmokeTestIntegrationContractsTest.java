package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.SmokeTestIntegrationContracts.ExcelMode;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.FrozenRuntimeValue;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.PagePolicy;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RunStatus;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeSnapshot;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeMode;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeValueState;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeVoidReason;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.ScopeKind;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmokeTestIntegrationContractsTest {
    private static final String REVISION = "A".repeat(64);

    @Test
    void parsesACompleteStartBodyAndNormalizesAuthoritativeEnumsAndRevision() {
        JsonObject body = validStartBody();

        SmokeTestIntegrationContracts.StartRequest request =
                SmokeTestIntegrationContracts.parseStart(body);

        assertEquals(1, request.contractVersion());
        assertEquals("start-1", request.requestId());
        assertEquals("binding-1", request.bindingEpoch());
        assertEquals(9L, request.workspaceEpoch());
        assertEquals(2, request.homeBankingId());
        assertEquals(32, request.botJobId());
        assertEquals("a".repeat(64), request.graphRevision());
        assertEquals(ScopeKind.BLOCKS, request.scope().kind());
        assertEquals(java.util.List.of(223, 222), request.scope().blockIds());
        assertEquals(ExcelMode.REAL, request.excelMode());
        assertEquals(PagePolicy.PRESERVE_ACTIVE, request.pagePolicy());
        assertEquals(RuntimeMode.JAVA_V1, request.runtimeMode());
        assertTrue(request.durableRuntimeWrites());

        body.addProperty("runtimeMode", "typescript_playwright_v2");
        assertEquals(
                RuntimeMode.TYPESCRIPT_PLAYWRIGHT_V2,
                SmokeTestIntegrationContracts.parseStart(body).runtimeMode());
    }

    @Test
    void parsesStringAndObjectEnvelopeBodiesAndEveryControlRequest() {
        JsonObject startEnvelope = new JsonObject();
        startEnvelope.addProperty("type", SmokeTestIntegrationContracts.START);
        startEnvelope.addProperty("sessionId", "smokeTestManager");
        startEnvelope.addProperty("body", validStartBody().toString());

        SmokeTestIntegrationContracts.StartRequest start =
                SmokeTestIntegrationContracts.parseStart(startEnvelope);
        assertEquals(32, start.botJobId());

        JsonObject stepBody = new JsonObject();
        stepBody.addProperty("contractVersion", 1);
        stepBody.addProperty("requestId", "step-1");
        stepBody.addProperty("runId", "run-1");
        stepBody.addProperty("sequence", 1);
        stepBody.addProperty("instructionId", 1728);
        stepBody.addProperty("excelRowIndex", 0);
        JsonObject stepEnvelope = new JsonObject();
        stepEnvelope.add("body", stepBody);

        SmokeTestIntegrationContracts.StepRequest step =
                SmokeTestIntegrationContracts.parseStep(stepEnvelope);
        assertEquals(1L, step.sequence());
        assertEquals(1728, step.instructionId());
        assertEquals(0, step.excelRowIndex());

        JsonObject stop = new JsonObject();
        stop.addProperty("contractVersion", 1);
        stop.addProperty("requestId", "stop-1");
        stop.addProperty("runId", "run-1");
        assertEquals("run-1", SmokeTestIntegrationContracts.parseStop(stop).runId());

        JsonObject finish = stop.deepCopy();
        finish.addProperty("requestId", "finish-1");
        finish.addProperty("lastSequence", 1);
        assertEquals(1L, SmokeTestIntegrationContracts.parseFinish(finish).lastSequence());
    }

    @Test
    void rejectsMalformedOrAmbiguousVersionOneRequests() {
        JsonObject wrongVersion = validStartBody();
        wrongVersion.addProperty("contractVersion", 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStart(wrongVersion));

        JsonObject stringBoolean = validStartBody();
        stringBoolean.addProperty("durableRuntimeWrites", "true");
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStart(stringBoolean));

        JsonObject duplicateBlocks = validStartBody();
        JsonArray duplicates = new JsonArray();
        duplicates.add(223);
        duplicates.add(223);
        duplicateBlocks.getAsJsonObject("scope").add("blockIds", duplicates);
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStart(duplicateBlocks));

        JsonObject invalidRuntime = validStartBody();
        invalidRuntime.addProperty("runtimeMode", "AUTO_FALLBACK");
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStart(invalidRuntime));

        JsonObject allWithIds = validStartBody();
        allWithIds.getAsJsonObject("scope").addProperty("kind", "ALL");
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStart(allWithIds));

        JsonObject fractionalStep = new JsonObject();
        fractionalStep.addProperty("contractVersion", 1);
        fractionalStep.addProperty("requestId", "step-2");
        fractionalStep.addProperty("runId", "run-1");
        fractionalStep.addProperty("sequence", 1.5);
        fractionalStep.addProperty("instructionId", 1728);
        fractionalStep.addProperty("excelRowIndex", 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStep(fractionalStep));

        JsonObject malformedEnvelope = new JsonObject();
        malformedEnvelope.addProperty("body", "not-json");
        assertThrows(
                IllegalArgumentException.class,
                () -> SmokeTestIntegrationContracts.parseStart(malformedEnvelope));
    }

    @Test
    void correlationNeverPromotesInvalidPayloadFacts() {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "request-9");
        body.addProperty("runId", "run-9");
        SmokeTestIntegrationContracts.Correlation valid =
                SmokeTestIntegrationContracts.correlation(body);
        assertEquals("request-9", valid.requestId());
        assertEquals("run-9", valid.runId());

        JsonObject invalid = new JsonObject();
        invalid.addProperty("body", "[");
        SmokeTestIntegrationContracts.Correlation empty =
                SmokeTestIntegrationContracts.correlation(invalid);
        assertTrue(empty.requestId().isEmpty());
        assertFalse(empty.runId().contains("run"));
    }

    @Test
    void startResponseCarriesAnImmutableBackendFrozenRuntimeSnapshot() {
        Map<Integer, FrozenRuntimeValue> mutableValues = new LinkedHashMap<>();
        mutableValues.put(
                30,
                new FrozenRuntimeValue(
                        RuntimeValueState.VALUE,
                        "",
                        null,
                        7L));
        mutableValues.put(
                31,
                new FrozenRuntimeValue(
                        RuntimeValueState.VOID,
                        null,
                        RuntimeVoidReason.NO_PRODUCER_YET,
                        2L));
        RuntimeSnapshot runtime = new RuntimeSnapshot(19L, true, mutableValues);
        SmokeTestIntegrationContracts.StartResponse response =
                new SmokeTestIntegrationContracts.StartResponse(
                        1,
                        "start-runtime-1",
                        "run-runtime-1",
                        4L,
                        RunStatus.STARTED,
                        "binding-runtime-1",
                        9L,
                        2,
                        32,
                        REVISION,
                        REVISION,
                        "REAL",
                        3L,
                        8L,
                        REVISION,
                        "JAVA_V1",
                        false,
                        runtime,
                        2,
                        4,
                        "INTEGRATION_STARTED",
                        "Integration started.");

        mutableValues.clear();
        assertEquals("JAVA_V1", response.runtimeMode());
        assertEquals(2, response.runtimeSnapshot().values().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> response.runtimeSnapshot().values().put(
                        32,
                        new FrozenRuntimeValue(
                                RuntimeValueState.VOID,
                                null,
                                RuntimeVoidReason.MISSING_BINDING,
                                0L)));

        JsonObject json = new Gson().toJsonTree(response).getAsJsonObject();
        JsonObject snapshot = json.getAsJsonObject("runtimeSnapshot");
        assertEquals(19L, snapshot.get("revision").getAsLong());
        assertTrue(snapshot.get("metadataAvailable").getAsBoolean());
        JsonObject values = snapshot.getAsJsonObject("values");
        JsonObject producedEmpty = values.getAsJsonObject("30");
        assertEquals("VALUE", producedEmpty.get("state").getAsString());
        assertEquals("", producedEmpty.get("value").getAsString());
        assertFalse(producedEmpty.has("voidReason"));
        JsonObject empty = values.getAsJsonObject("31");
        assertEquals("VOID", empty.get("state").getAsString());
        assertFalse(empty.has("value"));
        assertEquals("NO_PRODUCER_YET", empty.get("voidReason").getAsString());
        assertEquals(2L, empty.get("entryRevision").getAsLong());
    }

    @Test
    void frozenRuntimeValueRejectsAmbiguousValueAndVoidStates() {
        assertThrows(
                NullPointerException.class,
                () -> new FrozenRuntimeValue(RuntimeValueState.VALUE, null, null, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrozenRuntimeValue(
                        RuntimeValueState.VOID,
                        "",
                        RuntimeVoidReason.NO_PRODUCER_YET,
                        0L));
        assertThrows(
                NullPointerException.class,
                () -> new FrozenRuntimeValue(RuntimeValueState.VOID, null, null, 0L));
    }

    private JsonObject validStartBody() {
        JsonObject scope = new JsonObject();
        scope.addProperty("kind", "blocks");
        JsonArray blockIds = new JsonArray();
        blockIds.add(223);
        blockIds.add(222);
        scope.add("blockIds", blockIds);

        JsonObject body = new JsonObject();
        body.addProperty("contractVersion", 1);
        body.addProperty("requestId", "start-1");
        body.addProperty("bindingEpoch", "binding-1");
        body.addProperty("workspaceEpoch", 9);
        body.addProperty("homeBankingId", 2);
        body.addProperty("botJobId", 32);
        body.addProperty("graphRevision", REVISION);
        body.add("scope", scope);
        body.addProperty("excelMode", "real");
        body.addProperty("pagePolicy", "preserve_active");
        body.addProperty("durableRuntimeWrites", true);
        return body;
    }
}
