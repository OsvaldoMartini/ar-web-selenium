package com.allinweb.ch.vision;

import com.allinweb.ch.ocr.bridge.OcrBox;
import java.awt.Rectangle;
import net.sourceforge.tess4j.Word;

final class Tess4jOcrBoxAdapter {

    private Tess4jOcrBoxAdapter() {}

    static OcrBox boundsOf(Word word) {
        if (word == null) return null;
        return from(word.getBoundingBox());
    }

    private static OcrBox from(Rectangle rectangle) {
        if (rectangle == null) return null;
        return new OcrBox(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }
}
