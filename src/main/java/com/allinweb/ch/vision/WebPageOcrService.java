package com.allinweb.ch.vision;

import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrBridgeService;
import com.allinweb.ch.ocr.bridge.OcrResult;

/** Raster OCR facade backed by the native OCR bridge. */
public final class WebPageOcrService {

    private WebPageOcrService() {}

    /** Full-image OCR. Returns word-level boxes in image pixel space. */
    public static OcrResult recognize(RasterImage image) {
        return recognize(image, null);
    }

    /** Full-image OCR honoring config overrides for engine params. */
    public static OcrResult recognize(RasterImage image, OcrConfig cfg) {
        return OcrBridgeService.recognize(image, cfg);
    }

    /** Multi-pass OCR backed by the native OCR engine. */
    public static OcrResult recognizeMultiPass(RasterImage image, OcrConfig cfg) {
        return OcrBridgeService.recognizeMultiPass(image, cfg);
    }
}
