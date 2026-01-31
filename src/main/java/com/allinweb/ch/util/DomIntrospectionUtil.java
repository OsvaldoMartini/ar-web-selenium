package com.allinweb.ch.util;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;

public class DomIntrospectionUtil {

    private static final Set<String> IMPORTANT_TAGS = Set.of(
            "a",
            "button",
            "input",
            "textarea",
            "select",
            "option",
            "label",
            "form",
            "img",
            "ul",
            "ol",
            "li",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "p",
            "span",
            "div",
            "section",
            "article",
            "main",
            "nav",
            "aside",
            "footer");

    private static final Set<String> ALWAYS_IGNORE_TAGS = Set.of(
            "html",
            "head",
            "meta",
            "link",
            "script",
            "style",
            "noscript",
            "base",
            "svg",
            "path",
            "g",
            "circle",
            "polygon",
            "defs",
            "use",
            "title");

    public static List<String> listImportantElementsFromPageSource(WebDriver driver) {
        Document doc = Jsoup.parse(driver.getPageSource());
        Elements all = doc.getAllElements();

        LinkedHashSet<String> unique = new LinkedHashSet<>();

        for (Element el : all) {
            String tag = safeLower(el.tagName());
            if (tag.isBlank()) continue;
            if (ALWAYS_IGNORE_TAGS.contains(tag)) continue;
            if (!IMPORTANT_TAGS.contains(tag)) continue;
            if (isNoisyContainer(el, tag)) continue;

            String idPart = bestIdentifierForAny(el, tag);
            unique.add(tag + " - " + idPart);
        }

        return new ArrayList<>(unique);
    }

    /** Inputs-only list enriched with label text (generic: works on any page) */
    public static List<InputInfo> listInputsWithLabelsFromPageSource(WebDriver driver) {
        Document doc = Jsoup.parse(driver.getPageSource());

        // Build quick lookups for label[for] and id -> element text
        Map<String, String> labelForMap = buildLabelForMap(doc);
        Map<String, String> idToTextMap = buildIdToTextMap(doc);

        Elements inputs = doc.select("input, textarea, select");

        LinkedHashMap<String, InputInfo> unique = new LinkedHashMap<>();

        for (Element el : inputs) {
            String tag = safeLower(el.tagName());

            // skip hidden inputs unless you want them
            if (tag.equals("input")) {
                String type = normalize(el.attr("type")).toLowerCase(Locale.ROOT);
                if (type.equals("hidden")) continue;
            }

            String id = normalize(el.id());
            String name = normalize(el.attr("name"));
            String type = normalize(el.attr("type"));

            String labelText = inferLabelText(el, labelForMap, idToTextMap);

            String identifier = bestLocatorForInput(el);
            String printable = tag + " - " + identifier + (labelText.isBlank() ? "" : " - label=" + labelText);

            // Dedup key: prefer id, else name, else tag+placeholder+aria-label
            String dedupKey = !id.isBlank()
                    ? tag + "#id=" + id
                    : (!name.isBlank()
                            ? tag + "#name=" + name
                            : tag + "#fallback=" + normalize(el.attr("placeholder")) + "|"
                                    + normalize(el.attr("aria-label")));

            unique.putIfAbsent(dedupKey, new InputInfo(tag, id, name, type, labelText, identifier, printable));
        }

        return new ArrayList<>(unique.values());
    }

    // ----------------- label inference -----------------

    private static Map<String, String> buildLabelForMap(Document doc) {
        Map<String, String> map = new HashMap<>();
        for (Element label : doc.select("label[for]")) {
            String key = normalize(label.attr("for"));
            String val = normalize(label.text());
            if (!key.isBlank() && !val.isBlank()) {
                map.putIfAbsent(key, val);
            }
        }
        return map;
    }

