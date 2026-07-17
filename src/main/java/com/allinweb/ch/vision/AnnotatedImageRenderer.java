package com.allinweb.ch.vision;

import com.allinweb.ch.ocr.bridge.OcrBox;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.util.OcrCorrelationResult;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
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
            BufferedImage srcImg,
            List<OcrWord> ocrWords,
            List<OcrCorrelationResult> correlations,
            double dpr,
            Path outPath) {
        if (srcImg == null || outPath == null) return;
        if (dpr <= 0) dpr = 1.0;

        BufferedImage out = new BufferedImage(srcImg.getWidth(), srcImg.getHeight(), BufferedImage.TYPE_INT_RGB);
        copyPixels(srcImg, out);

        // 1. OCR word boxes
        if (ocrWords != null) {
            for (OcrWord w : ocrWords) {
                OcrBox b = w.getBounds();
                if (b == null) continue;
                drawRect(out, b.x(), b.y(), b.width(), b.height(), OCR_GREEN, 1);
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
                drawRect(out, x, y, w, h, DOM_RED, 2);
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
                drawRect(out, x, y, w, h, MATCH_GREEN, 3);
            }
        }

        try {
            ImageIO.write(out, "png", outPath.toFile());
            log.info("Annotated image written to {}", outPath);
        } catch (IOException e) {
            log.warn("Could not write annotated PNG {}: {}", outPath, e.getMessage());
        }
    }

    private static void copyPixels(BufferedImage source, BufferedImage target) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                target.setRGB(x, y, source.getRGB(x, y));
            }
        }
    }

    private static void drawRect(BufferedImage image, int x, int y, int width, int height, int color, int thickness) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;
        for (int i = 0; i < thickness; i++) {
            int left = x + i;
            int top = y + i;
            int right = x + width - 1 - i;
            int bottom = y + height - 1 - i;
            drawHorizontal(image, left, right, top, color);
            drawHorizontal(image, left, right, bottom, color);
            drawVertical(image, top, bottom, left, color);
            drawVertical(image, top, bottom, right, color);
        }
    }

    private static void drawHorizontal(BufferedImage image, int left, int right, int y, int color) {
        if (y < 0 || y >= image.getHeight()) return;
        int start = Math.max(0, left);
        int end = Math.min(image.getWidth() - 1, right);
        for (int x = start; x <= end; x++) {
            blendPixel(image, x, y, color);
        }
    }

    private static void drawVertical(BufferedImage image, int top, int bottom, int x, int color) {
        if (x < 0 || x >= image.getWidth()) return;
        int start = Math.max(0, top);
        int end = Math.min(image.getHeight() - 1, bottom);
        for (int y = start; y <= end; y++) {
            blendPixel(image, x, y, color);
        }
    }

    private static void blendPixel(BufferedImage image, int x, int y, int overlay) {
        int base = image.getRGB(x, y);
        int alpha = (overlay >>> 24) & 0xff;
        int inverse = 255 - alpha;
        int red = ((((overlay >>> 16) & 0xff) * alpha) + (((base >>> 16) & 0xff) * inverse)) / 255;
        int green = ((((overlay >>> 8) & 0xff) * alpha) + (((base >>> 8) & 0xff) * inverse)) / 255;
        int blue = (((overlay & 0xff) * alpha) + ((base & 0xff) * inverse)) / 255;
        image.setRGB(x, y, (red << 16) | (green << 8) | blue);
    }

    private static int rgba(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
