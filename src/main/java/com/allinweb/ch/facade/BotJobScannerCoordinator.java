package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI-independent owner of scanner preparation, modal launch concurrency, and close coordination. */
public final class BotJobScannerCoordinator {

    private final DataPort data;
    private final ScenePort scene;
    private final ErrorPort errors;
    private final MissingPathsPort missingPaths;
    private final AtomicBoolean launchRunning = new AtomicBoolean();

    BotJobScannerCoordinator(DataPort data, ScenePort scene, ErrorPort errors, MissingPathsPort missingPaths) {
        this.data = data;
        this.scene = scene;
        this.errors = errors;
        this.missingPaths = missingPaths;
    }

    public static BotJobScannerCoordinator createDefault(
            ScenePort scene, ErrorPort errors, MissingPathsPort missingPaths) {
        return new BotJobScannerCoordinator(new DefaultDataPort(), scene, errors, missingPaths);
    }

    public boolean open(BotJobLoadDTO botJob, TaskLauncher launcher) {
        requireJob(botJob);
        if (launcher == null) throw new IllegalArgumentException("A scanner task launcher is required");
        if (missingPaths.hasMissingMandatoryPaths() || !launchRunning.compareAndSet(false, true)) return false;
        try {
            launcher.launch("botJob-" + botJob.getId(), () -> runLaunch(botJob));
            return true;
        } catch (RuntimeException rejected) {
            launchRunning.set(false);
            errors.launchFailure(rejected);
            return false;
        }
    }

    public void close() {
        scene.closeWebDrivers();
        scene.closeModal();
    }

    public boolean isBusy() {
        return launchRunning.get();
    }

    private void runLaunch(BotJobLoadDTO botJob) {
        try {
            Preparation prepared = data.prepare(botJob);
            if (prepared.warning() != null) errors.databaseFailure(prepared.warning());
            scene.open(prepared.homeBanking(), botJob, prepared.block());
        } catch (LoadException loadFailure) {
            errors.databaseFailure(loadFailure.error());
        } catch (Exception failure) {
            errors.launchFailure(failure);
        } finally {
            launchRunning.set(false);
        }
    }

    private static void requireJob(BotJobLoadDTO botJob) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("An active Bot Job is required");
        }
    }

    record Preparation(HomeBankingLoadDTO homeBanking, BlockLoadDTO block, ErrorMessage warning) {}

    interface DataPort {
        Preparation prepare(BotJobLoadDTO botJob);
    }

    public interface ScenePort {
        void open(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block);
        void closeWebDrivers();
        void closeModal();
    }

    public interface ErrorPort {
        void databaseFailure(ErrorMessage error);
        void launchFailure(Exception error);
    }

    @FunctionalInterface
    public interface MissingPathsPort {
        boolean hasMissingMandatoryPaths();
    }

    @FunctionalInterface
    public interface TaskLauncher {
        void launch(String threadName, Runnable task);
    }

    private static final class LoadException extends IllegalStateException {
        private final ErrorMessage error;
        private LoadException(ErrorMessage error) {
            super(error == null ? "Unable to prepare scanner data" : error.getErrorMessage());
            this.error = error;
        }
        private ErrorMessage error() { return error; }
    }

    private static final class DefaultDataPort implements DataPort {
        private final PerformDBEngine engine = PerformDBEngine.getInstance();
        private final PerformDataBase database = PerformDataBase.getInstance();
        private final PerformLists lists = PerformLists.getInstance();

        @Override
        public Preparation prepare(BotJobLoadDTO botJob) {
            ErrorMessage error = engine.loadHomeBanking(botJob.getHomeBankingId());
            if (error == null) error = engine.loadHomeUrls(botJob.getHomeBankingId());
            if (error != null) throw new LoadException(error);

            HomeBankingLoadDTO homeBanking = lists.getListHomeBanking().isEmpty()
                    ? null
                    : lists.getListHomeBanking().get(0);
            HomeUrlDTO selectedUrl = matchingUrl(botJob);
            if (selectedUrl != null && homeBanking != null) {
                botJob.setHomeUrlId(selectedUrl.getId());
                homeBanking.setUrl(selectedUrl.getUrl());
            }

            BlockLoadDTO block = first(botJob.getBlockLoadDTOList());
            ErrorMessage warning = null;
            if (block == null) {
                warning = database.loadBlocks(botJob.getId(), botJob.getName(), "block");
                block = first(lists.getListBlock());
            }
            return new Preparation(homeBanking, block, warning);
        }

        private static HomeUrlDTO matchingUrl(BotJobLoadDTO botJob) {
            HomeBankingLoadDTO source = botJob.getHomeBankingLoadDTO();
            if (source == null || source.getHomeUrlDTOs() == null) return null;
            return source.getHomeUrlDTOs().stream()
                    .filter(Objects::nonNull)
                    .filter(url -> Objects.equals(url.getId(), botJob.getHomeUrlId()))
                    .findFirst()
                    .orElse(null);
        }

        private static <T> T first(List<T> values) {
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }
}
