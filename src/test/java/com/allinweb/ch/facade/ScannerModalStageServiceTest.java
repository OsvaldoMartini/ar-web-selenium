package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerModalStageServiceTest {

    @Test
    void createsAndShowsStageWhenMissing() {
        ScannerModalStageService service = new ScannerModalStageService();
        RecordingStageFactory factory = new RecordingStageFactory(false);
        ScannerModalStageService.Config config = new ScannerModalStageService.Config("Scanner", 1100, 650);

        ScannerModalStageService.ModalStage result = service.show(null, factory, config);

        assertSame(factory.stage, result);
        assertSame(config, factory.config);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("title:Scanner", "show"), factory.stage.calls);
    }

    @Test
    void reusesVisibleStageWithoutShowingAgain() {
        ScannerModalStageService service = new ScannerModalStageService();
        RecordingStage stage = new RecordingStage(true);

        ScannerModalStageService.ModalStage result = service.show(
                stage,
                config -> {
                    throw new AssertionError("Factory should not be called");
                },
                new ScannerModalStageService.Config("Scanner", 1100, 650));

        assertSame(stage, result);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("title:Scanner"), stage.calls);
    }

    @Test
    void returnsNullWhenFactoryCannotCreateStage() {
        ScannerModalStageService service = new ScannerModalStageService();

        ScannerModalStageService.ModalStage result = service.show(
                null,
                config -> null,
                new ScannerModalStageService.Config("Scanner", 1100, 650));

        assertNull(result);
    }

    @Test
    void closesAndClearsStage() {
        ScannerModalStageService service = new ScannerModalStageService();
        RecordingStage stage = new RecordingStage(true);

        ScannerModalStageService.ModalStage result = service.close(stage);

        assertNull(result);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("close"), stage.calls);
    }

    private static final class RecordingStageFactory implements ScannerModalStageService.StageFactory {
        private final RecordingStage stage;
        private ScannerModalStageService.Config config;

        private RecordingStageFactory(boolean showing) {
            this.stage = new RecordingStage(showing);
        }

        @Override
        public ScannerModalStageService.ModalStage create(ScannerModalStageService.Config config) {
            this.config = config;
            return stage;
        }
    }

    private static final class RecordingStage implements ScannerModalStageService.ModalStage {
        private final List<String> calls = new ArrayList<>();
        private boolean showing;

        private RecordingStage(boolean showing) {
            this.showing = showing;
        }

        @Override
        public void setTitle(String title) {
            calls.add("title:" + title);
        }

        @Override
        public boolean isShowing() {
            return showing;
        }

        @Override
        public void show() {
            calls.add("show");
            showing = true;
        }

        @Override
        public void close() {
            calls.add("close");
            showing = false;
        }
    }
}
