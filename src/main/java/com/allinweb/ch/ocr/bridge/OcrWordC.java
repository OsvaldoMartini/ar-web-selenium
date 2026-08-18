package com.allinweb.ch.ocr.bridge;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA mirror of the C OcrWordC struct. Field order MUST match the C struct
 * exactly. Memory: text points into a DLL-owned block; the entire array is
 * freed by aro_free_words. Java copies text via {@link Pointer#getString}
 * before that free.
 */
@Structure.FieldOrder({"text", "conf", "x", "y", "w", "h"})
public class OcrWordC extends Structure {
    public Pointer text; // const char* — UTF-8, owned by DLL
    public float conf;
    public int x;
    public int y;
    public int w;
    public int h;

    public OcrWordC() {}

    public OcrWordC(Pointer p) {
        super(p);
        read();
    }
}
