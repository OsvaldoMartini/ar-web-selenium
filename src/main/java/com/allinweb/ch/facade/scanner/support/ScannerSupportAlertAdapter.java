package com.allinweb.ch.facade.scanner.support;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.facade.ScannerSupportCaptureResultService;

public final class ScannerSupportAlertAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    public void showNoActiveBrowser() {
        show(
                ScannerDialogPublisher.Severity.INFO,
                "Support",
                "No active browser session",
                "There is no open browser to capture.");
    }

    public void showCaptureResult(ScannerSupportCaptureResultService.AlertMessage message) {
        show(
                message.ok() ? ScannerDialogPublisher.Severity.INFO : ScannerDialogPublisher.Severity.ERROR,
                "Support",
                message.header(),
                message.content());
    }

    private void show(
            ScannerDialogPublisher.Severity severity,
            String title,
            String header,
            String body) {
        dialogPublisher.alert(severity, title, header, body);
    }
}