    /** id -> element.text()/ownText() (used for aria-labelledby="someId") */
    private static Map<String, String> buildIdToTextMap(Document doc) {
        Map<String, String> map = new HashMap<>();
        for (Element el : doc.select("[id]")) {
            String id = normalize(el.id());
            if (id.isBlank()) continue;

            // prefer ownText() to avoid huge nested blobs; fallback to text()
            String text = normalize(el.ownText());
            if (!isGoodLabel(text)) {
                text = normalize(el.text());
            }

            if (isGoodLabel(text)) {
                map.putIfAbsent(id, text);
            }
        }
        return map;
    }

    /**
     * Generic priority:
     * 1) label[for=id]
     * 2) aria-labelledby -> referenced element text (first meaningful token)
     * 3) aria-label
     * 4) placeholder
     * 5) nearest preceding label/div/span/p text (prev siblings + parent prev siblings)
     * 6) name (last fallback)
     *
     * Guardrails: avoid junk (scripts/footer/legal-like blobs)
     */
    private static String inferLabelText(
            Element inputEl, Map<String, String> labelForMap, Map<String, String> idToTextMap) {

        String id = normalize(inputEl.id());
        if (!id.isBlank()) {
            String label = labelForMap.get(id);
            if (isGoodLabel(label)) return shorten(label, 80);
        }

        String ariaLabelledBy = normalize(inputEl.attr("aria-labelledby"));
        if (!ariaLabelledBy.isBlank()) {
            // aria-labelledby can be "id1 id2"
            for (String token : ariaLabelledBy.split("\\s+")) {
                String refText = idToTextMap.get(token.trim());
                if (isGoodLabel(refText)) return shorten(refText, 80);
            }
        }

        String ariaLabel = normalize(inputEl.attr("aria-label"));
        if (isGoodLabel(ariaLabel)) return shorten(ariaLabel, 80);

        String placeholder = normalize(inputEl.attr("placeholder"));
        if (isGoodLabel(placeholder)) return shorten(placeholder, 80);

        // ✅ New heuristic: nearest preceding label/div/span/p text (generic)
        String nearest = findNearestPrecedingLabelLikeText(inputEl);
        if (isGoodLabel(nearest)) return shorten(nearest, 80);

        String name = normalize(inputEl.attr("name"));
        if (isGoodLabel(name)) return shorten(name, 80);

        return "";
    }

    /**
     * Heuristic:
     * - scan previous siblings of the input:
     *   - if <label>, use its text
     *   - else if <div>/<span>/<p>, use ownText (then fallback to text)
     * - if not found, go up to parent and scan the parent's previous siblings similarly
     * - limited scan to reduce false positives + time
     */
    private static String findNearestPrecedingLabelLikeText(Element inputEl) {
        Element cursor = inputEl;

        final int maxParentHops = 5;
        final int maxSiblingScan = 8;

        for (int hop = 0; hop <= maxParentHops && cursor != null; hop++) {
            String fromSiblings = scanPreviousSiblingsForText(cursor, maxSiblingScan);
            if (isGoodLabel(fromSiblings)) return fromSiblings;

            cursor = cursor.parent();
        }
        return "";
    }

    private static String scanPreviousSiblingsForText(Element start, int maxScan) {
        Element sib = start.previousElementSibling();
        int scanned = 0;

        while (sib != null && scanned < maxScan) {
            String tag = safeLower(sib.tagName());

            // best case: explicit label
            if ("label".equals(tag)) {
                String t = normalize(sib.text());
                if (isGoodLabel(t)) return t;
            }

            // common text wrappers near inputs
            if (tag.equals("div")
                    || tag.equals("span")
                    || tag.equals("p")
                    || tag.equals("strong")
                    || tag.equals("em")
                    || tag.equals("small")
                    || tag.equals("legend")) {

                String own = normalize(sib.ownText());
                if (isGoodLabel(own)) return own;

                String full = normalize(sib.text());
                if (isGoodLabel(full)) return full;
            }

            // wrapper case: try to find a label-like text inside
            String nested = findLastLabelLikeTextInside(sib);
            if (isGoodLabel(nested)) return nested;

            sib = sib.previousElementSibling();
            scanned++;
        }

        return "";
    }

