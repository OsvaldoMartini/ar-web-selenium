package com.allinweb.ch.facade;

/** Coordinates scanner modal lifecycle behind a UI adapter boundary. */
public final class ScannerModalStageService {

    public ModalStage show(ModalStage currentStage, StageFactory stageFactory, Config config) {
        ModalStage stage = currentStage;
        if (stage == null) {
            stage = stageFactory.create(config);
            if (stage == null) {
                return null;
            }
        }

        stage.setTitle(config.title());
        if (!stage.isShowing()) {
            stage.show();
        }
        return stage;
    }

    public ModalStage close(ModalStage currentStage) {
        if (currentStage != null) {
            currentStage.close();
        }
        return null;
    }

    public record Config(String title, double width, double height) {}

    public interface StageFactory {
        ModalStage create(Config config);
    }

    public interface ModalStage {
        void setTitle(String title);

        boolean isShowing();

        void show();

        void close();
    }
}
