package com.allinweb.ch.util;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrResult;
import com.allinweb.ch.ocr.bridge.OcrWord;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coordinate-anchored correlation between OCR words (image pixel space)
 * and ElementDTOs whose bounding rects were captured by
 * {@link PageDiagnosticDumper} (CSS pixel space).
 *
 * <p>Quality tiers, in order of preference per element:
 * <ul>
 *   <li>EXACT_CONTAIN - OCR word center lies inside the DTO rect</li>
 *   <li>OVERLAP       - OCR word bbox intersects the DTO rect</li>
 *   <li>PROXIMITY     - nearest OCR word center within {@value #PROXIMITY_THRESHOLD_CSS_PX} px</li>
 *   <li>NONE          - no OCR text nearby</li>
 * </ul>
 */
public final class OcrDomCorrelator {

    public static final double PROXIMITY_THRESHOLD_CSS_PX = 30.0;

    private OcrDomCorrelator() {}

    /** Minimal shape matching {@code page-HP-rects.json} entries. */
    public static class RectEntry {
        public String xpath;
        public String iframeXPath;
        public Boolean found;
        public String tag;
        public Rect rect;

        public static class Rect {
            public double x, y, width, height;
            // full_page support: page-relative coords written by PageDiagnosticDumper.
            // Used by the correlator when screenshot.scope=full_page so OCR words detected in a
            // scroll-stitched image align with DOM rects shifted by the original scroll offset.
            public Double pageX;
            public Double pageY;
        }
    }

    public static List<OcrCorrelationResult> correlate(
            ElementDTO[] elements, List<RectEntry> domRects, OcrResult ocr, double dpr) {
        return correlate(elements, domRects, ocr, dpr, null);
    }

    public static List<OcrCorrelationResult> correlate(
            ElementDTO[] elements, List<RectEntry> domRects, OcrResult ocr, double dpr, OcrConfig cfg) {

        if (dpr <= 0) dpr = 1.0;

        boolean useFullPageCoords =
                cfg != null && "full_page".equalsIgnoreCase(cfg.getString("screenshot", "scope", "viewport"));

        double thGlobal = cfg == null
                ? PROXIMITY_THRESHOLD_CSS_PX
                : cfg.getDouble("correlation", "proximity_px_global", PROXIMITY_THRESHOLD_CSS_PX);
        double thInput = cfg == null ? thGlobal : cfg.getDouble("correlation", "proximity_px_input", thGlobal);
        double thButton = cfg == null ? thGlobal : cfg.getDouble("correlation", "proximity_px_button", thGlobal);

        Map<String, RectEntry> byXPath = new HashMap<>();
        if (domRects != null) {
            for (RectEntry r : domRects) {
                if (r != null && r.xpath != null) byXPath.put(r.xpath, r);
            }
        }

        List<OcrCorrelationResult> results = new ArrayList<>();
        if (elements == null) return results;

        for (ElementDTO dto : elements) {
            if (dto == null || dto.getXPath() == null) continue;

            OcrCorrelationResult r = new OcrCorrelationResult();
            r.xpath = dto.getXPath();
            r.iframeXPath = dto.getIFrameXPath();
            r.tagName = dto.getTagName();
            r.domText = dto.getSomeText();

            RectEntry domRect = byXPath.get(dto.getXPath());
            if (domRect == null || Boolean.FALSE.equals(domRect.found) || domRect.rect == null) {
                r.matchQuality = "NONE";
                r.ocrText = "";
                r.ocrNearestText = "";
                r.ocrConfidenceAvg = 0.0;
                r.ocrWordBoxes = Collections.emptyList();
                results.add(r);
                continue;
            }

            double dx = useFullPageCoords && domRect.rect.pageX != null ? domRect.rect.pageX : domRect.rect.x;
            double dy = useFullPageCoords && domRect.rect.pageY != null ? domRect.rect.pageY : domRect.rect.y;
            double dw = domRect.rect.width;
            double dh = domRect.rect.height;
            double dcx = dx + dw / 2.0;
            double dcy = dy + dh / 2.0;

            OcrCorrelationResult.Rect domOut = new OcrCorrelationResult.Rect();
            domOut.x = dx;
            domOut.y = dy;
            domOut.width = dw;
            domOut.height = dh;
            r.domRect = domOut;

            String tag = dto.getTagName() == null ? "" : dto.getTagName().toLowerCase();
            double proximityThreshold;
            if ("input".equals(tag) || "textarea".equals(tag) || "select".equals(tag)) {
                proximityThreshold = thInput;
            } else if ("button".equals(tag)) {
                proximityThreshold = thButton;
            } else {
                proximityThreshold = thGlobal;
            }

            List<OcrCorrelationResult.WordBox> contained = new ArrayList<>();
            List<OcrCorrelationResult.WordBox> overlapping = new ArrayList<>();
            List<OcrCorrelationResult.WordBox> nearest = new ArrayList<>();
            double nearestDist = Double.MAX_VALUE;

            for (OcrWord word : ocr.getWords()) {
                Rectangle b = word.getBounds();
                if (b == null) continue;

                double ox = b.x / dpr;
                double oy = b.y / dpr;
                double ow = b.width / dpr;
                double oh = b.height / dpr;
                double ocx = ox + ow / 2.0;
                double ocy = oy + oh / 2.0;

                OcrCorrelationResult.WordBox wb = new OcrCorrelationResult.WordBox();
                wb.text = word.getText();
                wb.x = ox;
                wb.y = oy;
                wb.width = ow;
                wb.height = oh;
                wb.confidence = word.getConfidence();

                boolean centerInside = ocx >= dx && ocx <= dx + dw && ocy >= dy && ocy <= dy + dh;
                boolean overlaps = !(ox + ow < dx || dx + dw < ox || oy + oh < dy || dy + dh < oy);

                if (centerInside) {
                    contained.add(wb);
                } else if (overlaps) {
                    overlapping.add(wb);
                } else {
                    double dist = Math.hypot(ocx - dcx, ocy - dcy);
                    if (dist <= proximityThreshold) {
                        if (dist < nearestDist) {
                            nearest.clear();
                            nearest.add(wb);
                            nearestDist = dist;
                        } else if (dist == nearestDist) {
                            nearest.add(wb);
                        }
                    }
                }
            }

            String quality;
            List<OcrCorrelationResult.WordBox> primary;
            if (!contained.isEmpty()) {
                quality = "EXACT_CONTAIN";
                primary = contained;
            } else if (!overlapping.isEmpty()) {
                quality = "OVERLAP";
                primary = overlapping;
            } else if (!nearest.isEmpty()) {
                quality = "PROXIMITY";
                primary = nearest;
            } else {
                quality = "NONE";
                primary = Collections.emptyList();
            }

            r.matchQuality = quality;
            r.ocrText = primary.stream()
                    .map(w -> w.text)
                    .collect(Collectors.joining(" "))
                    .trim();
            r.ocrNearestText = nearest.stream()
                    .map(w -> w.text)
                    .collect(Collectors.joining(" "))
                    .trim();
            r.ocrConfidenceAvg =
                    primary.stream().mapToDouble(w -> w.confidence).average().orElse(0.0);
            r.ocrWordBoxes = primary;
            results.add(r);
        }

        return results;
    }
}
