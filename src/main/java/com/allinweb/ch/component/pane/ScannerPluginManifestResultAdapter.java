package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.UiThreadDispatcher;
import com.allinweb.ch.model.PluginManifestDTO;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginManifestResultAdapter {

    void wire(
            Task<PluginManifestDTO> task,
            String manifestUrl,
            Consumer<PluginManifestDTO> onManifest,
            ScannerPluginDownloadResultAdapter.PluginNotifier notifier) {
        task.setOnSucceeded(evt -> {
            PluginManifestDTO manifest = task.getValue();
            UiThreadDispatcher.getInstance().execute(() -> onManifest.accept(manifest));
        });

        task.setOnFailed(evt -> {
            Throwable cause = task.getException();
            log.error("PluginManifest - fetch failed", cause);
            notifier.error(
                    "Cannot load plugin list",
                    "Failed to fetch manifest.json from:\n" + manifestUrl + "\n\n" + cause.getMessage());
        });
    }
}
