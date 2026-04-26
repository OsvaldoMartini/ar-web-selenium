package com.allinweb.ch.vision;

import com.allinweb.ch.facade.OcrConfigService;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.allinweb.ch.vision.ocr.OcrOpenCvUtils;
import com.allinweb.ch.vision.ocr.OcrResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.openqa.selenium.WebDriver;

/**
 * Orchestrator for the Roadmap 2 OCR pipeline. Consumes the Roadmap 1 artifacts
 * that already live in {@code PATH_DB/page_diagnostics/}:
 * <ul>
 *   <li>{@code page-HP-rects.json} — DOM bounding rects per xPath</li>
 *   <li>{@code page-HP-meta.json}  — viewport, DPR, scroll</li>
 * </ul>
 *
 * <p>Produces:
 * <ul>
 *   <li>{@code page-HP.png}               viewport screenshot</li>
 *   <li>{@code ocr-HP.json}               full OCR word dump</li>
 *   <li>{@code ocr-correlation-HP.json}   per-element DOM↔OCR correlation</li>
 * </ul>
 */
@Slf4j
public final class PageOcrDumper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger logScanner = org.slf4j.LoggerFactory.getLogger("com.allinweb.scanner");

    private PageOcrDumper() {}

    /** Full pipeline: screenshot → OCR → correlate → write files. */
    public static void runAndDump(WebDriver driver, ElementDTO[] elements, String pathDb, String prefix) {
        runAndDump(driver, elements, pathDb, prefix, null, null);
    }

    /**
     * Full pipeline honouring an {@link OcrConfig} when provided.
     * If cfg is null and (homebankingId, homeUrlId) are both null, falls back to the global default profile.
     */
    public static void runAndDump(
            WebDriver driver,
            ElementDTO[] elements,
            String pathDb,
            String prefix,
            Integer homebankingId,
            Integer homeUrlId) {
        if (driver == null || pathDb == null || prefix == null) {
            log.warn("PageOcrDumper skipped — driver/pathDb/prefix is null");
            return;
        }
        try {
            Path diagDir = Paths.get(pathDb, PageDiagnosticDumper.SUBFOLDER);
            Files.createDirectories(diagDir);

            // 0. Resolve OCR config for this scope
            OcrConfig cfg = OcrConfigService.getInstance().resolveFor(homebankingId, homeUrlId);

            // 1. Viewport screenshot
            byte[] png = WebScreenshotCapture.viewportBytes(driver);
            Path pngPath = diagDir.resolve(prefix + ".png");
            Files.write(pngPath, png);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                log.warn("Screenshot bytes did not decode to an image — aborting OCR pipeline");
                return;
            }

            // 2. Optional ColorMapper pre-OCR pass (configured)
            BufferedImage ocrInput = image;
            List<Map<String, Object>> colorOps =
                    cfg == null ? Collections.emptyList() : cfg.getJsonArray("color_mapping", "ops");
            if (!colorOps.isEmpty()) {
                ocrInput = applyColorMapping(image, colorOps, diagDir.resolve(prefix + "-colormapped.png"));
            }

            // 3. OCR (multi-pass: raw + optional CLAHE + optional button detection)
            OcrResult ocr = WebPageOcrService.recognizeMultiPass(ocrInput, cfg);

            // 4. DPR from meta.json (default 1.0)
            double dpr = readDprFromMeta(diagDir.resolve(prefix + "-meta.json"));

            // 5. Persist raw OCR dump
            writeOcrDump(diagDir.resolve("ocr-HP.json"), ocr, dpr, image.getWidth(), image.getHeight());

            // 6. Read DOM rects (written by PageDiagnosticDumper.dumpRects earlier in SEARCH_TOOL)
            List<OcrDomCorrelator.RectEntry> rects = readRects(diagDir.resolve(prefix + "-rects.json"));

            // 7. Correlate (with per-tag thresholds from config)
            List<OcrCorrelationResult> correlation = OcrDomCorrelator.correlate(elements, rects, ocr, dpr, cfg);

            // 8. Persist correlation
            Files.writeString(
                    diagDir.resolve("ocr-correlation-HP.json"), GSON.toJson(correlation), StandardCharsets.UTF_8);

            // 9. Optional annotated debug PNG
            boolean annotate = cfg != null && cfg.getBool("output", "save_annotated_png", false);
            if (annotate) {
                AnnotatedImageRenderer.render(
                        image, ocr.getWords(), correlation, dpr, diagDir.resolve(prefix + "-annotated.png"));
            }

            String profileName = cfg == null || cfg.getProfile() == null
                    ? "<none>"
                    : cfg.getProfile().getName();
            int exact = 0, overlap = 0, prox = 0, none = 0;
            for (OcrCorrelationResult r : correlation) {
                switch (r.matchQuality == null ? "NONE" : r.matchQuality) {
                    case "EXACT_CONTAIN":
                        exact++;
                        break;
                    case "OVERLAP":
                        overlap++;
                        break;
                    case "PROXIMITY":
                        prox++;
                        break;
                    default:
                        none++;
                        break;
                }
            }
            log.info(
                    "OCR pipeline done — profile={} words={} elements={} correlations={}",
                    profileName,
                    ocr.getWords().size(),
                    elements == null ? 0 : elements.length,
                    correlation.size());
            logScanner.info(
                    "PIPELINE — profile={} hbId={} homeUrlId={} words={} elements={} corr={} (EXACT={} OVERLAP={} PROX={} NONE={})",
                    profileName,
                    homebankingId,
                    homeUrlId,
                    ocr.getWords().size(),
                    elements == null ? 0 : elements.length,
                    correlation.size(),
                    exact,
                    overlap,
                    prox,
                    none);
        } catch (Exception ex) {
            log.warn("PageOcrDumper.runAndDump failed: {}", ex.getMessage(), ex);
        }
    }

    /** Apply ColorMapper ops to the image and optionally persist the mapped result for debugging. */
    private static BufferedImage applyColorMapping(
            BufferedImage original, List<Map<String, Object>> ops, Path debugPngPath) {
        Mat src = null;
        Mat mapped = null;
        try {
            // Trigger OpenCV native load lazily (ColorMapper uses OpenCV).
            Class.forName("com.allinweb.ch.vision.ocr.OpenCvNativeLoader");
            src = OcrOpenCvUtils.bufferedImageToMat(original);
            mapped = ColorMapper.apply(src, ops);
            BufferedImage out = OcrOpenCvUtils.matToBufferedImage(mapped);
            try {
                ImageIO.write(out, "png", debugPngPath.toFile());
            } catch (IOException ignore) {
                // Non-fatal.
            }
            return out;
        } catch (Throwable t) {
            log.warn("ColorMapper pass failed, falling back to raw screenshot: {}", t.getMessage());
            return original;
        } finally {
            if (mapped != null && mapped != src) mapped.release();
            if (src != null) src.release();
        }
    }

    private static double readDprFromMeta(Path metaPath) {
        if (!Files.exists(metaPath)) return 1.0;
        try (Reader r = Files.newBufferedReader(metaPath, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("devicePixelRatio") && !obj.get("devicePixelRatio").isJsonNull()) {
                    return obj.get("devicePixelRatio").getAsDouble();
                }
            }
        } catch (Exception e) {
            log.debug("Could not read DPR from {}: {}", metaPath, e.getMessage());
        }
        return 1.0;
    }

    private static List<OcrDomCorrelator.RectEntry> readRects(Path rectsPath) {
        if (!Files.exists(rectsPath)) {
            log.debug("Rects file missing: {}", rectsPath);
            return Collections.emptyList();
        }
        try (Reader r = Files.newBufferedReader(rectsPath, StandardCharsets.UTF_8)) {
            OcrDomCorrelator.RectEntry[] arr = GSON.fromJson(r, OcrDomCorrelator.RectEntry[].class);
            if (arr == null) return Collections.emptyList();
            List<OcrDomCorrelator.RectEntry> out = new ArrayList<>(arr.length);
            Collections.addAll(out, arr);
            return out;
        } catch (Exception e) {
            log.debug("Could not read rects from {}: {}", rectsPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static void writeOcrDump(Path out, OcrResult ocr, double dpr, int imgW, int imgH) throws IOException {
        JsonObject dump = new JsonObject();
        dump.addProperty("imageWidth", imgW);
        dump.addProperty("imageHeight", imgH);
        dump.addProperty("devicePixelRatio", dpr);
        dump.addProperty("wordCount", ocr.getWords().size());
        dump.addProperty("fullText", ocr.getFullText());

        JsonArray words = new JsonArray();
        ocr.getWords().forEach(w -> {
            JsonObject wobj = new JsonObject();
            wobj.addProperty("text", w.getText());
            wobj.addProperty("confidence", w.getConfidence());
            // Raw image px
            JsonObject pxBox = new JsonObject();
            pxBox.addProperty("x", w.getBounds().x);
            pxBox.addProperty("y", w.getBounds().y);
            pxBox.addProperty("width", w.getBounds().width);
            pxBox.addProperty("height", w.getBounds().height);
            wobj.add("pxBox", pxBox);
            // CSS px (divided by DPR)
            JsonObject cssBox = new JsonObject();
            cssBox.addProperty("x", w.getBounds().x / dpr);
            cssBox.addProperty("y", w.getBounds().y / dpr);
            cssBox.addProperty("width", w.getBounds().width / dpr);
            cssBox.addProperty("height", w.getBounds().height / dpr);
            wobj.add("cssBox", cssBox);
            words.add(wobj);
        });
        dump.add("words", words);

        Files.writeString(out, GSON.toJson(dump), StandardCharsets.UTF_8);
    }
}
