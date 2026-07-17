package com.allinweb.ch.vision;

import com.allinweb.ch.ocr.bridge.OcrBox;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.util.OcrCorrelationResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Paints three overlay layers on the viewport screenshot:
 * <ul>
 *   <li>OCR word boxes — translucent green</li>
 *   <li>DOM element rects — translucent red</li>
 *   <li>EXACT_CONTAIN matches — solid thick green over the DOM rect</li>
 * </ul>
 * Coordinates assume DOM rects are in CSS px and OCR words are in image px;
 * DOM rects are multiplied by DPR to land in image pixel space.
 */
@Slf4j
public final class AnnotatedImageRenderer {

    private static final int OCR_GREEN = rgba(0, 200, 0, 160);
    private static final int DOM_RED = rgba(255, 0, 0, 160);
    private static final int MATCH_GREEN = rgba(0, 180, 0, 230);

    private AnnotatedImageRenderer() {}

    public static void render(
            RasterImage srcImg,
            List<OcrWord> ocrWords,
            List<OcrCorrelationResult> correlations,
            double dpr,
            Path outPath) {
        if (srcImg == null || outPath == null) return;
        if (dpr <= 0) dpr = 1.0;

        RasterImageCanvas out = new RasterImageCanvas(srcImg);

        // 1. OCR word boxes
        if (ocrWords != null) {
            for (OcrWord w : ocrWords) {
                OcrBox b = w.getBounds();
                if (b == null) continue;
                out.drawRect(b.x(), b.y(), b.width(), b.height(), OCR_GREEN, 1);
            }
        }

        // 2. DOM element rects
        if (correlations != null) {
            for (OcrCorrelationResult cr : correlations) {
                if (cr == null || cr.domRect == null) continue;
                int x = (int) Math.round(cr.domRect.x * dpr);
                int y = (int) Math.round(cr.domRect.y * dpr);
                int w = (int) Math.round(cr.domRect.width * dpr);
                int h = (int) Math.round(cr.domRect.height * dpr);
                out.drawRect(x, y, w, h, DOM_RED, 2);
            }
        }

        // 3. EXACT_CONTAIN highlight
        if (correlations != null) {
            for (OcrCorrelationResult cr : correlations) {
                if (cr == null || cr.domRect == null || !"EXACT_CONTAIN".equals(cr.matchQuality)) continue;
                int x = (int) Math.round(cr.domRect.x * dpr);
                int y = (int) Math.round(cr.domRect.y * dpr);
                int w = (int) Math.round(cr.domRect.width * dpr);
                int h = (int) Math.round(cr.domRect.height * dpr);
                out.drawRect(x, y, w, h, MATCH_GREEN, 3);
            }
        }

        try {
            RasterImageIO.writePng(out.toImage(), outPath);
            log.info("Annotated image written to {}", outPath);
        } catch (IOException e) {
            log.warn("Could not write annotated PNG {}: {}", outPath, e.getMessage());
        }
    }

    private static final class RasterImageCanvas {
        private final int width;
        private final int height;
        private final int[] rgb;

        private RasterImageCanvas(RasterImage source) {
            this.width = source.width();
            this.height = source.height();
            this.rgb = source.copyRgb();
        }

        private void drawRect(int x, int y, int width, int height, int color, int thickness) {
            if (width <= 0 || height <= 0 || thickness <= 0) return;
            for (int i = 0; i < thickness; i++) {
                int left = x + i;
                int top = y + i;
                int right = x + width - 1 - i;
                int bottom = y + height - 1 - i;
                drawHorizontal(left, right, top, color);
                drawHorizontal(left, right, bottom, color);
                drawVertical(top, bottom, left, color);
                drawVertical(top, bottom, right, color);
            }
        }

        private void drawHorizontal(int left, int right, int y, int color) {
            if (y < 0 || y >= height) return;
            int start = Math.max(0, left);
            int end = Math.min(width - 1, right);
            for (int x = start; x <= end; x++) {
                blendPixel(x, y, color);
            }
        }

        private void drawVertical(int top, int bottom, int x, int color) {
            if (x < 0 || x >= width) return;
            int start = Math.max(0, top);
            int end = Math.min(height - 1, bottom);
            for (int y = start; y <= end; y++) {
                blendPixel(x, y, color);
            }
        }

        private void blendPixel(int x, int y, int overlay) {
            int index = (y * width) + x;
            int base = rgb[index];
            int alpha = (overlay >>> 24) & 0xff;
            int inverse = 255 - alpha;
            int red = ((((overlay >>> 16) & 0xff) * alpha) + (((base >>> 16) & 0xff) * inverse)) / 255;
            int green = ((((overlay >>> 8) & 0xff) * alpha) + (((base >>> 8) & 0xff) * inverse)) / 255;
            int blue = (((overlay & 0xff) * alpha) + ((base & 0xff) * inverse)) / 255;
            rgb[index] = (red << 16) | (green << 8) | blue;
        }

        private RasterImage toImage() {
            return new RasterImage(width, height, rgb);
        }
    }

    private static int rgba(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
