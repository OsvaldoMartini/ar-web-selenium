package com.allinweb.ch.component.pane;

import java.nio.file.Path;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginDownloadResultAdapter {

    void wireSingle(
            Task<String> task,
            Path pluginsDir,
            Runnable closeProgress,
            Runnable refreshButton,
            PluginNotifier notifier) {
        task.setOnSucceeded(e -> {
            closeProgress.run();
            Platform.runLater(refreshButton);
            notifier.information("Download complete", task.getValue() + "\nDestination: " + pluginsDir);
        });

        task.setOnFailed(e -> {
            closeProgress.run();
            Throwable ex = task.getException();
            log.error("UpdatePlugins - failed", ex);
            notifier.error("Download failed", ex.getMessage());
        });
    }

    void wireBatch(
            Task<Integer> task,
            int pluginCount,
            String pathPlugins,
            Runnable closeProgress,
            Runnable refreshButton,
            PluginNotifier notifier,
            IntConsumer onFinished) {
        task.setOnSucceeded(evt -> {
            closeProgress.run();
            int count = task.getValue();
            Platform.runLater(refreshButton);
            notifier.information(
                    "Download complete",
                    count + " of " + pluginCount + " plugin(s) downloaded and extracted to:\n" + pathPlugins);
            log.info("PluginDownload - finished: {}/{} plugins", count, pluginCount);
            onFinished.accept(count);
        });

        task.setOnFailed(evt -> {
            closeProgress.run();
            Throwable ex = task.getException();
            log.error("PluginDownload - failed", ex);
            notifier.error("Download failed", ex.getMessage());
        });
    }

    interface PluginNotifier {
        void information(String header, String body);

        void error(String header, String body);
    }
}
