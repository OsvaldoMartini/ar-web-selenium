package com.allinweb.ch.util;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;

/**
 * DOM element attribute helpers. Slimmed during the OCR migration: only
 * {@link #overrideClassAttribute} is still used (by {@code ScannerRuntimeBackend}
 * to map HTML tags to Android widget class strings).
 */
public final class VisionElementMapper {

    private VisionElementMapper() {}

    public static void overrideClassAttribute(ElementDTO dto, String newClassValue) {
        AttributeData[] attrs = dto.getAttributeData();
        if (attrs == null) return;
        for (AttributeData a : attrs) {
            if ("class".equalsIgnoreCase(a.getName())) {
                a.setValue(newClassValue);
                return;
            }
        }
    }
}
