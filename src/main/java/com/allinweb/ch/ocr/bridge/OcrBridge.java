package com.allinweb.ch.ocr.bridge;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA binding to ar_ocr.dll. Mirrors the C ABI declared in include/ar_ocr.h
 * inside the MultiTest-OCR project. Memory ownership is split: every output
 * array (OcrWordC*, OcrButtonC*) is allocated by the DLL and freed by the
 * matching aro_free_* call. JNA copies values into Java structures before
 * freeing.
 *
 * <p>The DLL is located by JNA via {@code -Djna.library.path} or the process
 * library search path. {@code OcrBridgeService} sets this property at first
 * use from {@code ARPropertyEnum.PATH_OCR} (the {@code path_ocr} entry in
 * {@code ARWeb.config}).
 */
public interface OcrBridge extends Library {

    /** Lazily loaded singleton. UnsatisfiedLinkError if the DLL is not on the JNA search path. */
    OcrBridge INSTANCE = Native.load("ar_ocr", OcrBridge.class);

    /** @return the DLL version string. Owned by the DLL; do not free. */
    String aro_version();

    /**
     * Open an engine handle. NULL/"" tessdata_path triggers extraction of the
     * embedded tessdata to {@code %LOCALAPPDATA%\ar_ocr\tessdata}.
     * @return the opaque handle, or {@code null} on failure (see {@link #aro_last_error}).
     */
    Pointer aro_open(String tessdata_path);

    /** Close an engine handle. Safe to pass {@code null}. */
    void aro_close(Pointer handle);

    /** Single-pass OCR on a raw pixel buffer. */
    int aro_recognize(Pointer handle,
                      byte[] pixels, int width, int height, int stride,
                      OcrConfigC.ByReference cfg,
                      PointerByReference out_words, IntByReference out_count);

    /** Single-pass OCR on a file (PNG/JPG/JPEG/BMP/TIFF). Empty args = no-op. */
    int aro_recognize_file(Pointer handle,
                           String folder, String filename,
                           OcrConfigC.ByReference cfg,
                           PointerByReference out_words, IntByReference out_count);

    /** Multi-pass OCR (raw + CLAHE) with IoU dedup; cfg.clahe enables pass 2. */
    int aro_recognize_multipass(Pointer handle,
                                byte[] pixels, int width, int height, int stride,
                                OcrConfigC.ByReference cfg,
                                PointerByReference out_words, IntByReference out_count);

    /** File-based variant of {@link #aro_recognize_multipass}. */
    int aro_recognize_multipass_file(Pointer handle,
                                     String folder, String filename,
                                     OcrConfigC.ByReference cfg,
                                     PointerByReference out_words, IntByReference out_count);

    /**
     * Detect color-thresholded buttons (red/blue/any per cfg flags) and run
     * per-ROI OCR with PSM_SINGLE_BLOCK.
     */
    int aro_detect_buttons_and_ocr(Pointer handle,
                                   byte[] pixels, int width, int height, int stride,
                                   OcrConfigC.ByReference cfg,
                                   PointerByReference out_buttons, IntByReference out_count);

    /** File-based variant of {@link #aro_detect_buttons_and_ocr}. */
    int aro_detect_buttons_and_ocr_file(Pointer handle,
                                        String folder, String filename,
                                        OcrConfigC.ByReference cfg,
                                        PointerByReference out_buttons, IntByReference out_count);

    /** Free an OcrWordC array allocated by aro_recognize* . Safe with (NULL, 0). */
    void aro_free_words(Pointer words, int count);

    /** Free an OcrButtonC array allocated by aro_detect_buttons_and_ocr* (frees each button's words too). */
    void aro_free_buttons(Pointer buttons, int count);

    /**
     * Last error message for the given handle, or open-time error if {@code handle == null}.
     * Storage is thread-local; pointer is valid until the next call on this thread.
     */
    String aro_last_error(Pointer handle);
}
