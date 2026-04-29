package com.allinweb.ch.ocr.bridge;

import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Native OCR engine — drop-in replacement for {@link com.allinweb.ch.vision.WebPageOcrService}
 * routed through {@code ar_ocr.dll} via JNA. API parity is intentional:
 * {@link #recognize(BufferedImage)}, {@link #recognize(BufferedImage, OcrConfig)},
 * and {@link #recognizeMultiPass(BufferedImage, OcrConfig)} return the same
 * {@link OcrResult} shape as the Java side.
 *
 * <p>The DLL is located via {@code jna.library.path}, which this class sets at
 * first use from {@link ARPropertyEnum#PATH_OCR} (the {@code path_ocr} row in
 * {@code ARWeb.config}).
 *
 * <p>A single {@code ARO_HANDLE} is shared across calls. The DLL serialises
 * Tesseract behind a per-handle mutex, so concurrent callers serialise
 * automatically — open multiple handles only if you genuinely need parallel OCR.
 */
@Slf4j
public final class OcrBridgeService {

    private static volatile boolean libraryPathConfigured;
    private static volatile Pointer sharedHandle;
    private static final Object HANDLE_LOCK = new Object();
    private static final double DEFAULT_IOU_THRESHOLD = 0.6;

    private OcrBridgeService() {}

    /**
     * Configure {@code jna.library.path} from {@code path_ocr} before any DLL
     * touch. Idempotent. Safe to call from application bootstrap; otherwise
     * the first {@link #recognize} call invokes it lazily.
     */
    public static void ensureLibraryPath() {
        if (libraryPathConfigured) return;
        synchronized (HANDLE_LOCK) {
            if (libraryPathConfigured) return;
            String pathOcr = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_OCR);
            if (pathOcr != null && !pathOcr.isBlank()) {
                String existing = System.getProperty("jna.library.path", "");
                if (!existing.contains(pathOcr)) {
                    String merged = existing.isEmpty() ? pathOcr : existing + File.pathSeparator + pathOcr;
                    System.setProperty("jna.library.path", merged);
                    log.info("OcrBridgeService — jna.library.path set to {}", merged);
                }
            } else {
                log.warn("OcrBridgeService — path_ocr not set in ARWeb.config; "
                        + "ar_ocr.dll must be on PATH or already-set jna.library.path");
            }
            libraryPathConfigured = true;
        }
    }

    private static Pointer handle() {
        ensureLibraryPath();
        Pointer h = sharedHandle;
        if (h != null) return h;
        synchronized (HANDLE_LOCK) {
            if (sharedHandle != null) return sharedHandle;
            Pointer opened = OcrBridge.INSTANCE.aro_open(null);
            if (opened == null) {
                String err = OcrBridge.INSTANCE.aro_last_error(null);
                throw new IllegalStateException("aro_open(null) failed: " + err);
            }
            sharedHandle = opened;
            Runtime.getRuntime()
                    .addShutdownHook(new Thread(
                            () -> {
                                try {
                                    OcrBridge.INSTANCE.aro_close(opened);
                                } catch (Throwable ignore) {
                                }
                            },
                            "ar-ocr-shutdown"));
            log.info("OcrBridgeService — ar_ocr {} opened", OcrBridge.INSTANCE.aro_version());
            return opened;
        }
    }

    /** Single-pass OCR. */
    public static OcrResult recognize(BufferedImage image) {
        return recognize(image, null);
    }

    /** Single-pass OCR honouring config overrides for engine params. */
    public static OcrResult recognize(BufferedImage image, OcrConfig cfg) {
        if (image == null) return empty();
        try {
            byte[] pixels = bgraBytes(image);
            int w = image.getWidth();
            int h = image.getHeight();
            OcrConfigC.ByReference cfgC = toCfgC(cfg, /*multipass=*/ false);
            PointerByReference outWords = new PointerByReference();
            IntByReference outCount = new IntByReference();
            int rc = OcrBridge.INSTANCE.aro_recognize(handle(), pixels, w, h, w * 4, cfgC, outWords, outCount);
            if (rc != 0) {
                log.warn("aro_recognize rc={}: {}", rc, OcrBridge.INSTANCE.aro_last_error(handle()));
                return empty();
            }
            List<OcrWord> words = new ArrayList<>();
            consumeWords(outWords.getValue(), outCount.getValue(), words);
            return assemble(words);
        } catch (Throwable t) {
            log.warn("Native OCR recognize failed: {}", t.getMessage(), t);
            return empty();
        }
    }

    /**
     * Multi-pass OCR matching {@link com.allinweb.ch.vision.WebPageOcrService#recognizeMultiPass}:
     * raw + (optional) CLAHE-preprocessed pass + (optional) red/blue/any
     * button detection. Cross-pass IoU dedup at the configured threshold
     * (default 0.6).
     */
    public static OcrResult recognizeMultiPass(BufferedImage image, OcrConfig cfg) {
        if (image == null) return empty();
        try {
            Pointer hh = handle();
            byte[] pixels = bgraBytes(image);
            int w = image.getWidth();
            int h = image.getHeight();
            List<OcrWord> all = new ArrayList<>();

            // Raw + CLAHE in one DLL call (DLL does its own IoU dedup across both passes).
            OcrConfigC.ByReference cfgC = toCfgC(cfg, /*multipass=*/ true);
            PointerByReference outWords = new PointerByReference();
            IntByReference outCount = new IntByReference();
            int rc = OcrBridge.INSTANCE.aro_recognize_multipass(hh, pixels, w, h, w * 4, cfgC, outWords, outCount);
            if (rc == 0) {
                consumeWords(outWords.getValue(), outCount.getValue(), all);
            } else {
                log.warn("aro_recognize_multipass rc={}: {}", rc, OcrBridge.INSTANCE.aro_last_error(hh));
            }

            // Optional button detection pass — same as Java side.
            if (anyButtonEnabled(cfg)) {
                OcrConfigC.ByReference btnCfg = toBtnCfgC(cfg);
                PointerByReference outBtns = new PointerByReference();
                IntByReference outBtnCount = new IntByReference();
                int brc = OcrBridge.INSTANCE.aro_detect_buttons_and_ocr(
                        hh, pixels, w, h, w * 4, btnCfg, outBtns, outBtnCount);
                if (brc == 0) {
                    consumeButtonWords(outBtns.getValue(), outBtnCount.getValue(), all);
                } else {
                    log.warn("aro_detect_buttons_and_ocr rc={}: {}", brc, OcrBridge.INSTANCE.aro_last_error(hh));
                }
            }

            double iou = cfg == null
                    ? DEFAULT_IOU_THRESHOLD
                    : cfg.getDouble("correlation", "dedupe_iou", DEFAULT_IOU_THRESHOLD);
            return assemble(dedupeByIoU(all, iou));
        } catch (Throwable t) {
            log.warn("Native OCR multipass failed: {}", t.getMessage(), t);
            return empty();
        }
    }

    // -- Helpers ---------------------------------------------------------

    private static byte[] bgraBytes(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        byte[] out = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int o = i * 4;
            out[o] = (byte) (p & 0xff); // B
            out[o + 1] = (byte) ((p >> 8) & 0xff); // G
            out[o + 2] = (byte) ((p >> 16) & 0xff); // R
            out[o + 3] = (byte) ((p >> 24) & 0xff); // A
        }
        return out;
    }

    private static OcrConfigC.ByReference toCfgC(OcrConfig cfg, boolean multipass) {
        OcrConfigC.ByReference c = new OcrConfigC.ByReference();
        c.psm = cfg == null ? 3 : cfg.getInt("engine", "psm_mode", 3);
        c.oem = 3;
        c.lang = null;
        c.upscale = cfg == null ? 2 : Math.max(1, cfg.getInt("preprocessing", "upscale_factor", 2));
        c.clahe = (multipass && cfg != null && cfg.getBool("preprocessing", "enable_clahe_pass", false)) ? 1 : 0;
        c.detect_red = 0;
        c.detect_blue = 0;
        c.detect_any = 0;
        c.write();
        return c;
    }

    private static OcrConfigC.ByReference toBtnCfgC(OcrConfig cfg) {
        OcrConfigC.ByReference c = new OcrConfigC.ByReference();
        c.psm = 6; // PSM_SINGLE_BLOCK — DLL also forces this internally
        c.oem = 3;
        c.lang = null;
        c.upscale = 2;
        c.clahe = 0;
        c.detect_red = (cfg != null && cfg.getBool("button_detection", "enable_red", false)) ? 1 : 0;
        c.detect_blue = (cfg != null && cfg.getBool("button_detection", "enable_blue", false)) ? 1 : 0;
        c.detect_any = (cfg != null && cfg.getBool("button_detection", "enable_any", false)) ? 1 : 0;
        c.write();
        return c;
    }

    private static boolean anyButtonEnabled(OcrConfig cfg) {
        return cfg != null
                && (cfg.getBool("button_detection", "enable_red", false)
                        || cfg.getBool("button_detection", "enable_blue", false)
                        || cfg.getBool("button_detection", "enable_any", false));
    }

    private static void consumeWords(Pointer base, int count, List<OcrWord> out) {
        if (base == null || count <= 0) return;
        OcrWordC first = new OcrWordC(base);
        OcrWordC[] arr = (OcrWordC[]) first.toArray(count);
        for (OcrWordC c : arr) {
            String t = (c.text == null) ? "" : c.text.getString(0);
            if (t.isEmpty()) continue;
            out.add(new OcrWord(t, new Rectangle(c.x, c.y, c.w, c.h), c.conf));
        }
        OcrBridge.INSTANCE.aro_free_words(base, count);
    }

    private static void consumeButtonWords(Pointer base, int count, List<OcrWord> out) {
        if (base == null || count <= 0) return;
        OcrButtonC first = new OcrButtonC(base);
        OcrButtonC[] arr = (OcrButtonC[]) first.toArray(count);
        for (OcrButtonC b : arr) {
            if (b.words == null || b.word_count <= 0) continue;
            OcrWordC firstWord = new OcrWordC(b.words);
            OcrWordC[] words = (OcrWordC[]) firstWord.toArray(b.word_count);
            for (OcrWordC c : words) {
                String t = (c.text == null) ? "" : c.text.getString(0);
                if (t.isEmpty()) continue;
                out.add(new OcrWord(t, new Rectangle(c.x, c.y, c.w, c.h), c.conf));
            }
        }
        OcrBridge.INSTANCE.aro_free_buttons(base, count);
    }

    private static OcrResult assemble(List<OcrWord> words) {
        StringBuilder full = new StringBuilder();
        for (OcrWord w : words) full.append(w.getText()).append(' ');
        return new OcrResult(full.toString().trim(), words);
    }

    private static OcrResult empty() {
        return new OcrResult("", new ArrayList<>());
    }

    // Port of WebPageOcrService.dedupeByIoU. Keeps the higher-confidence word
    // per overlapping bbox cluster; on confidence tie, keeps the longer text.
    private static List<OcrWord> dedupeByIoU(List<OcrWord> words, double threshold) {
        List<OcrWord> out = new ArrayList<>();
        for (OcrWord w : words) {
            if (w == null
                    || w.getBounds() == null
                    || w.getText() == null
                    || w.getText().isBlank()) continue;
            int dup = -1;
            for (int i = 0; i < out.size(); i++) {
                if (iou(w.getBounds(), out.get(i).getBounds()) > threshold) {
                    dup = i;
                    break;
                }
            }
            if (dup < 0) {
                out.add(w);
            } else {
                OcrWord existing = out.get(dup);
                boolean replace = w.getConfidence() > existing.getConfidence()
                        || (w.getConfidence() == existing.getConfidence()
                                && w.getText().length() > existing.getText().length());
                if (replace) out.set(dup, w);
            }
        }
        return out;
    }

    private static double iou(Rectangle a, Rectangle b) {
        if (a == null || b == null) return 0;
        double x1 = Math.max(a.x, b.x);
        double y1 = Math.max(a.y, b.y);
        double x2 = Math.min((double) a.x + a.width, (double) b.x + b.width);
        double y2 = Math.min((double) a.y + a.height, (double) b.y + b.height);
        double iw = Math.max(0, x2 - x1);
        double ih = Math.max(0, y2 - y1);
        double inter = iw * ih;
        double union = (double) a.width * a.height + (double) b.width * b.height - inter;
        return union <= 0 ? 0 : inter / union;
    }
}
