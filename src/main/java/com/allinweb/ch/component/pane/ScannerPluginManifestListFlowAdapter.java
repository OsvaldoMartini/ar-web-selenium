package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginManifestDTO;
import java.util.function.Consumer;
import javafx.concurrent.Task;

final class ScannerPluginManifestListFlowAdapter {

    private final ScannerPluginManifestFetchTaskAdapter manifestFetchTask =
            new ScannerPluginManifestFetchTaskAdapter();
    private final ScannerPluginManifestClient manifestClient = new ScannerPluginManifestClient();
    private final ScannerPluginManifestResultAdapter manifestResult =
            new ScannerPluginManifestResultAdapter();
    private final ScannerPluginBackgroundThreadAdapter backgroundThread =
            new ScannerPluginBackgroundThreadAdapter();

    void fetch(
            String urlPlugins,
            Consumer<ManifestListRequest> onManifest,
            ScannerPluginDownloadResultAdapter.PluginNotifier notifier) {
        String manifestUrl = buildManifestUrl(urlPlugins);
        String serverBase = manifestUrl.substring(0, manifestUrl.lastIndexOf("/plugins/manifest.json"));

        Task<PluginManifestDTO> fetchTask = manifestFetchTask.build(() -> manifestClient.fetch(manifestUrl));
        manifestResult.wire(
                fetchTask,
                manifestUrl,
                manifest -> onManifest.accept(new ManifestListRequest(manifest, serverBase)),
                notifier);
        backgroundThread.start(fetchTask, "plugin-manifest-fetch");
    }

    private static String buildManifestUrl(String urlPlugins) {
        String base = urlPlugins.endsWith("/") ? urlPlugins.substring(0, urlPlugins.length() - 1) : urlPlugins;

        int pluginsIdx = base.lastIndexOf("/plugins/");
        if (pluginsIdx > 0) {
            base = base.substring(0, pluginsIdx);
        }

        return base + "/plugins/manifest.json";
    }

    record ManifestListRequest(PluginManifestDTO manifest, String serverBase) {}
}
