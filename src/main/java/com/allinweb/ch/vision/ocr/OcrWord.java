package com.allinweb.ch.vision.ocr;

import java.awt.*;

public class OcrWord {
    private final String text;
    private final Rectangle bounds;
    private final float confidence;

    public OcrWord(String text, Rectangle bounds, float confidence) {
        this.text = text;
        this.bounds = bounds;
        this.confidence = confidence;
    }

    public String getText() {
        return text;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public float getConfidence() {
        return confidence;
    }
}
