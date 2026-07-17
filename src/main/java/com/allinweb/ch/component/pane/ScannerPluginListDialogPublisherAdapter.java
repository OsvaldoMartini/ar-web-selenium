package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.model.PluginManifestDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginListDialogPublisherAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void show(PluginManifestDTO manifest, String serverBase, String pathPlugins) {
        if (!dialogPublisher.pluginList(manifest, serverBase, pathPlugins)) {
            log.info("Plugin list dialog event skipped because no React scanner session is connected");
        }
    }
}
