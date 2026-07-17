package com.allinweb.ch.vision;

import com.allinweb.ch.ocr.bridge.OcrBox;
import java.lang.reflect.Field;
import net.sourceforge.tess4j.Word;

final class Tess4jOcrBoxAdapter {

    private Tess4jOcrBoxAdapter() {}

    static OcrBox boundsOf(Word word) {
        if (word == null) return null;
        return from(word.getBoundingBox());
    }

    private static OcrBox from(Object bounds) {
        if (bounds == null) return null;
        try {
            Class<?> boundsType = bounds.getClass();
            return new OcrBox(
                    intField(boundsType, bounds, "x"),
                    intField(boundsType, bounds, "y"),
                    intField(boundsType, bounds, "width"),
                    intField(boundsType, bounds, "height"));
        } catch (ReflectiveOperationException invalidBounds) {
            return null;
        }
    }

    private static int intField(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        Field field = type.getField(name);
        return field.getInt(instance);
    }
}
