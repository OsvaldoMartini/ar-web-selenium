package com.allinweb.ch.vision;

import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrBox;
import com.allinweb.ch.ocr.bridge.OcrBridgeService;
import com.allinweb.ch.ocr.bridge.OcrEngine;
import com.allinweb.ch.ocr.bridge.OcrResult;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.vision.ocr.OcrOpenCvUtils;
import com.allinweb.ch.vision.ocr.OcrPreprocessorOpenCv;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.opencv.core.Mat;

/** Thin stateless OCR wrapper. Extracts classpath tessdata to a temp dir once per JVM. */
@Slf4j
public final class WebPageOcrService {

    private static final String[] LANGS = {"eng", "ita", "fra", "deu"};
    private static volatile File tessdataDir;
    private static volatile String combinedLang;

    private WebPageOcrService() {}

    private static synchronized File ensureTessdata() throws IOException {
        if (tessdataDir != null) return tessdataDir;

        File tempRoot = Files.createTempDirectory("tessdata_root_").toFile();
        File dir = new File(tempRoot, "tessdata");
        if (!dir.mkdirs() && !dir.exists()) {
            throw new IOException("Could not create tessdata dir: " + dir);
        }

        List<String> available = new ArrayList<>();
        ClassLoader cl = WebPageOcrService.class.getClassLoader();
        for (String lang : LANGS) {
            String resourcePath = "tesseract/tessdata/" + lang + ".traineddata";
            try (InputStream is = cl.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    log.warn("tessdata missing from classpath: {}", resourcePath);
                    continue;
                }
                File target = new File(dir, lang + ".traineddata");
                Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                available.add(lang);
            }
        }

        if (available.isEmpty()) {
            throw new IllegalStateException("No tessdata files found under tesseract/tessdata/ on classpath");
        }

