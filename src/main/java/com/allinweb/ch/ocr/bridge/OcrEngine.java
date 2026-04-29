package com.allinweb.ch.ocr.bridge;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;

/**
 * Engine selector for OCR calls. Reads {@link ARPropertyEnum#OCR_ENGINE} from
 * {@code ARWeb.config}; values:
 *
 * <ul>
 *   <li>{@code java}    — route through {@code WebPageOcrService} (Tess4J + OpenCV).
 *   Default until Phase 7 cutover.</li>
 *   <li>{@code native}  — route through {@link OcrBridgeService} (ar_ocr.dll via JNA).</li>
 * </ul>
 *
 * Phase 8 deletes this class and the {@code java} branch entirely.
 */
public final class OcrEngine {

    private OcrEngine() {}

    public static boolean isNative() {
        String value = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.OCR_ENGINE);
        return value != null && "native".equalsIgnoreCase(value.trim());
    }
}
