package com.allinweb.ch.util;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.facade.OcrConfigService;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrBridgeService;
import com.allinweb.ch.ocr.bridge.OcrResult;
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
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

/**
 * Orchestrator for the OCR pipeline. Consumes the artifacts that already
 * live in {@code PATH_DB/page_diagnostics/}:
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
 *
 * <p>OCR is performed by {@link OcrBridgeService} (native ar_ocr.dll).
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
            OcrConfig cfg = OcrConfigService.getInstance().resolveFor(homebankingId, homeUrlId);
            String scope = cfg == null ? "viewport" : cfg.getString("screenshot", "scope", "viewport");
            byte[] png = "full_page".equalsIgnoreCase(scope)
                    ? WebScreenshotCapture.fullPageBytes(driver)
                    : WebScreenshotCapture.viewportBytes(driver);
            runPipeline(png, elements, pathDb, prefix, cfg, homebankingId, homeUrlId);
        } catch (Exception ex) {
            log.warn("PageOcrDumper.runAndDump failed: {}", ex.getMessage(), ex);
        }
    }

    /** Playwright equivalent — captures via the single Playwright browser (no Selenium driver). */
    public static void runAndDump(
            ARPlaywrightDriver pw,
            ElementDTO[] elements,
            String pathDb,
            String prefix,
            Integer homebankingId,
            Integer homeUrlId) {
        if (pw == null || pathDb == null || prefix == null) {
            log.warn("PageOcrDumper(pw) skipped — driver/pathDb/prefix is null");
            return;
        }
        try {
            OcrConfig cfg = OcrConfigService.getInstance().resolveFor(homebankingId, homeUrlId);
            String scope = cfg == null ? "viewport" : cfg.getString("screenshot", "scope", "viewport");
            byte[] png = "full_page".equalsIgnoreCase(scope)
                    ? WebScreenshotCapture.fullPageBytes(pw)
                    : WebScreenshotCapture.viewportBytes(pw);
            runPipeline(png, elements, pathDb, prefix, cfg, homebankingId, homeUrlId);
        } catch (Exception ex) {
            log.warn("PageOcrDumper.runAndDump(pw) failed: {}", ex.getMessage(), ex);
        }
    }

    /** Shared OCR pipeline: persist screenshot → OCR → correlate DOM rects → write files. */
    private static void runPipeline(
            byte[] png,
            ElementDTO[] elements,
            String pathDb,
            String prefix,
            OcrConfig cfg,
            Integer homebankingId,
            Integer homeUrlId) {
        try {
            Path diagDir = Paths.get(pathDb, PageDiagnosticDumper.SUBFOLDER);
            Files.createDirectories(diagDir);

            Path pngPath = diagDir.resolve(prefix + ".png");
            Files.write(pngPath, png);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                log.warn("Screenshot bytes did not decode to an image — aborting OCR pipeline");
                return;
            }

            // 2. OCR (multi-pass: raw + optional CLAHE + optional button detection)
            //    via native ar_ocr.dll.
            OcrResult ocr = OcrBridgeService.recognizeMultiPass(image, cfg);

            // 3. DPR from meta.json (default 1.0)
            double dpr = readDprFromMeta(diagDir.resolve(prefix + "-meta.json"));

            // 4. Persist raw OCR dump
            writeOcrDump(diagDir.resolve("ocr-HP.json"), ocr, dpr, image.getWidth(), image.getHeight());

            // 5. Read DOM rects (written by PageDiagnosticDumper.dumpRects earlier in the scan)
            List<OcrDomCorrelator.RectEntry> rects = readRects(diagDir.resolve(prefix + "-rects.json"));

            // 6. Correlate (with per-tag thresholds from config)
            List<OcrCorrelationResult> correlation = OcrDomCorrelator.correlate(elements, rects, ocr, dpr, cfg);

            // 7. Persist correlation
            Files.writeString(
                    diagDir.resolve("ocr-correlation-HP.json"), GSON.toJson(correlation), StandardCharsets.UTF_8);

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
            var bounds = w.getBounds();
            if (bounds == null) return;
            JsonObject pxBox = new JsonObject();
            pxBox.addProperty("x", bounds.x());
            pxBox.addProperty("y", bounds.y());
            pxBox.addProperty("width", bounds.width());
            pxBox.addProperty("height", bounds.height());
            wobj.add("pxBox", pxBox);
            JsonObject cssBox = new JsonObject();
            cssBox.addProperty("x", bounds.x() / dpr);
            cssBox.addProperty("y", bounds.y() / dpr);
            cssBox.addProperty("width", bounds.width() / dpr);
            cssBox.addProperty("height", bounds.height() / dpr);
            wobj.add("cssBox", cssBox);
            words.add(wobj);
        });
        dump.add("words", words);

        Files.writeString(out, GSON.toJson(dump), StandardCharsets.UTF_8);
    }
}
