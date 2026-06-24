package com.allinweb.ch.facade;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.util.OcrCorrelationResult;
import com.allinweb.ch.util.TextSimilarity;
import com.google.gson.Gson;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Roadmap 3 Phase 3a — best-text resolver.
 *
 * <p>For every {@link ElementDTO} in a pick batch, gathers candidate texts from
 * (in priority order):
 * <ol>
 *   <li>Sibling {@code <label for=this.id>} resolved within the same batch</li>
 *   <li>{@code aria-label}, {@code aria-labelledby} target text within the batch</li>
 *   <li>The element's own {@code someText} as populated by the JS scanner</li>
 *   <li>{@code placeholder}, {@code title}, {@code alt}</li>
 *   <li>Humanized {@code id} / {@code name}</li>
 *   <li>OCR EXACT_CONTAIN / OVERLAP / PROXIMITY (from {@code ocr-correlation-HP.json})</li>
 * </ol>
 * Sources 6 (preceding visible text node) and 9 (ancestor card title) from the spec are
 * intentionally deferred — the JS scanner doesn't surface them and they're rarely the strongest
 * signal anyway.
 *
 * <p>OCR corroboration: when an OCR text fuzzy-matches a non-OCR candidate, that candidate's
 * score is multiplied by 1.5 (Roadmap 2 reminder rule).
 *
 * <p>Mutates each DTO in place: writes {@link ElementDTO#setSomeText(String)} and
 * {@link ElementDTO#setDefinedName(String)}. Existing values are overwritten when a
 * stronger candidate is found; if every candidate is empty the DTO is left untouched.
 */
@Slf4j
public final class ElementTextResolver {

    private static final Gson GSON = new Gson();

    /** Fuzzy threshold above which OCR corroborates a DOM-derived candidate. */
    private static final double FUZZY_THRESHOLD = 0.85;

    /** Score multiplier applied to a DOM candidate when OCR agrees with it. */
    private static final double OCR_CORROBORATION_MULTIPLIER = 1.5;

    private ElementTextResolver() {}

    /**
     * Legacy entry: resolve with hardcoded weights. Equivalent to {@code resolveAll(elements, file, null)}.
     */
    public static void resolveAll(ElementDTO[] elements, Path ocrCorrelationFile) {
        resolveAll(elements, ocrCorrelationFile, null);
    }

    /**
     * Resolve {@code someText} + {@code definedName} for every element in the batch.
     * When {@code cfg} is non-null, OCR candidate weights come from the
     * {@code correlation.ocr_*_weight} params (see {@code OcrConfigDefaults}); the
     * "DOM-First (Anti-Drift)" profile lowers them so DOM text wins when both exist.
     * When {@code cfg} is null, the hardcoded defaults (0.85 / 0.70 / 0.55) apply.
     */
    public static void resolveAll(ElementDTO[] elements, Path ocrCorrelationFile, OcrConfig cfg) {
        if (elements == null || elements.length == 0) return;

        double exactWeight = cfg == null ? 0.85 : cfg.getDouble("correlation", "ocr_exact_contain_weight", 0.85);
        double overlapWeight = cfg == null ? 0.70 : cfg.getDouble("correlation", "ocr_overlap_weight", 0.70);
        double proxWeight = cfg == null ? 0.55 : cfg.getDouble("correlation", "ocr_proximity_weight", 0.55);

        Map<String, OcrCorrelationResult> ocrByXPath = readOcrByXPath(ocrCorrelationFile);

        // Pass 1 — index by id and pre-compute label[for=id] map within this batch.
        Map<String, ElementDTO> elementsById = new HashMap<>();
        Map<String, ElementDTO> labelsForId = new HashMap<>();
        for (ElementDTO e : elements) {
            if (e == null) continue;
            String id = e.getAttribId();
            if (id == null || id.isBlank()) id = getAttribute(e, "id");
            if (id != null && !id.isBlank()) elementsById.put(id, e);
            if ("label".equalsIgnoreCase(e.getTagName())) {
                String forAttr = getAttribute(e, "for");
                if (forAttr != null && !forAttr.isBlank()) labelsForId.put(forAttr.trim(), e);
            }
        }

        // Pass 2 — score candidates per element and assign someText / definedName.
        Set<String> usedNames = new HashSet<>();
        for (ElementDTO e : elements) {
            if (e == null) continue;
            try {
                List<TextCandidate> cands =
                        gather(e, elementsById, labelsForId, ocrByXPath, exactWeight, overlapWeight, proxWeight);
                applyOcrCorroboration(cands, ocrByXPath.get(e.getXPath()));
                cands.sort((a, b) -> Double.compare(b.score, a.score));

                String resolved = "";
                String resolvedSource = null;
                for (TextCandidate c : cands) {
                    String cleaned = clean(c.text);
                    if (!cleaned.isBlank()) {
                        resolved = cleaned;
                        resolvedSource = c.source;
                        break;
                    }
                }

                if (!resolved.isBlank()) {
                    e.setSomeText(resolved);
                }

                String slug = TextSimilarity.slug(resolved.isBlank() ? defaultBaseFor(e) : resolved);
                String unique = TextSimilarity.uniquify(slug, usedNames);
                usedNames.add(unique);
                e.setDefinedName(unique);
                if (!unique.equals(slug) && !resolved.isBlank()) {
                    e.setSomeText(resolved + unique.substring(slug.length()));
                }

                if (log.isDebugEnabled() && resolvedSource != null) {
                    log.debug(
                            "ElementTextResolver — xpath={} → someText='{}' (source={}) definedName='{}'",
                            e.getXPath(),
                            resolved,
                            resolvedSource,
                            unique);
                }
            } catch (Exception ex) {
                log.warn("ElementTextResolver failed for xpath={}: {}", e.getXPath(), ex.getMessage());
            }
        }
    }

    // ── Candidate gathering ──────────────────────────────────────────────

    private static List<TextCandidate> gather(
            ElementDTO e,
            Map<String, ElementDTO> elementsById,
            Map<String, ElementDTO> labelsForId,
            Map<String, OcrCorrelationResult> ocrByXPath,
            double ocrExactWeight,
            double ocrOverlapWeight,
            double ocrProxWeight) {
        List<TextCandidate> out = new ArrayList<>();

        // S1 — <label for=this.id>
        String myId = e.getAttribId();
        if (myId == null || myId.isBlank()) myId = getAttribute(e, "id");
        if (myId != null && !myId.isBlank()) {
            ElementDTO label = labelsForId.get(myId);
            if (label != null
                    && label.getSomeText() != null
                    && !label.getSomeText().isBlank()) {
                out.add(new TextCandidate(label.getSomeText(), 1.00, "label[for]"));
            }
        }

        // S2a — aria-label
        String ariaLabel = getAttribute(e, "aria-label");
        if (ariaLabel != null && !ariaLabel.isBlank()) {
            out.add(new TextCandidate(ariaLabel, 1.00, "aria-label"));
        }

        // S2b — aria-labelledby → target's text
        String ariaLabelledBy = getAttribute(e, "aria-labelledby");
        if (ariaLabelledBy != null && !ariaLabelledBy.isBlank()) {
            ElementDTO target = elementsById.get(ariaLabelledBy.trim());
            if (target != null
                    && target.getSomeText() != null
                    && !target.getSomeText().isBlank()) {
                out.add(new TextCandidate(target.getSomeText(), 0.95, "aria-labelledby"));
            }
        }

        // S3 — scanner-provided someText (medium weight; defers to label / aria when present)
        if (e.getSomeText() != null && !e.getSomeText().isBlank()) {
            out.add(new TextCandidate(e.getSomeText(), 0.40, "scanner.someText"));
        }

        // S4 — placeholder / title / alt
        addAttrCandidate(out, e, "placeholder", 0.65);
        addAttrCandidate(out, e, "title", 0.60);
        addAttrCandidate(out, e, "alt", 0.60);
        addAttrCandidate(out, e, "value", 0.45);

        // S5 — humanized id / name
        if (myId != null && !myId.isBlank()) {
            String h = TextSimilarity.humanize(myId);
            if (!h.isBlank()) out.add(new TextCandidate(h, 0.30, "id-humanized"));
        }
        String myName = e.getAttribName();
        if (myName == null || myName.isBlank()) myName = getAttribute(e, "name");
        if (myName != null && !myName.isBlank()) {
            String h = TextSimilarity.humanize(myName);
            if (!h.isBlank()) out.add(new TextCandidate(h, 0.30, "name-humanized"));
        }

        // S7 / S8 — OCR (weights from cfg, default 0.85 / 0.70 / 0.55)
        OcrCorrelationResult ocr = ocrByXPath.get(e.getXPath());
        if (ocr != null) {
            String quality = ocr.matchQuality == null ? "" : ocr.matchQuality;
            String text = ocr.ocrText == null ? "" : ocr.ocrText;
            if (!text.isBlank()) {
                switch (quality) {
                    case "EXACT_CONTAIN":
                        out.add(new TextCandidate(text, ocrExactWeight, "ocr.EXACT_CONTAIN"));
                        break;
                    case "OVERLAP":
                        out.add(new TextCandidate(text, ocrOverlapWeight, "ocr.OVERLAP"));
                        break;
                    case "PROXIMITY":
                        out.add(new TextCandidate(text, ocrProxWeight, "ocr.PROXIMITY"));
                        break;
                    default:
                        // NONE — skip
                }
            }
            // Even when no primary OCR word was joined, ocrNearestText might exist for PROXIMITY
            String near = ocr.ocrNearestText == null ? "" : ocr.ocrNearestText;
            if (!near.isBlank() && !"PROXIMITY".equals(quality) && text.isBlank()) {
                // Weighted slightly below PROXIMITY since it's never inside the bbox.
                out.add(new TextCandidate(near, ocrProxWeight * 0.85, "ocr.nearest"));
            }
        }

        return out;
    }

    /**
     * If OCR text fuzzy-matches a non-OCR candidate, multiply that candidate's score
     * by {@link #OCR_CORROBORATION_MULTIPLIER}. Implements Roadmap 2's reminder rule.
     */
    private static void applyOcrCorroboration(List<TextCandidate> cands, OcrCorrelationResult ocr) {
        if (ocr == null) return;
        String ocrText = ocr.ocrText == null ? "" : ocr.ocrText;
        if (ocrText.isBlank()) return;
        for (TextCandidate c : cands) {
            if (c.source.startsWith("ocr.")) continue;
            if (TextSimilarity.fuzzyMatches(c.text, ocrText, FUZZY_THRESHOLD)) {
                c.score *= OCR_CORROBORATION_MULTIPLIER;
                c.source = c.source + "+ocr-corroborated";
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void addAttrCandidate(List<TextCandidate> out, ElementDTO e, String attrName, double score) {
        String v = getAttribute(e, attrName);
        if (v != null && !v.isBlank()) {
            out.add(new TextCandidate(v, score, attrName));
        }
    }

    private static String getAttribute(ElementDTO e, String name) {
        if (e == null || e.getAttributeData() == null || name == null) return null;
        for (AttributeData a : e.getAttributeData()) {
            if (a == null || a.getName() == null) continue;
            if (name.equalsIgnoreCase(a.getName())) return a.getValue();
        }
        return null;
    }

    private static String clean(String text) {
        if (text == null) return "";
        String t = text.replace(' ', ' ') // NBSP
                .replaceAll("\\s+", " ")
                .trim();
        // Strip trailing colon / asterisk / exclamation often used in form labels.
        while (t.endsWith(":") || t.endsWith("*") || t.endsWith("!") || t.endsWith("?")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /**
     * Fallback base for {@code definedName} when no text candidate produced anything.
     * Uses tag + last-known id/name; falls back to {@code "element"}.
     */
    private static String defaultBaseFor(ElementDTO e) {
        String tag = e.getTagName() == null ? "element" : e.getTagName().toLowerCase(Locale.ROOT);
        String id = e.getAttribId();
        if (id != null && !id.isBlank()) return tag + "_" + id;
        String name = e.getAttribName();
        if (name != null && !name.isBlank()) return tag + "_" + name;
        return tag;
    }

    private static Map<String, OcrCorrelationResult> readOcrByXPath(Path file) {
        Map<String, OcrCorrelationResult> out = new HashMap<>();
        if (file == null || !Files.exists(file)) return out;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            OcrCorrelationResult[] arr = GSON.fromJson(r, OcrCorrelationResult[].class);
            if (arr == null) return out;
            for (OcrCorrelationResult c : arr) {
                if (c == null || c.xpath == null) continue;
                out.put(c.xpath, c);
            }
        } catch (Exception ex) {
            log.debug("Could not read OCR correlation from {}: {}", file, ex.getMessage());
        }
        return out;
    }

    /** Internal scoring record. */
    private static final class TextCandidate {
        final String text;
        double score;
        String source;

        TextCandidate(String text, double score, String source) {
            this.text = text;
            this.score = score;
            this.source = source;
        }
    }

    // Visible for tests
    static List<TextCandidate> gatherForTest(
            ElementDTO e,
            Map<String, ElementDTO> elementsById,
            Map<String, ElementDTO> labelsForId,
            Map<String, OcrCorrelationResult> ocrByXPath) {
        return gather(e, elementsById, labelsForId, ocrByXPath, 0.85, 0.70, 0.55);
    }

    @SuppressWarnings("unused")
    private static void touch() {
        Collections.emptyList();
    }
}
