package com.allinweb.ch.ocr.bridge;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA mirror of the C OcrButtonC struct. Field order MUST match the C
 * struct exactly. {@code words} is a DLL-allocated OcrWordC*; the rect
 * fields are in source-image (absolute) pixel space.
 */
@Structure.FieldOrder({"x", "y", "w", "h", "words", "word_count"})
public class OcrButtonC extends Structure {
    public int x;
    public int y;
    public int w;
    public int h;
    public Pointer words; // OcrWordC*
    public int word_count;

    public OcrButtonC() {}

    public OcrButtonC(Pointer p) {
        super(p);
        read();
    }
}
