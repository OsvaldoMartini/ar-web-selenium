package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import javafx.concurrent.Task;

final class ScannerPluginDownloadProgressPublisherAdapter {

    private static final String SINGLE_ID = "plugin-download";
    private static final String BATCH_ID = "plugin-download-batch";

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void bindSingle(String pluginName, Task<?> task) {
        String title = "Downloading: " + pluginName;
        dialogPublisher.progress(SINGLE_ID, title, "Downloading " + pluginName + "...", 0, 0, 0);
        task.messageProperty().addListener((obs, oldValue, newValue) ->
                publish(SINGLE_ID, title, newValue, task.getProgress(), 0, 0));
        task.progressProperty().addListener((obs, oldValue, newValue) ->
                publish(SINGLE_ID, title, task.getMessage(), newValue.doubleValue(), 0, 0));
    }

    void bindBatch(int totalPlugins, Task<?> task) {
        String title = "Downloading Plugins";
        dialogPublisher.progress(BATCH_ID, title, "Starting...", 0, 0, totalPlugins);
        task.messageProperty().addListener((obs, oldValue, newValue) ->
                publish(BATCH_ID, title, newValue, task.getProgress(), 0, totalPlugins));
        task.progressProperty().addListener((obs, oldValue, newValue) ->
                publish(BATCH_ID, title, task.getMessage(), newValue.doubleValue(), 0, totalPlugins));
    }

    void updateBatchCounter(int current, int total) {
        dialogPublisher.progress(BATCH_ID, "Downloading Plugins", current + " / " + total, -1, current, total);
    }

    void closeSingle() {
        dialogPublisher.closeProgress(SINGLE_ID);
    }

    void closeBatch() {
        dialogPublisher.closeProgress(BATCH_ID);
    }

    private void publish(String id, String title, String message, double progress, int current, int total) {
        dialogPublisher.progress(id, title, message == null ? "" : message, progress, current, total);
    }
}
