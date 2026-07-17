package com.allinweb.ch.facade.scanner.support;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.facade.ScannerSupportFileService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScannerSupportSaveFlowAdapter {
    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    public ScannerSupportSaveFlowAdapter() {}

    public void savePageReview(ScannerSupportFileService.SupportFile supportFile) {
        boolean sent = dialogPublisher.supportFile("pageReview", supportFile);
        if (!sent) {
            log.info(
                    "DOM capture support file was not offered because no React scanner session is available: {}",
                    supportFile.suggestedFileName());
        }
    }

    public void saveElementsReview(ScannerSupportFileService.SupportFile supportFile) {
        boolean sent = dialogPublisher.supportFile("elementsReview", supportFile);
        if (!sent) {
            log.info(
                    "Elements review support file was not offered because no React scanner session is available: {}",
                    supportFile.suggestedFileName());
        }
    }
}
