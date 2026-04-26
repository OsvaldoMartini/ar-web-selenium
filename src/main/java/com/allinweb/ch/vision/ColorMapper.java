package com.allinweb.ch.vision;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Applies an ordered list of HSV-based color manipulation ops to a BGR Mat
 * before it's fed to Tesseract. Configured via
 * {@code color_mapping} param in {@link com.allinweb.ch.model.OcrConfig}.
 *
 * <p>Op schema (JSON array, each op is a map):
 * <pre>
 * {
 *   "name":            "bancastato-red-button",
 *   "mode":            "replace_in_hsv_range" | "keep_in_range_binarize"
 *                      | "desaturate_to_black_white" | "invert",
 *   "hsv_lower":       [h, s, v],               // required for first two modes
 *   "hsv_upper":       [h, s, v],               // required for first two modes
 *   "replacement_bgr": [b, g, r]                // required for replace_in_hsv_range
 * }
 * </pre>
 *
 * <p>HSV ranges follow OpenCV convention: H 0..179, S 0..255, V 0..255.
 */
@Slf4j
public final class ColorMapper {

    private ColorMapper() {}

    /**
     * Apply the given ops to {@code srcBgr} in order. Returns a NEW Mat; caller owns and must release it.
     * If ops is null/empty, returns a clone of the input unchanged.
     */
    public static Mat apply(Mat srcBgr, List<Map<String, Object>> ops) {
        if (srcBgr == null || srcBgr.empty()) {
            log.warn("ColorMapper.apply: null/empty source Mat");
            return srcBgr;
        }
        Mat current = srcBgr.clone();
        if (ops == null || ops.isEmpty()) return current;

        for (Map<String, Object> op : ops) {
            if (op == null) continue;
            String mode = asString(op.get("mode"));
            if (mode == null) continue;
            try {
                switch (mode) {
                    case "replace_in_hsv_range":
                        current = applyReplaceInHsvRange(current, op);
                        break;
                    case "keep_in_range_binarize":
                        current = applyKeepInRangeBinarize(current, op);
                        break;
                    case "desaturate_to_black_white":
                        current = applyDesaturateBW(current, op);
                        break;
                    case "invert":
                        current = applyInvert(current);
                        break;
                    default:
                        log.warn("ColorMapper: unknown mode '{}' — skipping", mode);
                }
            } catch (Exception e) {
                log.warn("ColorMapper op '{}' mode '{}' failed: {}", op.get("name"), mode, e.getMessage());
            }
        }
        return current;
    }

    // ── Ops ──────────────────────────────────────────────────────────────

    private static Mat applyReplaceInHsvRange(Mat bgr, Map<String, Object> op) {
        Scalar lower = asHsv(op.get("hsv_lower"));
        Scalar upper = asHsv(op.get("hsv_upper"));
        Scalar replacementBgr = asBgr(op.get("replacement_bgr"));
        if (lower == null || upper == null || replacementBgr == null) {
            log.warn("replace_in_hsv_range missing hsv_lower/hsv_upper/replacement_bgr — skip");
            return bgr;
        }

        Mat hsv = new Mat();
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsv, lower, upper, mask);

        // Paint replacement where mask != 0. Create a solid-color Mat and copy with mask.
        Mat solid = new Mat(bgr.size(), CvType.CV_8UC3, replacementBgr);
        solid.copyTo(bgr, mask);

        hsv.release();
        mask.release();
        solid.release();
        return bgr;
    }

    private static Mat applyKeepInRangeBinarize(Mat bgr, Map<String, Object> op) {
        Scalar lower = asHsv(op.get("hsv_lower"));
        Scalar upper = asHsv(op.get("hsv_upper"));
        if (lower == null || upper == null) {
            log.warn("keep_in_range_binarize missing hsv_lower/hsv_upper — skip");
            return bgr;
        }

        Mat hsv = new Mat();
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsv, lower, upper, mask);
        // mask: white (255) where in range, black (0) elsewhere.
        // For Tesseract we want black text on white background.
        // So invert: text pixels = black, background = white.
        Core.bitwise_not(mask, mask);

        // Return as 3-channel grayscale-looking BGR so downstream code treats it the same.
        Mat out = new Mat();
        Imgproc.cvtColor(mask, out, Imgproc.COLOR_GRAY2BGR);

        bgr.release();
        hsv.release();
        mask.release();
        return out;
    }

    private static Mat applyDesaturateBW(Mat bgr, Map<String, Object> op) {
        // Heuristic: anything with HSV saturation > S_THRESH OR value < V_DARK → black;
        // otherwise (near white / pale) → white.
        int sThresh = asInt(op.get("s_thresh"), 40);
        int vDark = asInt(op.get("v_dark"), 180);

        Mat hsv = new Mat();
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);

        // Mask for "colored" pixels: S > sThresh
        Mat coloredMask = new Mat();
        Core.inRange(hsv, new Scalar(0, sThresh, 0), new Scalar(179, 255, 255), coloredMask);
        // Mask for "dark" pixels: V < vDark
        Mat darkMask = new Mat();
        Core.inRange(hsv, new Scalar(0, 0, 0), new Scalar(179, 255, vDark), darkMask);

        Mat textMask = new Mat();
        Core.bitwise_or(coloredMask, darkMask, textMask);

        // Build a white canvas and paint black where textMask!=0.
        Mat white = new Mat(bgr.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        Mat black = new Mat(bgr.size(), CvType.CV_8UC3, new Scalar(0, 0, 0));
        black.copyTo(white, textMask);

        bgr.release();
        hsv.release();
        coloredMask.release();
        darkMask.release();
        textMask.release();
        black.release();
        return white;
    }

    private static Mat applyInvert(Mat bgr) {
        Mat out = new Mat();
        Core.bitwise_not(bgr, out);
        bgr.release();
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Scalar asHsv(Object v) {
        List<Number> l = (v instanceof List) ? (List<Number>) v : null;
        if (l == null || l.size() < 3) return null;
        return new Scalar(
                l.get(0).doubleValue(), l.get(1).doubleValue(), l.get(2).doubleValue());
    }

    @SuppressWarnings("unchecked")
    private static Scalar asBgr(Object v) {
        List<Number> l = (v instanceof List) ? (List<Number>) v : null;
        if (l == null || l.size() < 3) return null;
        // Gson parses JSON numbers as Double — accept both.
        return new Scalar(
                l.get(0).doubleValue(), l.get(1).doubleValue(), l.get(2).doubleValue());
    }

    private static int asInt(Object v, int fallback) {
        if (v == null) return fallback;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
