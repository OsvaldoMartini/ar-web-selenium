package com.allinweb.ch.ocr.bridge;

import com.allinweb.ch.vision.WebPageOcrService;
import com.allinweb.ch.vision.ocr.OcrResult;
import com.allinweb.ch.vision.ocr.OcrWord;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Word;

/**
 * Phase 7 side-by-side parity harness. Runs the Java (Tess4J) and native
 * (ar_ocr.dll via {@link OcrBridgeService}) engines on the same image and
 * reports the diff so the cutover risk can be eyeballed before flipping
 * {@link OcrEngine#isNative()} to {@code true} as the default.
 *
 * <p>Run from your IDE (right-click → Run main) or from Maven:
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.allinweb.ch.ocr.bridge.OcrParityTest
 *   mvn -q exec:java -Dexec.mainClass=com.allinweb.ch.ocr.bridge.OcrParityTest \
 *       -Dexec.args="C:/path/to/some.png"
 * </pre>
 *
 * <p>Default fixture is {@code D:\Projects\ARWeb-Martini\ARWeb\page_diagnostics\page-HP.png}.
 *
 * <p>This class deliberately bypasses {@link WebPageOcrService#recognize}'s
 * native-engine delegation so it always exercises both engines, regardless
 * of the {@code ocr_engine} value in {@code ARWeb.config}.
 */
public class OcrParityTest {

    private static final double IOU_THRESHOLD = 0.5;
    private static final String DEFAULT_FIXTURE =
            "D:\\Projects\\ARWeb-Martini\\ARWeb\\page_diagnostics\\page-HP.png";

    public static void main(String[] args) throws IOException {
        File fixture = new File(args.length > 0 ? args[0] : DEFAULT_FIXTURE);
        if (!fixture.isFile()) {
            System.err.println("Fixture not found: " + fixture);
            System.err.println("Pass a path as argv[0] or place a PNG at the default location.");
            System.exit(1);
        }
        BufferedImage image = ImageIO.read(fixture);
        if (image == null) {
            System.err.println("Could not decode: " + fixture);
            System.exit(2);
        }

        System.out.println("Fixture: " + fixture);
        System.out.printf("Image  : %dx%d%n%n", image.getWidth(), image.getHeight());

        // Native first — also warms up JNA/DLL load before Java's run.
        long t0 = System.nanoTime();
        OcrResult nat = OcrBridgeService.recognize(image, null);
        long nativeMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        OcrResult java = forceJava(image);
        long javaMs = (System.nanoTime() - t1) / 1_000_000;

        report(java, nat, javaMs, nativeMs);
    }

    /**
     * Inline copy of WebPageOcrService.recognize that always uses Tess4J,
     * never delegates to the native bridge. Keeps the harness self-contained
     * — flipping the OCR_ENGINE config does not affect this method.
     */
    private static OcrResult forceJava(BufferedImage image) throws IOException {
        ITesseract tess = WebPageOcrService.createEngine(null);
        List<Word> words = tess.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        List<OcrWord> out = new ArrayList<>();
        StringBuilder full = new StringBuilder();
        for (Word w : words) {
            String text = w.getText() == null ? "" : w.getText().trim();
            if (text.isEmpty()) continue;
            out.add(new OcrWord(text, w.getBoundingBox(), w.getConfidence()));
            full.append(text).append(' ');
        }
        return new OcrResult(full.toString().trim(), out);
    }

