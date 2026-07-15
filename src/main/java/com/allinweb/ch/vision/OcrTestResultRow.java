package com.allinweb.ch.vision;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * One row in the "Test On Current Page" review grid (Phase 4c+).
 * Combines the persisted ElementDTO text with the test-run OCR result + a per-row Approved toggle.
 */
public class OcrTestResultRow {

    private final BooleanProperty approved = new SimpleBooleanProperty(false);
    private final String definedName; // ElementTextResolver output — slugged unique identifier
    private final String matchQuality; // EXACT_CONTAIN | OVERLAP | PROXIMITY | NONE
    private final String tag;
    private final String domText; // ElementDTO.someText at test time (already resolved by scanner payload creation)
    private final String ocrText; // OcrCorrelationResult.ocrText (joined primary-tier words)
    private final String xPath;
    private final String xPathShort;

    public OcrTestResultRow(
            String definedName, String matchQuality, String tag, String domText, String ocrText, String xPath) {
        this.definedName = definedName == null ? "" : definedName;
        this.matchQuality = matchQuality == null ? "NONE" : matchQuality;
        this.tag = tag == null ? "" : tag;
        this.domText = domText == null ? "" : domText;
        this.ocrText = ocrText == null ? "" : ocrText;
        this.xPath = xPath == null ? "" : xPath;
        this.xPathShort = shorten(this.xPath);
    }

    public String getDefinedName() {
        return definedName;
    }

    public boolean isApproved() {
        return approved.get();
    }

    public void setApproved(boolean v) {
        approved.set(v);
    }

    public BooleanProperty approvedProperty() {
        return approved;
    }

    public String getMatchQuality() {
        return matchQuality;
    }

    public String getTag() {
        return tag;
    }

    public String getDomText() {
        return domText;
    }

    public String getOcrText() {
        return ocrText;
    }

    public String getxPath() {
        return xPath;
    }

    public String getxPathShort() {
        return xPathShort;
    }

    /** Truncate xpath from the front so the tail (the actual element segment) stays visible. */
    private static String shorten(String x) {
        if (x == null) return "";
        if (x.length() <= 60) return x;
        return "…" + x.substring(x.length() - 60);
    }
}
