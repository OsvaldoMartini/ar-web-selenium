package com.allinweb.ch.facade.scanner.plugin;

import com.allinweb.ch.facade.ScannerDialogPublisher;

public final class ScannerPluginAlertAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    public void warning(String header, String body) {
        show(ScannerDialogPublisher.Severity.WARNING, header, body);
    }

    public void error(String header, String body) {
        show(ScannerDialogPublisher.Severity.ERROR, header, body);
    }

    public void information(String header, String body) {
        show(ScannerDialogPublisher.Severity.INFO, header, body);
    }

    public void show(ScannerDialogPublisher.Severity severity, String header, String body) {
        dialogPublisher.alert(severity, "Plugin Test", header, body);
    }
}
