package com.allinweb.ch.vision;

import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrBox;
import com.allinweb.ch.ocr.bridge.OcrBridgeService;
import com.allinweb.ch.ocr.bridge.OcrResult;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.vision.ocr.OcrOpenCvUtils;
import com.allinweb.ch.vision.ocr.OcrPreprocessorOpenCv;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * Per-color button detection plus per-ROI OCR. Each detected rect is upscaled by
 * {@link OcrPreprocessorOpenCv#preprocessButton(Mat)} before OCR, so word boxes
 * are mapped back to original-image pixel space before being returned.
 */
@Slf4j
public final class ButtonDetectionService {

    private static final int BUTTON_ROI_UPSCALE = 3;

    private ButtonDetectionService() {}

    /**
     * Runs enabled color passes and returns every OCR word with its box in the
     * original image pixel space. Returns an empty list when all passes are disabled.
     */
    public static List<OcrWord> detectAndOcr(Mat matOriginal, OcrConfig cfg) {
        List<OcrWord> out = new ArrayList<>();
        if (matOriginal == null || matOriginal.empty()) return out;

        boolean red = cfg != null && cfg.getBool("button_detection", "enable_red", false);
        boolean blue = cfg != null && cfg.getBool("button_detection", "enable_blue", false);
        boolean any = cfg != null && cfg.getBool("button_detection", "enable_any", false);
        if (!red && !blue && !any) return out;

        try {
            Class.forName("com.allinweb.ch.vision.ocr.OpenCvNativeLoader");
        } catch (Throwable t) {
            log.warn("OpenCV native load failed - button detection disabled: {}", t.getMessage());
            return out;
        }

        try {
            if (red) out.addAll(runPass(matOriginal, cfg, OcrPreprocessorOpenCv::detectRedButtons, "RED"));
            if (blue) out.addAll(runPass(matOriginal, cfg, OcrPreprocessorOpenCv::detectBlueButtons, "BLUE"));
            if (any) out.addAll(runPass(matOriginal, cfg, OcrPreprocessorOpenCv::detectAnyButtons, "ANY"));
        } catch (RuntimeException e) {
            log.warn("Button detection pass failed: {}", e.getMessage(), e);
        }

        log.info("ButtonDetectionService: red={} blue={} any={} - {} word(s) recognized", red, blue, any, out.size());
        return out;
    }

    private static List<OcrWord> runPass(Mat matOriginal, OcrConfig cfg, Function<Mat, List<Rect>> detector, String label) {
        List<OcrWord> out = new ArrayList<>();
        List<Rect> rects;
        try {
            rects = detector.apply(matOriginal);
        } catch (RuntimeException e) {
            log.debug("{} detector threw: {}", label, e.getMessage());
            return out;
        }
        if (rects == null || rects.isEmpty()) return out;

        for (Rect rect : rects) {
            Mat roi = null;
            Mat prep = null;
            try {
                roi = new Mat(matOriginal, rect);
                prep = OcrPreprocessorOpenCv.preprocessButton(roi);
                RasterImage image = OcrOpenCvUtils.matToRasterImage(prep);
                OcrResult result = OcrBridgeService.recognize(image, cfg);
                if (result == null || result.getWords() == null) continue;
                for (OcrWord word : result.getWords()) {
                    OcrWord mapped = mapToOriginalRect(word, rect);
                    if (mapped != null) out.add(mapped);
                }
            } catch (Exception e) {
                log.debug("{} button OCR failed on rect {}: {}", label, rect, e.getMessage());
            } finally {
                if (prep != null) prep.release();
                if (roi != null) roi.release();
            }
        }
        return out;
    }

    private static OcrWord mapToOriginalRect(OcrWord word, Rect rect) {
        String text = word.getText() == null ? "" : word.getText().trim();
        if (text.isEmpty()) return null;

        OcrBox b = word.getBounds();
        if (b == null) return null;

        int mappedX = rect.x + (int) Math.round(b.x() / (double) BUTTON_ROI_UPSCALE);
        int mappedY = rect.y + (int) Math.round(b.y() / (double) BUTTON_ROI_UPSCALE);
        int mappedW = Math.max(1, (int) Math.round(b.width() / (double) BUTTON_ROI_UPSCALE));
        int mappedH = Math.max(1, (int) Math.round(b.height() / (double) BUTTON_ROI_UPSCALE));
        return new OcrWord(text, new OcrBox(mappedX, mappedY, mappedW, mappedH), word.getConfidence());
    }
}
