package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannedElement;
import com.allinweb.ch.util.TextSimilarity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Self-healing resolver against the {@code scanned_element} source-of-truth registry.
 *
 * <p>Given an instruction (as authored) and the registry rows for its scope
 * (organization + bot job), returns the best-matching scanned element so execution can validate or
 * re-resolve the target when its stored xPath drifts or a name collides. Pure and deterministic —
 * first strong match wins, so it is fully unit-testable and safe to call as a fallback.
 *
 * <p>Ladder (first match wins):
 * <ol>
 *   <li>exact current or client-authored custom xPath</li>
 *   <li>exact CSS selector</li>
 *   <li>exact name ({@code defined_name}, {@code some_text}, {@code client_named}, or HTML
 *       {@code attrib_name}, case-insensitive): unique -&gt; take it; multiple (same name, different
 *       xPath) -&gt; disambiguate by nearest coordinates</li>
 *   <li>fuzzy name (Levenshtein ratio &ge; threshold)</li>
 * </ol>
 */
public final class ScannedElementResolver {

    /** Minimum Levenshtein ratio for the fuzzy-name rung. */
    private static final double FUZZY_THRESHOLD = 0.80;

    public enum Strategy {
        XPATH_EXACT,
        CSS_EXACT,
        NAME_UNIQUE,
        NAME_COORDS,
        NAME_AMBIGUOUS,
        NAME_FUZZY,
        NONE
    }

    public record Result(ScannedElement element, Strategy strategy, double confidence) {
        public boolean matched() {
            return element != null && strategy != Strategy.NONE;
        }
    }

    private static final Result NO_MATCH = new Result(null, Strategy.NONE, 0.0);

    private ScannedElementResolver() {}

    public static Result resolve(List<ScannedElement> registry, InstructionLoad ins) {
        if (registry == null || registry.isEmpty() || ins == null) {
            return NO_MATCH;
        }

        // 1) exact current or client-authored xPath
        String xpath = trim(ins.getXpath());
        if (!xpath.isEmpty()) {
            for (ScannedElement s : registry) {
                if (xpath.equals(trim(s.getCustomXPath())) || xpath.equals(trim(s.getXPath()))) {
                    return new Result(s, Strategy.XPATH_EXACT, 1.0);
                }
            }
        }

        // 2) exact CSS selector
        String css = trim(ins.getCssSelector());
        if (!css.isEmpty()) {
            for (ScannedElement s : registry) {
                if (css.equalsIgnoreCase(trim(s.getCssSelector()))) {
                    return new Result(s, Strategy.CSS_EXACT, 0.95);
                }
            }
        }

        // 3) exact name (defined_name / some_text)
        String name = trim(ins.getName()).toLowerCase(Locale.ROOT);
        if (!name.isEmpty()) {
            List<ScannedElement> byName = new ArrayList<>();
            for (ScannedElement s : registry) {
                if (matchesName(name, s)) {
                    byName.add(s);
                }
            }
            if (byName.size() == 1) {
                return new Result(byName.get(0), Strategy.NAME_UNIQUE, 0.85);
            }
            if (byName.size() > 1) {
                // Same name, different elements — disambiguate by nearest coordinates.
                double[] target = parseCoords(ins.getCoordinates());
                if (target != null) {
                    ScannedElement best = null;
                    double bestDist = Double.MAX_VALUE;
                    for (ScannedElement s : byName) {
                        double[] c = parseCoords(s.getCoordinates());
                        if (c == null) continue;
                        double d = dist(target, c);
                        if (d < bestDist) {
                            bestDist = d;
                            best = s;
                        }
                    }
                    if (best != null) {
                        return new Result(best, Strategy.NAME_COORDS, 0.75);
                    }
                }
                // No coordinates to break the tie — return the first but flag it ambiguous.
                return new Result(byName.get(0), Strategy.NAME_AMBIGUOUS, 0.50);
            }
        }

        // 4) fuzzy name
        if (!name.isEmpty()) {
            ScannedElement best = null;
            double bestRatio = 0.0;
            for (ScannedElement s : registry) {
                double r = maxSimilarity(
                        name,
                        s.getDefinedName(),
                        s.getSomeText(),
                        s.getClientNamed(),
                        s.getAttribName());
                if (r > bestRatio) {
                    bestRatio = r;
                    best = s;
                }
            }
            if (best != null && bestRatio >= FUZZY_THRESHOLD) {
                return new Result(best, Strategy.NAME_FUZZY, bestRatio);
            }
        }

        return NO_MATCH;
    }

    private static boolean matchesName(String expected, ScannedElement element) {
        return expected.equals(normalized(element.getDefinedName()))
                || expected.equals(normalized(element.getSomeText()))
                || expected.equals(normalized(element.getClientNamed()))
                || expected.equals(normalized(element.getAttribName()));
    }

    private static double maxSimilarity(String expected, String... candidates) {
        double best = 0.0;
        for (String candidate : candidates) {
            best = Math.max(
                    best,
                    TextSimilarity.levenshteinRatio(expected, normalized(candidate)));
        }
        return best;
    }

    private static String normalized(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static double[] parseCoords(String coords) {
        if (coords == null || coords.isBlank()) return null;
        String[] parts = coords.split(",");
        if (parts.length < 2) return null;
        try {
            return new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
