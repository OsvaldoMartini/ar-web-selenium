package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;

final class ScannerPluginAlertAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void warning(String header, String body) {
        show(ScannerDialogPublisher.Severity.WARNING, header, body);
    }

    void error(String header, String body) {
        show(ScannerDialogPublisher.Severity.ERROR, header, body);
    }

    void information(String header, String body) {
        show(ScannerDialogPublisher.Severity.INFO, header, body);
    }

    void show(ScannerDialogPublisher.Severity severity, String header, String body) {
        dialogPublisher.alert(severity, "Plugin Test", header, body);
    }
}
