package com.allinweb.ch.facade;

/**
 * Untrusted revision reference staged by Page Mappings for transactional Memory List Apply.
 *
 * <p>No element fields or locators cross this contract. Apply reloads the exact registry row and
 * immutable capture before it builds an instruction.
 */
public record PageMappingInstructionReference(
        String captureId,
        String pageKey,
        long scannedElementId,
        String elementHash,
        String expectedLastScannedAt,
        int expectedScanCount) {

    public PageMappingInstructionReference {
        captureId = trimmed(captureId);
        pageKey = trimmed(pageKey);
        elementHash = trimmed(elementHash).toLowerCase(java.util.Locale.ROOT);
        expectedLastScannedAt = trimmed(expectedLastScannedAt);
    }

    public boolean valid() {
        return captureId.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                && !pageKey.isBlank()
                && pageKey.length() <= 128
                && scannedElementId > 0
                && elementHash.matches("[0-9a-f]{64}")
                && !expectedLastScannedAt.isBlank()
                && expectedLastScannedAt.length() <= 40
                && expectedScanCount > 0;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
