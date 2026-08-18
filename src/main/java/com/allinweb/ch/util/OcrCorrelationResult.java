package com.allinweb.ch.util;

import java.util.List;

/** JSON-serialisable result of correlating a single ElementDTO with OCR words. */
public class OcrCorrelationResult {

    public String xpath;
    public String iframeXPath;
    public String tagName;
    public String domText; // DTO.someText at the time of scan
    public String matchQuality; // EXACT_CONTAIN | OVERLAP | PROXIMITY | NONE
    public String ocrText; // joined primary-tier words
    public String ocrNearestText; // joined PROXIMITY words (may differ from ocrText)
    public double ocrConfidenceAvg; // average confidence of primary-tier words (0..100)
    public Rect domRect; // element bbox in CSS px (for debugging)
    public List<WordBox> ocrWordBoxes; // primary-tier words, coords in CSS px

    public static class Rect {
        public double x, y, width, height;
    }

    public static class WordBox {
        public String text;
        public double x, y, width, height;
        public float confidence;
    }
}
