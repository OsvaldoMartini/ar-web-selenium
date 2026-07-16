package com.allinweb.ch.facade;

public final class ScannerSupportFileChooserService {
    private static final String SUPPORT_FILE_DESCRIPTION = "Support Files (*.support)";
    private static final String SUPPORT_FILE_PATTERN = "*.support";

    public Request pageReview(ScannerSupportFileService.SupportFile supportFile) {
        return new Request("Save Support File", supportFile.suggestedFileName());
    }

    public Request elementsReview(ScannerSupportFileService.SupportFile supportFile) {
        return new Request("Save Elements Review", supportFile.suggestedFileName());
    }

    public String extensionDescription() {
        return SUPPORT_FILE_DESCRIPTION;
    }

    public String extensionPattern() {
        return SUPPORT_FILE_PATTERN;
    }

    public record Request(String title, String initialFileName) {}
}
