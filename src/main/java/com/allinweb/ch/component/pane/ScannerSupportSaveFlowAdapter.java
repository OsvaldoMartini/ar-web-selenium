package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerSupportFileChooserService;
import com.allinweb.ch.facade.ScannerSupportFileSaveService;
import com.allinweb.ch.facade.ScannerSupportFileService;
import com.allinweb.ch.facade.ScannerSupportSavedFileMessageService;
import java.io.File;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerSupportSaveFlowAdapter {
    private final ScannerSupportFileSaveService saveService = new ScannerSupportFileSaveService();
    private final ScannerSupportFileChooserService chooserService = new ScannerSupportFileChooserService();
    private final ScannerSupportFileChooserAdapter chooserAdapter = new ScannerSupportFileChooserAdapter(chooserService);
    private final ScannerSupportSavedFileMessageService messageService = new ScannerSupportSavedFileMessageService();
    private final ScannerSupportAlertAdapter alertAdapter;

    ScannerSupportSaveFlowAdapter(ScannerSupportAlertAdapter alertAdapter) {
        this.alertAdapter = alertAdapter;
    }

    void savePageReview(Stage stage, ScannerSupportFileService.SupportFile supportFile) throws java.io.IOException {
        File chosen = chooserAdapter.showSaveDialog(stage, chooserService.pageReview(supportFile));
        if (chosen == null) {
            log.info("DOM capture save cancelled by user");
            return;
        }
        ScannerSupportFileSaveService.SavedSupportFile savedFile =
                saveService.save(supportFile, chosen.toPath());
        log.info("DOM capture saved to {}", chosen.getAbsolutePath());
        alertAdapter.showSavedFile(messageService.pageReview(savedFile));
    }

    void saveElementsReview(Stage stage, ScannerSupportFileService.SupportFile supportFile) throws java.io.IOException {
        File chosen = chooserAdapter.showSaveDialog(stage, chooserService.elementsReview(supportFile));
        if (chosen == null) {
            return;
        }
        ScannerSupportFileSaveService.SavedSupportFile savedFile =
                saveService.save(supportFile, chosen.toPath());
        alertAdapter.showSavedFile(messageService.elementsReview(savedFile));
    }
}
