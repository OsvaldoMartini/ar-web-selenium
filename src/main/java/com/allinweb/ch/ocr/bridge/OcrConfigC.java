package com.allinweb.ch.ocr.bridge;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA mirror of the C OcrConfigC struct. Field order MUST match the C
 * struct exactly — see include/ar_ocr.h in the MultiTest-OCR project.
 *
 * <p>Defaults applied DLL-side when this is passed as null:
 * psm=3, oem=3, lang="eng+ita+fra+deu", upscale=2, clahe=0, detect_*=0.
 */
@Structure.FieldOrder({"psm", "oem", "lang", "upscale", "clahe",
                       "detect_red", "detect_blue", "detect_any"})
public class OcrConfigC extends Structure {
    public int psm;
    public int oem;
    public Pointer lang;     // const char* — null means use DLL default
    public int upscale;
    public int clahe;
    public int detect_red;
    public int detect_blue;
    public int detect_any;

    public OcrConfigC() {}

    public static class ByReference extends OcrConfigC implements Structure.ByReference {}
}
