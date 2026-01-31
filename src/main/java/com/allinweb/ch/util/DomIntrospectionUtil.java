package com.allinweb.ch.util;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;

public class DomIntrospectionUtil {

    // Tags we consider "important" for UI automation / interaction / visible content
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
            "table",
            "thead",
            "tbody",
            "tr",
            "td",
            "th",
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

    // Tags to always ignore (noise / infra)
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

    /**
     * Returns unique element names from current page source, keeping only "important" elements.
     * Output: tagName - id=... / name=... / data-testid=... / aria-label=... / text=...
     */
    public static List<String> listImportantElementNamesFromPageSource(WebDriver driver) {
        String html = driver.getPageSource();
        if (html == null || html.isBlank()) return Collections.emptyList();

        Document doc = Jsoup.parse(html);

        // Option A: iterate over all and filter
        Elements all = doc.getAllElements();

        LinkedHashSet<String> unique = new LinkedHashSet<>();

        for (Element el : all) {
            String tag = safeLower(el.tagName());
            if (tag.isBlank()) continue;

            // hard ignore
            if (ALWAYS_IGNORE_TAGS.contains(tag)) continue;

            // keep only relevant tags
            if (!IMPORTANT_TAGS.contains(tag)) continue;

            // ignore structural containers that have no identity and no text (reduces div/span spam)
            if (isNoisyContainer(el, tag)) continue;

            String idPart = bestIdentifier(el, tag);
            unique.add(tag + " - " + idPart);
        }

        return new ArrayList<>(unique);
    }

    /**
     * Helps reduce spam from div/span/nav/etc with no attributes and no useful text.
     */
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

        // If container has a strong identifier, keep it
        if (hasAnyAttr(el, "id", "name", "data-testid", "data-test", "aria-label", "role", "href")) {
            return false;
        }

        // If it has meaningful text, keep it
        String text = normalize(el.ownText());
        if (!text.isBlank() && text.length() >= 3) return false;

        // otherwise ignore
        return true;
    }

    private static boolean hasAnyAttr(Element el, String... attrs) {
        for (String a : attrs) {
            if (!normalize(el.attr(a)).isBlank()) return true;
        }
        return false;
    }

    /**
     * Chooses the best identification depending on tag.
     * - inputs: prefer id/name/placeholder
     * - links: prefer id/aria-label/href/text
     * - buttons: prefer id/aria-label/text
     * - images: prefer alt/title/src
     */
    private static String bestIdentifier(Element el, String tag) {
        List<String> keys;

        switch (tag) {
            case "input", "textarea", "select" -> keys = List.of(
                    "id",
                    "name",
                    "aria-label",
                    "aria-labelledby",
                    "placeholder",
                    "data-testid",
                    "data-test",
                    "data-test-id",
                    "role");

            case "button" -> keys = List.of(
                    "id", "aria-label", "aria-labelledby", "data-testid", "data-test", "data-test-id", "name", "role");

            case "a" -> keys = List.of(
                    "id", "aria-label", "aria-labelledby", "data-testid", "data-test", "data-test-id", "href", "role");

            case "img" -> keys = List.of("id", "alt", "title", "data-testid", "src");

            default -> keys = List.of(
                    "id",
                    "data-testid",
                    "data-test",
                    "data-test-id",
                    "name",
                    "aria-label",
                    "aria-labelledby",
                    "role",
                    "title");
        }

        for (String k : keys) {
            String v = normalize(el.attr(k));
            if (!v.isBlank()) return k + "=" + shorten(v, 90);
        }

        // fallback: text for visible elements
        String ownText = normalize(el.ownText());
        if (!ownText.isBlank()) return "text=" + shorten(ownText, 60);

        return "(no-id)";
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
