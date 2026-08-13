package com.allinweb.ch.facade.execution.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.Status;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.BlockSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Environment;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeRunCoordinator.Authority;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeRunCoordinator.RuntimePort;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.DataMode;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.IssuedGrant;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class ExecutionRuntimeRunCoordinatorTest {
    private static final String RUN_ID = "22222222-2222-4222-8222-222222222222";
    private static final String PAGE_KEY = "url-v1:" + "a".repeat(64);
    private static final String PREVIOUS_PAGE_KEY = "url-v1:" + "d".repeat(64);
    private static final String PLAN_REVISION = "b".repeat(64);
    private static final String GRAPH_REVISION = "c".repeat(64);

    @Test
    void ownsQueuedStartAuthoritativeActionAndExactTerminalCleanup() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.snapshots.add(state("QUEUED"));
        runtime.snapshots.add(state("STARTING"));
        runtime.snapshots.add(state("READY"));
        ExecutionRuntimeRunCoordinator coordinator = coordinator(runtime);

        ExecutionRuntimeRunCoordinator.Run run = coordinator.start(facts(), plan());
        JsonObject result = coordinator.action(run, 1L, 1733, null);
        coordinator.close(run);

        assertEquals(RUN_ID, run.runId());
        assertEquals(2, runtime.heartbeatCount);
        assertEquals(1, runtime.actionCount);
        assertEquals(PAGE_KEY, runtime.lastAction.get("pageKey").getAsString());
        assertEquals("CLICK", runtime.lastAction.get("action").getAsString());
        assertFalse(runtime.lastAction.has("inputValue"));
        assertEquals(1, runtime.stopCount);
        assertEquals(1, runtime.releaseCount);
        assertThrows(IllegalStateException.class, () -> coordinator.refresh(run));
    }

    @Test
    void refusesMismatchedPlanBeforeReservationAndCleansFailedReadiness() {
        FakeRuntime mismatchRuntime = new FakeRuntime();
        ExecutionRuntimeRunCoordinator mismatch = coordinator(mismatchRuntime);
        AuthorizedGrantFacts wrong = new AuthorizedGrantFacts(
                2, 2, 29, 7L, GRAPH_REVISION, PLAN_REVISION, DataMode.SYNTHETIC);
        assertThrows(IllegalArgumentException.class, () -> mismatch.start(wrong, plan()));
        assertEquals(0, mismatchRuntime.reserveCount);

        FakeRuntime failedRuntime = new FakeRuntime();
        failedRuntime.snapshots.add(state("FAILED"));
        ExecutionRuntimeRunCoordinator failed = coordinator(failedRuntime);
        assertThrows(IllegalStateException.class, () -> failed.start(facts(), plan()));
        assertEquals(1, failedRuntime.stopCount);
        assertEquals(1, failedRuntime.releaseCount);
    }

    @Test
    void delegatesGetAndSetToTheFrozenParentWithoutChangingTheCommandIdentity() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.snapshots.add(state("READY"));
        ExecutionRuntimeRunCoordinator coordinator = coordinator(runtime);
        ExecutionRuntimeRunCoordinator.Run run = coordinator.start(facts(), planWithCommands());

        coordinator.get(run, 4L, 1734);
        coordinator.set(run, 9L, 1735, "Banca Stato");

        JsonObject get = runtime.actions.get(0);
        JsonObject set = runtime.actions.get(1);
        assertEquals(1734, get.get("instructionId").getAsInt());
        assertEquals(1L, get.get("sequence").getAsLong());
        assertEquals("OUTPUT", get.get("action").getAsString());
        assertEquals("//*[@id='login']", get.getAsJsonArray("authoredSelectors").get(0).getAsString());
        assertEquals(1735, set.get("instructionId").getAsInt());
        assertEquals(2L, set.get("sequence").getAsLong());
        assertEquals("INPUT", set.get("action").getAsString());
        assertEquals("Banca Stato", set.get("inputValue").getAsString());
    }

    @Test
    void unknownPhysicalOutcomePoisonsFurtherActionsButStillAllowsExactCleanup() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.snapshots.add(state("READY"));
        runtime.failActions = true;
        ExecutionRuntimeRunCoordinator coordinator = coordinator(runtime);
        ExecutionRuntimeRunCoordinator.Run run = coordinator.start(facts(), plan());

        assertThrows(RuntimeException.class, () -> coordinator.action(run, 1L, 1733, null));
        assertThrows(IllegalStateException.class, () -> coordinator.action(run, 2L, 1733, null));
        coordinator.close(run);

        assertEquals(1, runtime.actionCount);
        assertEquals(1, runtime.stopCount);
        assertEquals(1, runtime.releaseCount);
    }

    @Test
    void savesAnApprovedCrossPageRecoveryAgainstTheServerHeldPreviousPage() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.snapshots.add(state("READY"));
        runtime.actionResponses.add(recovery("e".repeat(64), PREVIOUS_PAGE_KEY));
        runtime.actionResponses.add(success());
        FakeHealing healing = new FakeHealing();
        ExecutionRuntimeRunCoordinator coordinator = coordinator(runtime, healing);
        ExecutionRuntimeRunCoordinator.Run run = coordinator.start(facts(), plan());

        JsonObject failed = coordinator.action(run, 1L, 1733, null);
        JsonObject recovered = coordinator.recover(run, 1733, "e".repeat(64), true);

        assertFalse(failed.get("ok").getAsBoolean());
        assertTrue(recovered.get("ok").getAsBoolean());
        assertTrue(recovered.get("locatorSaved").getAsBoolean());
        assertEquals(PREVIOUS_PAGE_KEY, healing.savedPageKey);
        assertEquals(991L, healing.savedElementId);
        assertEquals("//*[@data-live='login']", healing.savedXPath);
        assertEquals(2, runtime.actionCount);
    }

    @Test
    void rejectsAnInvalidServerHeldRecoveryPageBeforeAnotherPhysicalAction() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.snapshots.add(state("READY"));
        runtime.actionResponses.add(recovery("f".repeat(64), "not-a-page-key"));
        FakeHealing healing = new FakeHealing();
        ExecutionRuntimeRunCoordinator coordinator = coordinator(runtime, healing);
        ExecutionRuntimeRunCoordinator.Run run = coordinator.start(facts(), plan());
        coordinator.action(run, 1L, 1733, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.recover(run, 1733, "f".repeat(64), true));
        assertEquals(1, runtime.actionCount);
        assertEquals(0, healing.saveCount);
    }

    private static ExecutionRuntimeRunCoordinator coordinator(FakeRuntime runtime) {
        return coordinator(runtime, new FakeHealing());
    }

    private static ExecutionRuntimeRunCoordinator coordinator(
            FakeRuntime runtime, FakeHealing healing) {
        IssuedGrant grant = new IssuedGrant(
                1,
                "v1",
                "11111111-1111-4111-8111-111111111111",
                RUN_ID,
                Instant.parse("2026-08-11T12:00:00Z"),
                Instant.parse("2026-08-11T12:01:30Z"),
                "compact-grant");
        return new ExecutionRuntimeRunCoordinator(
                ignored -> grant,
                runtime,
                healing,
                new ExecutionRuntimeActionFactory(),
                new FakeTime());
    }

    private static AuthorizedGrantFacts facts() {
        return new AuthorizedGrantFacts(
                13, 13, 29, 7L, GRAPH_REVISION, PLAN_REVISION, DataMode.SYNTHETIC);
    }

    private static Plan plan() {
        Owner owner = new Owner(13, 29);
        BlockSnapshot block = new BlockSnapshot(7, 1, "Login", "", null, "", true, 0);
        InstructionSnapshot instruction = new InstructionSnapshot(
                owner, "Lloyds", "", block, 1733, 1, "C", "log_in", "Login", "",
                "//*[@id='login']", "", "", "", "button", "", "", "button#login",
                "", "", false, false, null, null, false, false, true, null, null,
                List.of(), Map.of());
        Environment environment = new Environment(
                13, "Lloyds", 29, "Lloyds", "", 15, "TEST",
                "https://www.lloydsbank.com/", "", "CHROMIUM");
        return new Plan(
                owner, environment, Scope.all(), List.of(block), List.of(instruction), PLAN_REVISION);
    }

    private static Plan planWithCommands() {
        Plan base = plan();
        InstructionSnapshot parent = base.instructions().get(0);
        InstructionSnapshot get = command(parent, 1734, "GET", "GET_WRITE");
        InstructionSnapshot set = command(parent, 1735, "SET", "READ_SET");
        return new Plan(
                base.owner(),
                base.environment(),
                base.scope(),
                base.blocks(),
                List.of(parent, get, set),
                PLAN_REVISION);
    }

    private static InstructionSnapshot command(
            InstructionSnapshot parent, int id, String action, String slot) {
        return new InstructionSnapshot(
                parent.owner(), parent.botJobName(), parent.botJobPriority(), parent.block(),
                id, id - 1732, action, action.toLowerCase(), null, "", "", "", "", "", "",
                "", "", "", "", "", false, false, null, null, false, false, true,
                parent.id(), parent.block().id(), List.of(), Map.of(slot, 91));
    }

    private static JsonObject state(String value) {
        JsonObject result = new JsonObject();
        result.addProperty("state", value);
        return result;
    }

    private static JsonObject success() {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        return result;
    }

    private static JsonObject recovery(String candidateId, String previousPageKey) {
        JsonObject candidate = new JsonObject();
        candidate.addProperty("recoveryCandidateId", candidateId);
        candidate.addProperty("registryCandidateId", 991L);
        candidate.addProperty("newXPath", "//*[@data-live='login']");
        candidate.addProperty("newCss", "[data-live='login']");
        candidate.addProperty("tag", "button");
        candidate.addProperty("previousPageIdentity", previousPageKey);
        JsonObject recovery = new JsonObject();
        recovery.addProperty("state", "AWAITING_USER");
        recovery.add("candidates", new com.google.gson.JsonArray());
        recovery.getAsJsonArray("candidates").add(candidate);
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.add("recovery", recovery);
        return result;
    }

    private static final class FakeHealing implements ExecutionRuntimeRunCoordinator.HealingPort {
        private int saveCount;
        private String savedPageKey;
        private long savedElementId;
        private String savedXPath;

        @Override
        public Preparation prepare(
                Integer homeBankingId,
                Integer botJobId,
                String pageKey,
                com.allinweb.ch.model.InstructionLoad instruction) {
            return new Preparation(
                    Status.READY, homeBankingId, botJobId, pageKey,
                    List.of(), List.of(), List.of());
        }

        @Override
        public boolean save(
                int homeBankingId,
                int botJobId,
                String pageKey,
                long scannedElementId,
                String xpath) {
            saveCount++;
            savedPageKey = pageKey;
            savedElementId = scannedElementId;
            savedXPath = xpath;
            return true;
        }
    }

    private static final class FakeAuthority implements Authority {}

    private static final class FakeRuntime implements RuntimePort {
        private final Authority authority = new FakeAuthority();
        private final Queue<JsonObject> snapshots = new ArrayDeque<>();
        private int reserveCount;
        private int heartbeatCount;
        private int actionCount;
        private int stopCount;
        private int releaseCount;
        private JsonObject lastAction;
        private final List<JsonObject> actions = new ArrayList<>();
        private final Queue<JsonObject> actionResponses = new ArrayDeque<>();
        private boolean failActions;

        @Override
        public Authority reserve(IssuedGrant grant) {
            reserveCount++;
            return authority;
        }

        @Override
        public JsonObject start(
                Authority authority, ExecutionRuntimeHttpClient.StartFacts facts) {
            return snapshots.remove();
        }

        @Override
        public JsonObject heartbeat(Authority authority) {
            heartbeatCount++;
            return snapshots.remove();
        }

        @Override
        public String pageIdentity(Authority authority) {
            return PAGE_KEY;
        }

        @Override
        public JsonObject action(Authority authority, JsonObject request) {
            actionCount++;
            lastAction = request.deepCopy();
            actions.add(lastAction);
            if (failActions) throw new RuntimeException("transport failed");
            return actionResponses.isEmpty() ? success() : actionResponses.remove().deepCopy();
        }

        @Override
        public JsonObject refresh(Authority authority) {
            return state("READY");
        }

        @Override
        public JsonObject stop(Authority authority) {
            stopCount++;
            return state("STOPPED");
        }

        @Override
        public JsonObject release(Authority authority) {
            releaseCount++;
            return new JsonObject();
        }
    }

    private static final class FakeTime implements ExecutionRuntimeRunCoordinator.TimePort {
        private long now;

        @Override
        public long nanoTime() {
            return now;
        }

        @Override
        public void sleep(long milliseconds) {
            now += milliseconds * 1_000_000L;
        }
    }
}
