package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginPickerDialogPublisherAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void show(List<String[]> plugins, String baseUrl, Path pluginsDir) {
        if (!dialogPublisher.pluginPicker(plugins, baseUrl, pluginsDir.toString())) {
            log.info("Plugin picker dialog event skipped because no React scanner session is connected");
        }
    }
}
