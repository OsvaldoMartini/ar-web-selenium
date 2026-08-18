package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrBox;
import com.allinweb.ch.ocr.bridge.OcrResult;
import com.allinweb.ch.ocr.bridge.OcrWord;
import com.allinweb.ch.util.OcrCorrelationResult;
import com.allinweb.ch.util.OcrDomCorrelator;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import com.allinweb.ch.vision.WebPageOcrService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure OCR comparison seam for one already verified immutable Page Mappings capture.
 *
 * <p>This service deliberately has no filesystem, database, WebSocket, browser, or workspace
 * ownership access. Its caller must first verify the capture and resolve the OCR configuration,
 * then provide the exact PNG bytes, capture membership, normalized geometry, and device pixel
 * ratio. The result contains only presentation data and stable revision references; accepting an
 * OCR label remains a separate owner-scoped transaction.
 */
public final class PageMappingsOcrReviewService {

    private static final int MAX_SCREENSHOT_BYTES = 8_000_000;
    private static final long MAX_SCREENSHOT_PIXELS = 40_000_000L;
    private static final int MAX_ELEMENTS = 100_000;
    private static final int MAX_OCR_WORDS = 100_000;
    private static final int MAX_WORD_CHARACTERS = 4_096;
    private static final List<String> QUALITY_ORDER =
            List.of("EXACT_CONTAIN", "OVERLAP", "PROXIMITY", "NONE");