    private static String findLastLabelLikeTextInside(Element container) {
        // labels inside wrapper
        Elements labels = container.select("label");
        for (int i = labels.size() - 1; i >= 0; i--) {
            String t = normalize(labels.get(i).text());
            if (isGoodLabel(t)) return t;
        }

        // common textish nodes inside wrapper
        Elements nodes = container.select("span, div, p, strong, em, small, legend");
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Element e = nodes.get(i);

            String t = normalize(e.ownText());
            if (!isGoodLabel(t)) {
                t = normalize(e.text());
            }
            if (isGoodLabel(t)) return t;
        }

        return "";
    }

    /**
     * Guardrails to avoid junk/boilerplate:
     * - not empty
     * - not too long (likely paragraphs/legal)
     * - not JS/CSS/JSON-ish
     * - not typical footer/legal phrases
     */
    private static boolean isGoodLabel(String s) {
        String t = normalize(s);
        if (t.isBlank()) return false;

        // too long = probably a paragraph/legal blob
        if (t.length() > 90) return false;

        // looks like code/CSS/JSON
        if (t.contains("{") || t.contains("}") || t.contains(";")) return false;

        // very low signal
        if (t.replaceAll("[\\p{Punct}\\s]+", "").length() < 2) return false;

        String low = t.toLowerCase(Locale.ROOT);

        if (low.equals("javascript")) return false;
        if (low.contains("all rights reserved")) return false;
        if (low.contains("cookie")) return false; // comment out if you want cookie labels
        if (low.contains("privacy")) return false; // comment out if you want privacy labels
        if (low.contains("terms")) return false; // comment out if you want terms labels
        if (low.contains("©")) return false;

        return true;
    }

    // ----------------- locator / formatting -----------------

    private static String bestLocatorForInput(Element el) {
        // For inputs, id/name are usually the best automation locators.
        String id = normalize(el.id());
        if (!id.isBlank()) return "id=" + shorten(id, 90);

        String name = normalize(el.attr("name"));
        if (!name.isBlank()) return "name=" + shorten(name, 90);

        String testId = normalize(el.attr("data-testid"));
        if (!testId.isBlank()) return "data-testid=" + shorten(testId, 90);

        String aria = normalize(el.attr("aria-label"));
        if (!aria.isBlank()) return "aria-label=" + shorten(aria, 90);

        String placeholder = normalize(el.attr("placeholder"));
        if (!placeholder.isBlank()) return "placeholder=" + shorten(placeholder, 90);

        return "(no-id)";
    }

    private static String bestIdentifierForAny(Element el, String tag) {
        // General-purpose best identifier
        List<String> keys = List.of(
                "id",
                "name",
                "data-testid",
                "data-test",
                "data-test-id",
                "aria-label",
                "aria-labelledby",
                "role",
                "title",
                "href",
                "src",
                "placeholder",
                "alt");

        for (String k : keys) {
            String v = normalize(el.attr(k));
            if (!v.isBlank()) return k + "=" + shorten(v, 90);
        }

        String text = normalize(el.ownText());
        if (!text.isBlank()) return "text=" + shorten(text, 60);

        return "(no-id)";
    }

    private static boolean isNoisyContainer(Element el, String tag) {
        boolean isContainer = tag.equals("div")
                || tag.equals("span")
                || tag.equals("section")
                || tag.equals("article")
                || tag.equals("nav")
                || tag.equals("main")
                || tag.equals("aside")
                || tag.equals("footer")
                || tag.equals("p");

        if (!isContainer) return false;

        if (hasAnyAttr(el, "id", "name", "data-testid", "data-test", "aria-label", "role", "href")) {
            return false;
        }

        String text = normalize(el.ownText());
        return text.isBlank() || text.length() < 3;
    }

    private static boolean hasAnyAttr(Element el, String... attrs) {
        for (String a : attrs) {
            if (!normalize(el.attr(a)).isBlank()) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 1) + "…";
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
