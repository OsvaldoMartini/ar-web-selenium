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

    /** Main request: inputs-only list enriched with label text */
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

    /** id -> element.text() (used for aria-labelledby="someId") */
    private static Map<String, String> buildIdToTextMap(Document doc) {
        Map<String, String> map = new HashMap<>();
        for (Element el : doc.select("[id]")) {
            String id = normalize(el.id());
            if (id.isBlank()) continue;
            String text = normalize(el.text());
            if (!text.isBlank()) {
                map.putIfAbsent(id, text);
            }
        }
        return map;
    }

    /**
     * Priority similar to your JS:
     * 1) label[for=id]
     * 2) aria-labelledby -> id text (first token)
     * 3) aria-label
     * 4) placeholder
     * 5) name (as last fallback)
     */
    private static String inferLabelText(
            Element inputEl, Map<String, String> labelForMap, Map<String, String> idToTextMap) {

        String id = normalize(inputEl.id());
        if (!id.isBlank()) {
            String label = labelForMap.get(id);
            if (label != null && !label.isBlank()) return shorten(label, 80);
        }

        String ariaLabelledBy = normalize(inputEl.attr("aria-labelledby"));
        if (!ariaLabelledBy.isBlank()) {
            // aria-labelledby can be "id1 id2"
            String firstId = ariaLabelledBy.split("\\s+")[0].trim();
            String refText = idToTextMap.get(firstId);
            if (refText != null && !refText.isBlank()) return shorten(refText, 80);
        }

        String ariaLabel = normalize(inputEl.attr("aria-label"));
        if (!ariaLabel.isBlank()) return shorten(ariaLabel, 80);

        String placeholder = normalize(inputEl.attr("placeholder"));
        if (!placeholder.isBlank()) return shorten(placeholder, 80);

        String name = normalize(inputEl.attr("name"));
        if (!name.isBlank()) return shorten(name, 80);

        return "";
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
