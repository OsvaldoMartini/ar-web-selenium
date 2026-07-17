package com.allinweb.ch.ocr.bridge;

public class OcrWord {
    private final String text;
    private final OcrBox bounds;
    private final float confidence;

    public OcrWord(String text, OcrBox bounds, float confidence) {
        this.text = text;
        this.bounds = bounds;
        this.confidence = confidence;
    }

    public String getText() {
        return text;
    }

    public OcrBox getBounds() {
        return bounds;
    }

    public float getConfidence() {
        return confidence;
    }
}
