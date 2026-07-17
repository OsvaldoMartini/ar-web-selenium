package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.concurrent.Task;

final class ScannerPluginDownloadFlowAdapter {

    private final ScannerPluginDownloadProgressPublisherAdapter progressPublisher =
            new ScannerPluginDownloadProgressPublisherAdapter();
    private final ScannerPluginSingleDownloadTaskAdapter singleDownloadTask =
            new ScannerPluginSingleDownloadTaskAdapter();
    private final ScannerPluginBatchDownloadTaskAdapter batchDownloadTask =
            new ScannerPluginBatchDownloadTaskAdapter();
    private final ScannerPluginDownloadResultAdapter downloadResult =
            new ScannerPluginDownloadResultAdapter();
    private final ScannerPluginBackgroundThreadAdapter backgroundThread =
            new ScannerPluginBackgroundThreadAdapter();

    void runSingle(
            String downloadUrl,
            String fileName,
            String pluginName,
            Path pluginsDir,
            Runnable refreshButton,
            ScannerPluginDownloadResultAdapter.PluginNotifier notifier) {
        Task<String> task = singleDownloadTask.build(downloadUrl, fileName, pluginName, pluginsDir);

        progressPublisher.bindSingle(pluginName, task);
        downloadResult.wireSingle(task, pluginsDir, progressPublisher::closeSingle, refreshButton, notifier);
        backgroundThread.start(task, "plugin-download-thread");
    }

    void runBatch(
            List<PluginDTO> plugins,
            String serverBase,
            String pathPlugins,
            Runnable refreshButton,
            ScannerPluginDownloadResultAdapter.PluginNotifier notifier) {
        Path pluginsDir = Paths.get(pathPlugins);
        Task<Integer> task =
                batchDownloadTask.build(plugins, serverBase, pluginsDir, progressPublisher::updateBatchCounter);

        progressPublisher.bindBatch(plugins.size(), task);
        downloadResult.wireBatch(
                task,
                plugins.size(),
                pathPlugins,
                progressPublisher::closeBatch,
                refreshButton,
                notifier,
                count -> {});
        backgroundThread.start(task, "plugin-download-thread");
    }
}