    /** Executes OCR and DOM correlation without reading or mutating external state. */
    public ReviewResult review(ReviewInput input) {
        if (input == null) {
            throw new IllegalArgumentException("A verified Page Mappings OCR input is required");
        }

        RasterImage image = decode(input.screenshotPng());
        List<ElementDTO> correlationElements = correlationElements(input.elements());
        List<OcrDomCorrelator.RectEntry> correlationGeometry =
                correlationGeometry(input.rectangles(), input.elements());
        OcrResult recognized = WebPageOcrService.recognizeMultiPass(image, input.ocrConfig());
        List<OcrWord> words = validatedWords(recognized == null ? null : recognized.getWords());
        OcrResult safeResult = new OcrResult("", words);
        List<OcrCorrelationResult> correlations = OcrDomCorrelator.correlate(
                correlationElements.toArray(ElementDTO[]::new),
                correlationGeometry,
                safeResult,
                input.devicePixelRatio(),
                input.ocrConfig());

        Map<String, OcrCorrelationResult> correlationByKey = new HashMap<>();
        for (OcrCorrelationResult correlation : correlations) {
            if (correlation != null && correlation.xpath != null) {
                correlationByKey.put(correlation.xpath, correlation);
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        QUALITY_ORDER.forEach(quality -> counts.put(quality, 0));
        List<ReviewRow> rows = new ArrayList<>(input.elements().size());
        for (int index = 0; index < input.elements().size(); index++) {
            ReviewElement element = input.elements().get(index);
            OcrCorrelationResult correlation = correlationByKey.get(correlationKey(index));
            String quality = normalizedQuality(
                    correlation == null ? null : correlation.matchQuality);
            counts.put(quality, counts.get(quality) + 1);
            rows.add(new ReviewRow(
                    index,
                    element.scannedElementId(),
                    element.elementHash(),
                    element.expectedLastScannedAt(),
                    element.expectedScanCount(),
                    element.definedName(),
                    element.clientNamed(),
                    quality,
                    element.tag(),
                    element.domText(),
                    correlation == null ? "" : value(correlation.ocrText),
                    element.xPath(),
                    element.iFrameXPath(),
                    correlation == null ? 0.0d : correlation.ocrConfidenceAvg));
        }
        rows.sort(Comparator.comparingInt((ReviewRow row) -> qualityRank(row.quality()))
                .thenComparingInt(ReviewRow::elementIndex));

        List<ReviewWord> reviewWords = words.stream()
                .map(PageMappingsOcrReviewService::reviewWord)
                .toList();
        return new ReviewResult(input.source(), words.size(), counts, rows, reviewWords);
    }

    private static RasterImage decode(byte[] screenshotPng) {
        try {
            requireBoundedPngDimensions(screenshotPng);
            RasterImage image = RasterImageIO.readPng(screenshotPng);
            if (image == null || image.width() <= 0 || image.height() <= 0) {
                throw new IllegalArgumentException(
                        "The verified Page Mappings screenshot is unavailable");
            }
            return image;
        } catch (IOException invalidPng) {
            throw new IllegalArgumentException(
                    "The verified Page Mappings screenshot could not be decoded", invalidPng);
        }
    }

    private static void requireBoundedPngDimensions(byte[] png) {
        if (png == null || png.length < 24) {
            throw new IllegalArgumentException(
                    "The verified Page Mappings screenshot is incomplete");
        }
        long width = unsignedInt(png, 16);
        long height = unsignedInt(png, 20);
        if (width == 0L
                || height == 0L
                || width > Integer.MAX_VALUE
                || height > Integer.MAX_VALUE
                || width > MAX_SCREENSHOT_PIXELS / height) {
            throw new IllegalArgumentException(
                    "The verified Page Mappings screenshot dimensions are too large");
        }
    }

    private static long unsignedInt(byte[] value, int offset) {
        return ((long) value[offset] & 0xffL) << 24
                | ((long) value[offset + 1] & 0xffL) << 16
                | ((long) value[offset + 2] & 0xffL) << 8
                | ((long) value[offset + 3] & 0xffL);
    }

    private static List<ElementDTO> correlationElements(List<ReviewElement> elements) {
        List<ElementDTO> result = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            ReviewElement source = elements.get(index);
            ElementDTO element = new ElementDTO();
            // A capture may legitimately contain duplicate or blank authored XPaths (for example,
            // elements in different frames). OcrDomCorrelator indexes by XPath, so an internal
            // per-capture key prevents one row from borrowing another row's rectangle. The real
            // authored XPath remains untouched in ReviewElement and ReviewRow.
            element.setXPath(correlationKey(index));
            element.setIFrameXPath(source.iFrameXPath());
            element.setTagName(source.tag());
            element.setDefinedName(source.definedName());
            element.setClientNamed(source.clientNamed());
            element.setSomeText(source.domText());
            result.add(element);
        }
        return result;
    }

    private static List<OcrDomCorrelator.RectEntry> correlationGeometry(
            List<CaptureRectangle> rectangles, List<ReviewElement> elements) {
        List<OcrDomCorrelator.RectEntry> result = new ArrayList<>(rectangles.size());
        for (CaptureRectangle source : rectangles) {
            ReviewElement element = elements.get(source.elementIndex());
            OcrDomCorrelator.RectEntry entry = new OcrDomCorrelator.RectEntry();
            entry.xpath = correlationKey(source.elementIndex());
            entry.iframeXPath = element.iFrameXPath();
            entry.found = true;
            entry.tag = element.tag();
            entry.rect = new OcrDomCorrelator.RectEntry.Rect();
            entry.rect.x = source.x();
            entry.rect.y = source.y();
            entry.rect.width = source.width();
            entry.rect.height = source.height();
            // PageMappingsWorkspaceService already normalizes a verified rectangle to the capture
            // scope. Populate both coordinate forms so a saved OCR profile cannot reinterpret that
            // immutable geometry by carrying a different screenshot.scope value.
            entry.rect.pageX = source.x();
            entry.rect.pageY = source.y();
            result.add(entry);
        }
        return result;
    }

    private static List<OcrWord> validatedWords(List<OcrWord> input) {
        if (input == null || input.isEmpty()) return List.of();
        if (input.size() > MAX_OCR_WORDS) {
            throw new IllegalStateException("The OCR result contains too many words");
        }
        List<OcrWord> result = new ArrayList<>(input.size());
        for (OcrWord word : input) {
            if (word == null || word.getBounds() == null) continue;
            String text = value(word.getText());
            if (text.length() > MAX_WORD_CHARACTERS) {
                throw new IllegalStateException("An OCR result word is too large");
            }
            OcrBox bounds = word.getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) continue;
            result.add(new OcrWord(text, bounds, word.getConfidence()));
        }
        return List.copyOf(result);
    }

    private static ReviewWord reviewWord(OcrWord word) {
        OcrBox bounds = word.getBounds();
        return new ReviewWord(
                value(word.getText()),
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                word.getConfidence());
    }

    private static String correlationKey(int elementIndex) {
        return "page-mappings-ocr-element-" + elementIndex;
    }

    private static String normalizedQuality(String quality) {
        return QUALITY_ORDER.contains(quality) ? quality : "NONE";
    }

    private static int qualityRank(String quality) {
        int rank = QUALITY_ORDER.indexOf(quality);
        return rank < 0 ? QUALITY_ORDER.size() : rank;
    }

    private static String value(String source) {
        return source == null ? "" : source;
    }

