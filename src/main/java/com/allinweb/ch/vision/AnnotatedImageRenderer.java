package com.allinweb.ch.vision;

import com.allinweb.ch.ocr.bridge.OcrBox;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.util.OcrCorrelationResult;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    private static final Color OCR_GREEN = new Color(0, 200, 0, 160);
    private static final Color DOM_RED = new Color(255, 0, 0, 160);
    private static final Color MATCH_GREEN = new Color(0, 180, 0, 230);

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
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(srcImg, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. OCR word boxes
            if (ocrWords != null) {
                g.setStroke(new BasicStroke(1f));
                g.setColor(OCR_GREEN);
                for (OcrWord w : ocrWords) {
                    OcrBox b = w.getBounds();
                    if (b == null) continue;
                    g.drawRect(b.x(), b.y(), b.width(), b.height());
                }
            }

            // 2. DOM element rects
            if (correlations != null) {
                g.setStroke(new BasicStroke(1.5f));
                g.setColor(DOM_RED);
                for (OcrCorrelationResult cr : correlations) {
                    if (cr == null || cr.domRect == null) continue;
                    int x = (int) Math.round(cr.domRect.x * dpr);
                    int y = (int) Math.round(cr.domRect.y * dpr);
                    int w = (int) Math.round(cr.domRect.width * dpr);
                    int h = (int) Math.round(cr.domRect.height * dpr);
                    g.drawRect(x, y, w, h);
                }
            }

            // 3. EXACT_CONTAIN highlight
            if (correlations != null) {
                g.setStroke(new BasicStroke(3f));
                g.setColor(MATCH_GREEN);
                for (OcrCorrelationResult cr : correlations) {
                    if (cr == null || cr.domRect == null || !"EXACT_CONTAIN".equals(cr.matchQuality)) continue;
                    int x = (int) Math.round(cr.domRect.x * dpr);
                    int y = (int) Math.round(cr.domRect.y * dpr);
                    int w = (int) Math.round(cr.domRect.width * dpr);
                    int h = (int) Math.round(cr.domRect.height * dpr);
                    g.drawRect(x, y, w, h);
                }
            }
        } finally {
            g.dispose();
        }

        try {
            ImageIO.write(out, "png", outPath.toFile());
            log.info("Annotated image written to {}", outPath);
        } catch (IOException e) {
            log.warn("Could not write annotated PNG {}: {}", outPath, e.getMessage());
        }
    }
}
