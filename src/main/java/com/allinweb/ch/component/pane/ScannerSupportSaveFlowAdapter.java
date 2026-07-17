package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.facade.ScannerSupportFileService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerSupportSaveFlowAdapter {
    private final ScannerDialogPublisher dialogPublisher = ScannerDialogPublisher.getInstance();

    ScannerSupportSaveFlowAdapter() {}

    void savePageReview(ScannerSupportFileService.SupportFile supportFile) {
        boolean sent = dialogPublisher.supportFile("pageReview", supportFile);
        if (!sent) {
            log.info(
                    "DOM capture support file was not offered because no React scanner session is available: {}",
                    supportFile.suggestedFileName());
        }
    }

    void saveElementsReview(ScannerSupportFileService.SupportFile supportFile) {
        boolean sent = dialogPublisher.supportFile("elementsReview", supportFile);
        if (!sent) {
            log.info(
                    "Elements review support file was not offered because no React scanner session is available: {}",
                    supportFile.suggestedFileName());
        }
    }
}