    /**
     * Explicit immutable input from an already owner- and checksum-verified capture.
     *
     * <p>{@code rectangles} must contain at most one normalized, found rectangle per element.
     * Coordinates are CSS pixels in the selected capture's own viewport/full-page scope.
     */
    public record ReviewInput(
            byte[] screenshotPng,
            List<ReviewElement> elements,
            List<CaptureRectangle> rectangles,
            double devicePixelRatio,
            OcrConfig ocrConfig,
            String source) {

        public ReviewInput {
            if (screenshotPng == null
                    || screenshotPng.length == 0
                    || screenshotPng.length > MAX_SCREENSHOT_BYTES) {
                throw new IllegalArgumentException(
                        "A bounded verified Page Mappings screenshot is required");
            }
            if (elements == null || elements.size() > MAX_ELEMENTS) {
                throw new IllegalArgumentException(
                        "The verified Page Mappings element membership is invalid");
            }
            if (rectangles == null || rectangles.size() > elements.size()) {
                throw new IllegalArgumentException(
                        "The verified Page Mappings geometry is invalid");
            }
            if (!Double.isFinite(devicePixelRatio) || devicePixelRatio <= 0.0d) {
                throw new IllegalArgumentException(
                        "A positive Page Mappings device pixel ratio is required");
            }
            screenshotPng = screenshotPng.clone();
            elements = List.copyOf(elements);
            rectangles = validatedRectangles(rectangles, elements.size());
            source = value(source);
        }

        @Override
        public byte[] screenshotPng() {
            return screenshotPng.clone();
        }

        private static List<CaptureRectangle> validatedRectangles(
                List<CaptureRectangle> rectangles, int elementCount) {
            boolean[] seen = new boolean[elementCount];
            for (CaptureRectangle rectangle : rectangles) {
                if (rectangle == null
                        || rectangle.elementIndex() < 0
                        || rectangle.elementIndex() >= elementCount
                        || seen[rectangle.elementIndex()]) {
                    throw new IllegalArgumentException(
                            "Page Mappings geometry does not match its element membership");
                }
                seen[rectangle.elementIndex()] = true;
            }
            return List.copyOf(rectangles);
        }
    }

    /** One immutable capture element plus its current authoritative registry revision. */
    public record ReviewElement(
            long scannedElementId,
            String elementHash,
            String expectedLastScannedAt,
            int expectedScanCount,
            String definedName,
            String clientNamed,
            String tag,
            String domText,
            String xPath,
            String iFrameXPath) {

        public ReviewElement {
            if (scannedElementId < 0 || expectedScanCount < 0) {
                throw new IllegalArgumentException(
                        "A Page Mappings OCR element revision is invalid");
            }
            elementHash = value(elementHash);
            expectedLastScannedAt = value(expectedLastScannedAt);
            definedName = value(definedName);
            clientNamed = clientNamed == null ? null : clientNamed.trim();
            tag = value(tag);
            domText = value(domText);
            xPath = value(xPath);
            iFrameXPath = value(iFrameXPath);
        }

        /** Only registry-enriched rows may later participate in an alias mutation. */
        public boolean persistable() {
            return scannedElementId > 0
                    && expectedScanCount > 0
                    && !elementHash.isBlank()
                    && !expectedLastScannedAt.isBlank();
        }
    }

    /** One verified capture rectangle already normalized to the screenshot's coordinate scope. */
    public record CaptureRectangle(
            int elementIndex, double x, double y, double width, double height) {

        public CaptureRectangle {
            if (elementIndex < 0
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(width)
                    || !Double.isFinite(height)
                    || width <= 0.0d
                    || height <= 0.0d) {
                throw new IllegalArgumentException(
                        "A Page Mappings OCR rectangle is invalid");
            }
        }
    }

    /** One OCR Review row. Identity/revision fields are never derived from OCR output. */
    public record ReviewRow(
            int elementIndex,
            long scannedElementId,
            String elementHash,
            String expectedLastScannedAt,
            int expectedScanCount,
            String definedName,
            String clientNamed,
            String quality,
            String tag,
            String domText,
            String ocrText,
            String xPath,
            String iFrameXPath,
            double confidence) {

        public ReviewRow {
            elementHash = value(elementHash);
            expectedLastScannedAt = value(expectedLastScannedAt);
            definedName = value(definedName);
            tag = value(tag);
            domText = value(domText);
            ocrText = value(ocrText);
            xPath = value(xPath);
            iFrameXPath = value(iFrameXPath);
            quality = normalizedQuality(quality);
            if (!Double.isFinite(confidence)) confidence = 0.0d;
        }

        public boolean persistable() {
            return scannedElementId > 0
                    && expectedScanCount > 0
                    && !elementHash.isBlank()
                    && !expectedLastScannedAt.isBlank();
        }
    }

    /** OCR word geometry is expressed in screenshot image pixels for React overlay rendering. */
    public record ReviewWord(
            String text, int x, int y, int width, int height, float confidence) {

        public ReviewWord {
            text = value(text);
        }
    }

    /** Immutable presentation result; no alias or scanner state has been changed. */
    public record ReviewResult(
            String source,
            int wordCount,
            Map<String, Integer> counts,
            List<ReviewRow> rows,
            List<ReviewWord> words) {

        public ReviewResult {
            source = value(source);
            counts = counts == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
            rows = rows == null ? List.of() : List.copyOf(rows);
            words = words == null ? List.of() : List.copyOf(words);
        }
    }
}
