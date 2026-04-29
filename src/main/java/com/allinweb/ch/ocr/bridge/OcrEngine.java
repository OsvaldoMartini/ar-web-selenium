package com.allinweb.ch.ocr.bridge;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;

/**
 * Engine selector for OCR calls. Reads {@link ARPropertyEnum#OCR_ENGINE} from
 * {@code ARWeb.config}; values:
 *
 * <ul>
 *   <li>{@code native}  — route through {@link OcrBridgeService} (ar_ocr.dll via JNA).
 *   This is the default since Phase 7 cutover (when the property is unset or blank).</li>
 *   <li>{@code java}    — route through {@code WebPageOcrService} (Tess4J + OpenCV).
 *   Opt-in only; kept around for emergency fallback until Phase 8 deletes it.</li>
 * </ul>
 *
 * Phase 8 deletes this class and the {@code java} branch entirely.
 */
public final class OcrEngine {

    private OcrEngine() {}

    /**
     * @return {@code true} unless {@link ARPropertyEnum#OCR_ENGINE} is explicitly
     * set to {@code java}. Missing/blank/anything-else = native.
     */
    public static boolean isNative() {
        String value = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.OCR_ENGINE);
        if (value == null || value.isBlank()) return true;
        return !"java".equalsIgnoreCase(value.trim());
    }
}
