package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginManifestDTO;
import java.util.concurrent.Callable;
import javafx.concurrent.Task;

final class ScannerPluginManifestFetchTaskAdapter {

    Task<PluginManifestDTO> build(Callable<PluginManifestDTO> fetchManifest) {
        return new Task<>() {
            @Override
            protected PluginManifestDTO call() throws Exception {
                updateMessage("Connecting to plugin server...");
                return fetchManifest.call();
            }
        };
    }
}
