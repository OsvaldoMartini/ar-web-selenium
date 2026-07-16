package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.ScannerSupportFileChooserService;
import java.io.File;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class ScannerSupportFileChooserAdapter {
    private final ScannerSupportFileChooserService chooserService;

    ScannerSupportFileChooserAdapter(ScannerSupportFileChooserService chooserService) {
        this.chooserService = chooserService;
    }

    File showSaveDialog(Stage owner, ScannerSupportFileChooserService.Request request) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(request.title());
        chooser.setInitialFileName(request.initialFileName());
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(
                        chooserService.extensionDescription(), chooserService.extensionPattern()));
        return chooser.showSaveDialog(owner);
    }
}