        combinedLang = String.join("+", available);
        tessdataDir = dir;
        log.info("Tesseract tessdata extracted to {} with langs: {}", dir, combinedLang);
        return dir;
    }

    public static ITesseract createEngine() throws IOException {
        return createEngine(null);
    }

    /** Create a Tesseract engine, overriding languages/PSM/DPI from config when provided. */
    public static ITesseract createEngine(OcrConfig cfg) throws IOException {
        File dir = ensureTessdata();
        Tesseract tess = new Tesseract();
        tess.setDatapath(dir.getAbsolutePath());

        String lang = cfg == null ? combinedLang : cfg.getString("engine", "languages", combinedLang);
        tess.setLanguage(filterToAvailable(lang));

        int psm = cfg == null
                ? ITessAPI.TessPageSegMode.PSM_AUTO
                : cfg.getInt("engine", "psm_mode", ITessAPI.TessPageSegMode.PSM_AUTO);
        tess.setPageSegMode(psm);

        int dpi = cfg == null ? 300 : cfg.getInt("engine", "user_defined_dpi", 300);
        tess.setTessVariable("user_defined_dpi", String.valueOf(dpi));
        return tess;
    }

    /** Full-image OCR. Returns word-level boxes in image pixel space. */
    public static OcrResult recognize(RasterImage image) {
        return recognize(image, null);
    }

    /** Full-image OCR honoring config overrides for engine params. */
    public static OcrResult recognize(RasterImage image, OcrConfig cfg) {
        if (image == null) return emptyResult();
        if (OcrEngine.isNative()) return OcrBridgeService.recognize(image, cfg);
        return Tess4jRasterOcrAdapter.recognize(image, cfg);
    }

    /**
     * Multi-pass OCR: raw + optional CLAHE-preprocessed + optional color-button passes.
     * Results from each pass are merged into a single {@link OcrResult} after IoU
     * de-duplication.
     */
    public static OcrResult recognizeMultiPass(RasterImage image, OcrConfig cfg) {
        if (image == null) return emptyResult();
        if (OcrEngine.isNative()) return OcrBridgeService.recognizeMultiPass(image, cfg);

        List<OcrWord> all = new ArrayList<>();

        OcrResult raw = recognize(image, cfg);
        all.addAll(raw.getWords());

        boolean enableClahe = cfg != null && cfg.getBool("preprocessing", "enable_clahe_pass", false);
        if (enableClahe) {
            addClahePass(image, cfg, all);
        }

        boolean anyButton = cfg != null
                && (cfg.getBool("button_detection", "enable_red", false)
                        || cfg.getBool("button_detection", "enable_blue", false)
                        || cfg.getBool("button_detection", "enable_any", false));
        if (anyButton) {
            addButtonPass(image, cfg, all);
        }

        double iouThreshold = cfg == null ? 0.6 : cfg.getDouble("correlation", "dedupe_iou", 0.6);
        List<OcrWord> deduped = dedupeByIoU(all, iouThreshold);

        StringBuilder fullText = new StringBuilder();
        for (OcrWord w : deduped) fullText.append(w.getText()).append(' ');
        log.info(
                "Multi-pass OCR - raw={} clahe={} buttons={} merged={} (iou>={})",
                raw.getWords().size(),
                enableClahe,
                anyButton,
                deduped.size(),
                iouThreshold);
        return new OcrResult(fullText.toString().trim(), deduped);
    }

    private static void addClahePass(RasterImage image, OcrConfig cfg, List<OcrWord> all) {
        Mat src = null;
        Mat prep = null;
        try {
            Class.forName("com.allinweb.ch.vision.ocr.OpenCvNativeLoader");
            src = OcrOpenCvUtils.rasterImageToMat(image);
            prep = OcrPreprocessorOpenCv.preprocess(src);
            OcrResult prepRes = recognize(OcrOpenCvUtils.matToRasterImage(prep), cfg);
            int up = Math.max(1, cfg.getInt("preprocessing", "upscale_factor", 2));
            for (OcrWord w : prepRes.getWords()) {
                OcrBox b = w.getBounds();
                if (b == null) continue;
                OcrBox mapped = new OcrBox(
                        b.x() / up,
                        b.y() / up,
                        Math.max(1, b.width() / up),
                        Math.max(1, b.height() / up));
                all.add(new OcrWord(w.getText(), mapped, w.getConfidence()));
            }
        } catch (Throwable t) {
            log.warn("CLAHE preprocessing pass failed: {}", t.getMessage());
        } finally {
            if (prep != null) prep.release();
            if (src != null) src.release();
        }
    }

    private static void addButtonPass(RasterImage image, OcrConfig cfg, List<OcrWord> all) {
        Mat src = null;
        try {
            Class.forName("com.allinweb.ch.vision.ocr.OpenCvNativeLoader");
            src = OcrOpenCvUtils.rasterImageToMat(image);
            all.addAll(ButtonDetectionService.detectAndOcr(src, cfg));
        } catch (Throwable t) {
            log.warn("Button detection pass failed: {}", t.getMessage());
        } finally {
            if (src != null) src.release();
        }
    }

    /** Replace duplicates (IoU &gt; threshold) with the higher-confidence entry. */
    static List<OcrWord> dedupeByIoU(List<OcrWord> words, double iouThreshold) {
        List<OcrWord> out = new ArrayList<>();
        for (OcrWord w : words) {
            if (w == null || w.getBounds() == null || w.getText() == null || w.getText().isBlank()) continue;
            int dup = -1;
            for (int i = 0; i < out.size(); i++) {
                if (iou(w.getBounds(), out.get(i).getBounds()) > iouThreshold) {
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

    static double iou(OcrBox a, OcrBox b) {
        if (a == null || b == null) return 0;
        double interX1 = Math.max(a.x(), b.x());
        double interY1 = Math.max(a.y(), b.y());
        double interX2 = Math.min((double) a.x() + a.width(), (double) b.x() + b.width());
        double interY2 = Math.min((double) a.y() + a.height(), (double) b.y() + b.height());
        double interW = Math.max(0, interX2 - interX1);
        double interH = Math.max(0, interY2 - interY1);
        double inter = interW * interH;
        double union = (double) a.width() * a.height() + (double) b.width() * b.height() - inter;
        return union <= 0 ? 0 : inter / union;
    }

    /** Filter '+'-joined lang codes to those we actually extracted from classpath. */
    private static String filterToAvailable(String requested) {
        if (requested == null || requested.isBlank()) return combinedLang;
        String[] parts = requested.split("\\+");
        List<String> keep = new ArrayList<>();
        String[] availArr = combinedLang == null ? new String[0] : combinedLang.split("\\+");
        for (String req : parts) {
            for (String a : availArr) {
                if (a.equalsIgnoreCase(req.trim())) {
                    keep.add(a);
                    break;
                }
            }
        }
        return keep.isEmpty() ? combinedLang : String.join("+", keep);
    }

    private static OcrResult emptyResult() {
        return new OcrResult("", new ArrayList<>());
    }
}
