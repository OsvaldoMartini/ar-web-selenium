package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginUpdateDialogPublisherAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void show(List<String[]> rows, String pluginsDir, boolean serverConfigured) {
        if (!dialogPublisher.pluginUpdate(pluginsDir, serverConfigured, rows)) {
            log.info("Plugin update dialog event skipped because no React scanner session is connected");
        }
    }
}
