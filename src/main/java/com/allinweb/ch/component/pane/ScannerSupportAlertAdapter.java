package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.facade.ScannerSupportCaptureResultService;
import com.allinweb.ch.facade.ScannerSupportSavedFileMessageService;

final class ScannerSupportAlertAdapter {

    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    void showNoActiveBrowser() {
        show(
                ScannerDialogPublisher.Severity.INFO,
                "Support",
                "No active browser session",
                "There is no open browser to capture.");
    }

    void showCaptureResult(ScannerSupportCaptureResultService.AlertMessage message) {
        show(
                message.ok() ? ScannerDialogPublisher.Severity.INFO : ScannerDialogPublisher.Severity.ERROR,
                "Support",
                message.header(),
                message.content());
    }

    void showSavedFile(ScannerSupportSavedFileMessageService.Message message) {
        show(
                ScannerDialogPublisher.Severity.INFO,
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
