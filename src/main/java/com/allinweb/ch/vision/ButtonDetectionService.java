package com.allinweb.ch.vision;

import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.vision.ocr.OcrOpenCvUtils;
import com.allinweb.ch.vision.ocr.OcrPreprocessorOpenCv;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Word;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * Per-color button detection + per-ROI OCR. Each detected rect is upscaled ×3 by
 * {@link OcrPreprocessorOpenCv#preprocessButton(Mat)} before OCR, so word bboxes
 * are mapped back to original-image pixel space before being returned.
 */
@Slf4j
public final class ButtonDetectionService {

    private static final int BUTTON_ROI_UPSCALE = 3;

    private ButtonDetectionService() {}

    /**
     * Runs enabled color passes and returns every OCR'd word with its bbox in the
     * original image's pixel space. Returns empty list when all passes are disabled.
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
            log.warn("OpenCV native load failed — button detection disabled: {}", t.getMessage());
            return out;
        }

        try {
            ITesseract tess = WebPageOcrService.createEngine(cfg);
            // Buttons hold single short label → bias segmentation.
            tess.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK);

            if (red) out.addAll(runPass(matOriginal, tess, OcrPreprocessorOpenCv::detectRedButtons, "RED"));
            if (blue) out.addAll(runPass(matOriginal, tess, OcrPreprocessorOpenCv::detectBlueButtons, "BLUE"));
            if (any) out.addAll(runPass(matOriginal, tess, OcrPreprocessorOpenCv::detectAnyButtons, "ANY"));
        } catch (IOException | RuntimeException e) {
            log.warn("Button detection pass failed: {}", e.getMessage(), e);
        }

        log.info("ButtonDetectionService: red={} blue={} any={} — {} word(s) recognized", red, blue, any, out.size());
        return out;
    }

    private static List<OcrWord> runPass(
            Mat matOriginal, ITesseract tess, Function<Mat, List<Rect>> detector, String label) {
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
                BufferedImage img = OcrOpenCvUtils.matToBufferedImage(prep);
                List<Word> words = tess.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);
                if (words == null) continue;
                for (Word w : words) {
                    String text = w.getText() == null ? "" : w.getText().trim();
                    if (text.isEmpty()) continue;
                    Rectangle b = w.getBoundingBox();
                    if (b == null) continue;
                    // Map back: ROI is the original rect, OCR ran on ×3 upscale.
                    int mappedX = rect.x + (int) Math.round(b.x / (double) BUTTON_ROI_UPSCALE);
                    int mappedY = rect.y + (int) Math.round(b.y / (double) BUTTON_ROI_UPSCALE);
                    int mappedW = Math.max(1, (int) Math.round(b.width / (double) BUTTON_ROI_UPSCALE));
                    int mappedH = Math.max(1, (int) Math.round(b.height / (double) BUTTON_ROI_UPSCALE));
                    Rectangle mapped = new Rectangle(mappedX, mappedY, mappedW, mappedH);
                    out.add(new OcrWord(text, mapped, w.getConfidence()));
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
}
