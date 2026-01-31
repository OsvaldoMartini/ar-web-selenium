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

    /**
     * Editable / selectable controls list (generic for any page).
     *
     * Includes:
     * - native: input, textarea, select (and select's options)
     * - ARIA/W3C patterns: role=combobox, textbox, searchbox, spinbutton,
     *                      listbox, option, tree, treeitem, grid, row, gridcell,
     *                      menu, menuitem, menuitemcheckbox, menuitemradio
     * - common framework hints: contenteditable, tabindex, data-testid, etc.
     *
     * Label inference:
     * 1) label[for=id]
     * 2) aria-labelledby -> referenced element text
     * 3) aria-label
     * 4) placeholder
     * 5) nearest preceding label/div/span/p text (prev siblings + parent prev siblings)
     *
     * Guardrails to avoid junk (scripts/footer/legal blobs/etc.)
     */
    public static List<InputInfo> listInputsWithLabelsFromPageSource(WebDriver driver) {
        Document doc = Jsoup.parse(driver.getPageSource());

        Map<String, String> labelForMap = buildLabelForMap(doc);
        Map<String, String> idToTextMap = buildIdToTextMap(doc);

        // ✅ minimal change: we now collect "editable/selectable" controls (not only input/textarea/select)
        Elements controls = collectEditableControls(doc);

        LinkedHashMap<String, InputInfo> unique = new LinkedHashMap<>();

        for (Element el : controls) {
            String tag = safeLower(el.tagName());

            // Ignore obvious noise
            if (ALWAYS_IGNORE_TAGS.contains(tag)) continue;

            // Skip hidden inputs unless you want them
            if ("input".equals(tag)) {
                String type = normalize(el.attr("type")).toLowerCase(Locale.ROOT);
                if ("hidden".equals(type)) continue;
            }

            // Determine "type" for record (native type OR role-based type)
            String type = inferControlType(el);

            String id = normalize(el.id());
            String name = normalize(el.attr("name"));

            String labelText = inferLabelText(el, labelForMap, idToTextMap);

            String identifier = bestLocatorForControl(el);
            String printable = tag + " - " + identifier
                    + (type.isBlank() ? "" : " - type=" + type)
                    + (labelText.isBlank() ? "" : " - label=" + labelText);

            String dedupKey = buildControlDedupKey(el, tag, id, name, type);

            unique.putIfAbsent(dedupKey, new InputInfo(tag, id, name, type, labelText, identifier, printable));
        }

        return new ArrayList<>(unique.values());
    }

    // ----------------- NEW: collect controls -----------------

    private static Elements collectEditableControls(Document doc) {
        // Native form controls
        String nativeSelector = "input, textarea, select, option";

        // WAI-ARIA patterns for editable/selectable UI
        // (covers React/Angular custom dropdowns, comboboxes, listboxes, etc.)
        String ariaSelector = "[role=textbox], [role=searchbox], [role=combobox], [role=listbox], [role=option], "
                + "[role=spinbutton], [role=tree], [role=treeitem], "
                + "[role=grid], [role=row], [role=gridcell], [role=cell], "
                + "[role=menu], [role=menuitem], [role=menuitemcheckbox], [role=menuitemradio]";

        // Contenteditable fields (common in editors)
        String editableSelector = "[contenteditable=true], [contenteditable=''], [contenteditable=yes]";

        // A few common framework “input-like” cases:
        // - elements with aria-haspopup=listbox often behave as dropdown triggers
        // - elements with aria-expanded + aria-controls sometimes indicate menus / combobox popups
        String frameworkHints = "[aria-haspopup=listbox], [aria-haspopup=menu], [aria-controls][aria-expanded]";

        // Compose selector
        Elements controls = new Elements();
        controls.addAll(doc.select(nativeSelector));
        controls.addAll(doc.select(ariaSelector));
        controls.addAll(doc.select(editableSelector));
        controls.addAll(doc.select(frameworkHints));

        // Optional: de-dup while preserving order
        LinkedHashSet<Element> ordered = new LinkedHashSet<>(controls);
        return new Elements(ordered);
    }

    private static String inferControlType(Element el) {
        String tag = safeLower(el.tagName());

        if ("input".equals(tag)) return normalize(el.attr("type"));
        if ("textarea".equals(tag)) return "textarea";
        if ("select".equals(tag)) return "select";
        if ("option".equals(tag)) return "option";

        String role = normalize(el.attr("role")).toLowerCase(Locale.ROOT);
        if (!role.isBlank()) return role;

        if (isContentEditable(el)) return "contenteditable";

        // fallback: hint for dropdown triggers etc.
        if (!normalize(el.attr("aria-haspopup")).isBlank())
            return "aria-haspopup=" + normalize(el.attr("aria-haspopup"));

        return "";
    }

    private static boolean isContentEditable(Element el) {
        String ce = normalize(el.attr("contenteditable")).toLowerCase(Locale.ROOT);
        return "true".equals(ce) || "yes".equals(ce) || ce.isBlank() && el.hasAttr("contenteditable");
    }

    private static String buildControlDedupKey(Element el, String tag, String id, String name, String type) {
        if (!id.isBlank()) return tag + "#id=" + id;

        if (!name.isBlank()) return tag + "#name=" + name;

        String role = normalize(el.attr("role"));
        if (!role.isBlank()) return tag + "#role=" + role + "#type=" + type;

        String testId = normalize(el.attr("data-testid"));
        if (!testId.isBlank()) return tag + "#data-testid=" + testId;

        String ariaLabel = normalize(el.attr("aria-label"));
        String placeholder = normalize(el.attr("placeholder"));
        String text = normalize(el.ownText());

        return tag + "#fallback=" + type + "|" + placeholder + "|" + ariaLabel + "|" + text;
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
     * 2) aria-labelledby -> referenced element text
     * 3) aria-label
     * 4) placeholder
     * 5) nearest preceding label/div/span/p text (prev siblings + parent prev siblings)
     * 6) name (last fallback)
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
            for (String token : ariaLabelledBy.split("\\s+")) {
                String refText = idToTextMap.get(token.trim());
                if (isGoodLabel(refText)) return shorten(refText, 80);
            }
        }

        String ariaLabel = normalize(inputEl.attr("aria-label"));
        if (isGoodLabel(ariaLabel)) return shorten(ariaLabel, 80);

        String placeholder = normalize(inputEl.attr("placeholder"));
        if (isGoodLabel(placeholder)) return shorten(placeholder, 80);

        String nearest = findNearestPrecedingLabelLikeText(inputEl);
        if (isGoodLabel(nearest)) return shorten(nearest, 80);

        String name = normalize(inputEl.attr("name"));
        if (isGoodLabel(name)) return shorten(name, 80);

        return "";
    }

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

            if ("label".equals(tag)) {
                String t = normalize(sib.text());
                if (isGoodLabel(t)) return t;
            }

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

            String nested = findLastLabelLikeTextInside(sib);
            if (isGoodLabel(nested)) return nested;

            sib = sib.previousElementSibling();
            scanned++;
        }

        return "";
    }

    private static String findLastLabelLikeTextInside(Element container) {
        Elements labels = container.select("label");
        for (int i = labels.size() - 1; i >= 0; i--) {
            String t = normalize(labels.get(i).text());
            if (isGoodLabel(t)) return t;
        }

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

    private static boolean isGoodLabel(String s) {
        String t = normalize(s);
        if (t.isBlank()) return false;
        if (t.length() > 90) return false;
        if (t.contains("{") || t.contains("}") || t.contains(";")) return false;
        if (t.replaceAll("[\\p{Punct}\\s]+", "").length() < 2) return false;

        String low = t.toLowerCase(Locale.ROOT);
        if (low.equals("javascript")) return false;
        if (low.contains("all rights reserved")) return false;
        if (low.contains("©")) return false;

        // keep these as “soft” guardrails; comment out if you want those labels
        if (low.contains("cookie")) return false;
        if (low.contains("privacy")) return false;
        if (low.contains("terms")) return false;

        return true;
    }

    // ----------------- locator / formatting -----------------

    // ✅ minimal addition: best locator for ANY editable control (not only native inputs)
    private static String bestLocatorForControl(Element el) {
        String id = normalize(el.id());
        if (!id.isBlank()) return "id=" + shorten(id, 90);

        String name = normalize(el.attr("name"));
        if (!name.isBlank()) return "name=" + shorten(name, 90);

        String testId = normalize(el.attr("data-testid"));
        if (!testId.isBlank()) return "data-testid=" + shorten(testId, 90);

        String role = normalize(el.attr("role"));
        if (!role.isBlank()) return "role=" + shorten(role, 90);

        String aria = normalize(el.attr("aria-label"));
        if (!aria.isBlank()) return "aria-label=" + shorten(aria, 90);

        String labelledBy = normalize(el.attr("aria-labelledby"));
        if (!labelledBy.isBlank()) return "aria-labelledby=" + shorten(labelledBy, 90);

        String placeholder = normalize(el.attr("placeholder"));
        if (!placeholder.isBlank()) return "placeholder=" + shorten(placeholder, 90);

        // option-like identification
        if ("option".equalsIgnoreCase(el.tagName())) {
            String val = normalize(el.attr("value"));
            if (!val.isBlank()) return "value=" + shorten(val, 90);
            String txt = normalize(el.text());
            if (!txt.isBlank()) return "text=" + shorten(txt, 60);
        }

        String text = normalize(el.ownText());
        if (!text.isBlank()) return "text=" + shorten(text, 60);

        return "(no-id)";
    }

    private static String bestIdentifierForAny(Element el, String tag) {
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
