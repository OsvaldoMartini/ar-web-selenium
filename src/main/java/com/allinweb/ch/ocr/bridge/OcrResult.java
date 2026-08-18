package com.allinweb.ch.ocr.bridge;

import java.util.List;

public class OcrResult {
    private final String fullText;
    private final List<OcrWord> words;

    public OcrResult(String fullText, List<OcrWord> words) {
        this.fullText = fullText;
        this.words = words;
    }

    public String getFullText() {
        return fullText;
    }

    public List<OcrWord> getWords() {
        return words;
    }
}