    private static void report(OcrResult java, OcrResult nat, long jms, long nms) {
        System.out.println("=== Top-level ===");
        System.out.printf("  Java   : %4d words   %5d chars   %4d ms%n",
                java.getWords().size(), java.getFullText().length(), jms);
        System.out.printf("  Native : %4d words   %5d chars   %4d ms%n",
                nat.getWords().size(), nat.getFullText().length(), nms);
        double speedup = jms == 0 ? 0 : (double) jms / Math.max(1, nms);
        System.out.printf("  Native is %.2fx %s than Java%n%n",
                speedup, speedup >= 1.0 ? "faster" : "slower");

        // For each Java word, find the best-overlapping native word (IoU > threshold).
        int matched = 0;
        int textMismatch = 0;
        double absConfSum = 0;
        boolean[] natUsed = new boolean[nat.getWords().size()];
        List<String> sampleMismatches = new ArrayList<>();
        for (OcrWord jw : java.getWords()) {
            int idx = bestMatchIndex(jw, nat.getWords(), natUsed);
            if (idx < 0) continue;
            natUsed[idx] = true;
            matched++;
            OcrWord nw = nat.getWords().get(idx);
            absConfSum += Math.abs(jw.getConfidence() - nw.getConfidence());
            if (!jw.getText().equals(nw.getText())) {
                textMismatch++;
                if (sampleMismatches.size() < 10) {
                    sampleMismatches.add(String.format("    java='%s'  native='%s'  bbox=(%d,%d,%d,%d)",
                            jw.getText(), nw.getText(),
                            jw.getBounds().x, jw.getBounds().y,
                            jw.getBounds().width, jw.getBounds().height));
                }
            }
        }

        int javaOnly = java.getWords().size() - matched;
        int nativeOnly = 0;
        for (boolean used : natUsed) if (!used) nativeOnly++;

        System.out.println("=== Per-word match (IoU > " + IOU_THRESHOLD + ") ===");
        System.out.printf("  Matched              : %d%n", matched);
        System.out.printf("  In Java only         : %d%n", javaOnly);
        System.out.printf("  In Native only       : %d%n", nativeOnly);
        System.out.printf("  Text differs (matched): %d%n", textMismatch);
        if (matched > 0) {
            System.out.printf("  Avg |conf delta|     : %.2f%n", absConfSum / matched);
        }

        if (!sampleMismatches.isEmpty()) {
            System.out.println();
            System.out.println("Sample text mismatches (up to 10):");
            for (String s : sampleMismatches) System.out.println(s);
        }

        if (javaOnly > 0) {
            System.out.println();
            System.out.println("Sample words present only in Java (up to 10):");
            int shown = 0;
            for (OcrWord jw : java.getWords()) {
                if (shown >= 10) break;
                if (bestMatchIndex(jw, nat.getWords(), new boolean[nat.getWords().size()]) < 0) {
                    System.out.printf("    '%s' bbox=(%d,%d,%d,%d) conf=%.1f%n",
                            jw.getText(),
                            jw.getBounds().x, jw.getBounds().y,
                            jw.getBounds().width, jw.getBounds().height,
                            jw.getConfidence());
                    shown++;
                }
            }
        }

        if (nativeOnly > 0) {
            System.out.println();
            System.out.println("Sample words present only in Native (up to 10):");
            int shown = 0;
            boolean[] none = new boolean[java.getWords().size()];
            for (OcrWord nw : nat.getWords()) {
                if (shown >= 10) break;
                if (bestMatchIndex(nw, java.getWords(), none) < 0) {
                    System.out.printf("    '%s' bbox=(%d,%d,%d,%d) conf=%.1f%n",
                            nw.getText(),
                            nw.getBounds().x, nw.getBounds().y,
                            nw.getBounds().width, nw.getBounds().height,
                            nw.getConfidence());
                    shown++;
                }
            }
        }
    }

    private static int bestMatchIndex(OcrWord query, List<OcrWord> pool, boolean[] used) {
        int bestIdx = -1;
        double bestIou = IOU_THRESHOLD;
        for (int i = 0; i < pool.size(); i++) {
            if (used[i]) continue;
            double iou = iou(query.getBounds(), pool.get(i).getBounds());
            if (iou > bestIou) {
                bestIou = iou;
                bestIdx = i;
            }
        }
        return bestIdx;
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
