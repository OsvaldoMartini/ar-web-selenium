package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.concurrent.Task;

final class ScannerPluginDownloadFlowAdapter {

    private final ScannerPluginDownloadProgressDialogAdapter singleProgress =
            new ScannerPluginDownloadProgressDialogAdapter();
    private final ScannerPluginBatchDownloadProgressDialogAdapter batchProgress =
            new ScannerPluginBatchDownloadProgressDialogAdapter();
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

        singleProgress.bind(pluginName, task);
        downloadResult.wireSingle(task, pluginsDir, singleProgress, refreshButton, notifier);
        backgroundThread.start(task, "plugin-download-thread");
        singleProgress.show();
    }

    void runBatch(
            List<PluginDTO> plugins,
            String serverBase,
            String pathPlugins,
            Runnable refreshButton,
            ScannerPluginDownloadResultAdapter.PluginNotifier notifier) {
        Path pluginsDir = Paths.get(pathPlugins);
        Task<Integer> task =
                batchDownloadTask.build(plugins, serverBase, pluginsDir, batchProgress::updateCounter);

        batchProgress.bind(plugins.size(), task);
        downloadResult.wireBatch(
                task,
                plugins.size(),
                pathPlugins,
                batchProgress,
                refreshButton,
                notifier,
                count -> {});
        backgroundThread.start(task, "plugin-download-thread");
        batchProgress.show();
    }
}
