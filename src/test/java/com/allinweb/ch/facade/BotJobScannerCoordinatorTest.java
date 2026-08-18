package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BotJobScannerCoordinatorTest {

    @Test
    void ownsLaunchLeaseUntilPreparedScannerTaskCompletes() {
        FakeScene scene = new FakeScene();
        FakeErrors errors = new FakeErrors();
        BlockLoadDTO block = new BlockLoadDTO();
        HomeBankingLoadDTO bank = new HomeBankingLoadDTO();
        BotJobScannerCoordinator coordinator = new BotJobScannerCoordinator(
                job -> new BotJobScannerCoordinator.Preparation(bank, block, null), scene, errors, () -> false);
        AtomicReference<Runnable> task = new AtomicReference<>();

        assertTrue(coordinator.open(job(), (name, runnable) -> task.set(runnable)));
        assertTrue(coordinator.isBusy());
        assertFalse(coordinator.open(job(), (name, runnable) -> {}));
        task.get().run();

        assertFalse(coordinator.isBusy());
        assertEquals(bank, scene.bank);
        assertEquals(block, scene.block);
        assertEquals(42, scene.job.getId());
    }

    @Test
    void missingPathsRejectBeforeScheduling() {
        List<String> launches = new ArrayList<>();
        BotJobScannerCoordinator coordinator = new BotJobScannerCoordinator(
                job -> { throw new AssertionError("data must not load"); },
                new FakeScene(), new FakeErrors(), () -> true);

        assertFalse(coordinator.open(job(), (name, task) -> launches.add(name)));
        assertTrue(launches.isEmpty());
        assertFalse(coordinator.isBusy());
    }

    @Test
    void preparationWarningIsReportedButScannerStillOpens() {
        FakeScene scene = new FakeScene();
        FakeErrors errors = new FakeErrors();
        ErrorMessage warning = new ErrorMessage("Database", "Blocks", "Block load failed");
        BotJobScannerCoordinator coordinator = new BotJobScannerCoordinator(
                job -> new BotJobScannerCoordinator.Preparation(new HomeBankingLoadDTO(), null, warning),
                scene, errors, () -> false);

        coordinator.open(job(), (name, task) -> task.run());

        assertEquals(List.of(warning), errors.databaseErrors);
        assertEquals(1, scene.openCalls);
        assertFalse(coordinator.isBusy());
    }

    @Test
    void rejectedSchedulingAndSceneFailureReleaseLeaseAndReportFailure() {
        FakeScene scene = new FakeScene();
        FakeErrors errors = new FakeErrors();
        BotJobScannerCoordinator coordinator = new BotJobScannerCoordinator(
                job -> new BotJobScannerCoordinator.Preparation(null, null, null), scene, errors, () -> false);

        assertFalse(coordinator.open(job(), (name, task) -> { throw new IllegalStateException("rejected"); }));
        assertFalse(coordinator.isBusy());
        scene.failure = new IllegalStateException("modal failed");
        assertTrue(coordinator.open(job(), (name, task) -> task.run()));
        assertFalse(coordinator.isBusy());
        assertEquals(List.of("rejected", "modal failed"), errors.launchErrors);

        coordinator.close();
        assertEquals(1, scene.closeDriverCalls);
        assertEquals(1, scene.closeModalCalls);
    }

    private static BotJobLoadDTO job() {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setHomeBankingId(7);
        return job;
    }

    private static final class FakeScene implements BotJobScannerCoordinator.ScenePort {
        private int openCalls;
        private int closeDriverCalls;
        private int closeModalCalls;
        private HomeBankingLoadDTO bank;
        private BotJobLoadDTO job;
        private BlockLoadDTO block;
        private RuntimeException failure;
        public void open(HomeBankingLoadDTO bank, BotJobLoadDTO job, BlockLoadDTO block) {
            openCalls++;
            if (failure != null) throw failure;
            this.bank = bank;
            this.job = job;
            this.block = block;
        }
        public void closeWebDrivers() { closeDriverCalls++; }
        public void closeModal() { closeModalCalls++; }
    }

    private static final class FakeErrors implements BotJobScannerCoordinator.ErrorPort {
        private final List<ErrorMessage> databaseErrors = new ArrayList<>();
        private final List<String> launchErrors = new ArrayList<>();
        public void databaseFailure(ErrorMessage error) { databaseErrors.add(error); }
        public void launchFailure(Exception error) { launchErrors.add(error.getMessage()); }
    }
}
