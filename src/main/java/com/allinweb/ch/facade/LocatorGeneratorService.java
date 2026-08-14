package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.TextSimilarity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Generates unique locators from pasted HTML and applies one XPath to one scanned element. */
@Slf4j
public final class LocatorGeneratorService {

    private static final LocatorGeneratorService INSTANCE =
            new LocatorGeneratorService(new DatabasePersistence());
    private static final Gson GSON = new Gson();

    // Leave room for the surrounding JSON envelope under the Page Scanner's 2 MB request cap.
    private static final int MAX_HTML_LENGTH = 1_500_000;
    private static final int MAX_CONTROLS = 500;
    private static final int MAX_XPATH_LENGTH = 16_384;
    private static final int MAX_ELEMENT_KEY_LENGTH = 2_048;

    private static final String CANDIDATE_SELECTOR = String.join(
            ", ",
            "input",
            "select",
            "textarea",
            "button",
            "mat-select",
            "[role=combobox]",
            "[role=listbox]",
            "[role=textbox]",
            "[role=button]",
            "[contenteditable=true]");

    /** Stable attributes in preference order. Framework-generated IDs are filtered separately. */
    private static final String[] STABLE_ATTRS = {
        "data-testid", "data-test", "test-id", "id", "formcontrolname", "name",
        "aria-label", "placeholder", "type", "role"
    };

    private static final Pattern VOLATILE_ID = Pattern.compile(
            "(?i)^(mat-|cdk-|:r|react|ember|ext-gen|radix|headlessui|__)"
                    + "|[0-9a-f]{8,}|\\d{4,}$");

    private final Persistence persistence;

    LocatorGeneratorService(Persistence persistence) {
        this.persistence = Objects.requireNonNull(persistence, "Locator persistence is required");
    }

    public static LocatorGeneratorService getInstance() {
        return INSTANCE;
    }

    /** Body: {@code {requestId, html}}. */
    public JsonObject generate(JsonObject body) {
        String requestId = optionalString(body, "requestId");
        String html = optionalString(body, "html");
        JsonObject response = baseResponse(requestId);

        if (html.isBlank()) {
            return generationFailure(response, "Paste the HTML of the controls you want to disambiguate.");
        }
        if (html.length() > MAX_HTML_LENGTH) {
            return generationFailure(response, "Pasted HTML is too large; paste only the relevant controls.");
        }

        try {
            Document document = Jsoup.parseBodyFragment(html);
            List<Element> candidates = collectCandidates(document);
            if (candidates.size() > MAX_CONTROLS) {
                return generationFailure(
                        response,
                        "Pasted HTML contains too many controls; paste only the controls that need locators.");
            }

            JsonArray controls = new JsonArray();
            boolean anyPositional = false;
            Set<String> usedDefinedNames = new LinkedHashSet<>();
            for (int index = 0; index < candidates.size(); index++) {
                JsonObject locator = buildLocator(candidates.get(index), document, index, usedDefinedNames);
                controls.add(locator);
                anyPositional |= locator.get("positional").getAsBoolean();
            }

            response.addProperty("ok", true);
            response.add("controls", controls);
            if (candidates.isEmpty()) {
                response.addProperty(
                        "warning", "No interactive controls were found in the pasted HTML.");
            } else if (anyPositional) {
                response.addProperty(
                        "warning",
                        "Some controls required a positional locator. Verify those locators if the page layout changes.");
            } else {
                response.addProperty("warning", "");
            }
            return response;
        } catch (RuntimeException error) {
            log.warn("Locator generation failed: {}", error.getMessage());
            return generationFailure(response, "Could not parse the pasted HTML.");
        }
    }

    /**
     * Body: {@code {requestId, elementKey, elementDetails:[ElementDTO], xpath}}.
     * Returns the authoritative persisted DTO for the correlated scanner row.
     */
    public JsonObject apply(
            JsonObject body,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            String pageUrl) {
        String requestId = requireString(body, "requestId", 160, "Locator requestId is required");
        String elementKey = requireString(
                body, "elementKey", MAX_ELEMENT_KEY_LENGTH, "Select a Page Scanner element first");
        String xpath = requireString(body, "xpath", MAX_XPATH_LENGTH, "Select an XPath to apply");
        if (!looksLikeXPath(xpath) || !isValidXPath(xpath)) {
            throw new IllegalArgumentException("The selected locator is not an XPath");
        }
        if (homeBankingId <= 0 || botJobId <= 0) {
            throw new IllegalArgumentException("An active Page Scanner Bot Job is required");
        }

        JsonArray details = body == null || !body.has("elementDetails")
                        || !body.get("elementDetails").isJsonArray()
                ? null
                : body.getAsJsonArray("elementDetails");
        if (details == null || details.size() != 1 || details.get(0).isJsonNull()) {
            throw new IllegalArgumentException("Locator apply requires exactly one Page Scanner element");
        }
        ElementDTO selected = GSON.fromJson(details.get(0), ElementDTO.class);
        if (selected == null || blank(selected.getXPath())) {
            throw new IllegalArgumentException("The selected Page Scanner element has no stable XPath identity");
        }
        if (!elementKey.equals(elementKey(selected))) {
            throw new IllegalArgumentException(
                    "The selected Page Scanner element changed; refresh the grid and try again");
        }

        // Gson produces a complete detached copy, including flags that ElementDTO's historical copy
        // constructor does not currently carry.
        ElementDTO updated = GSON.fromJson(GSON.toJson(selected), ElementDTO.class);
        updated.setCustomXPath(xpath);

        PersistenceResult persisted;
        try {
            persisted = persistence.persist(
                    homeBankingId, botJobId, positiveOrNull(homeUrlId), pageUrl, updated);
        } catch (Exception failure) {
            throw new IllegalStateException("The generated XPath could not be saved", failure);
        }
        if (persisted == null || persisted.affectedRows() != 1) {
            throw new IllegalStateException("The generated XPath was not saved to the selected element");
        }

        JsonObject response = baseResponse(requestId);
        response.addProperty("ok", true);
        response.addProperty("elementKey", elementKey);
        response.add("element", GSON.toJsonTree(updated));
        response.addProperty("persisted", true);
        response.addProperty("message", "Generated XPath applied to the selected Page Scanner element.");
        return response;
    }

    private List<Element> collectCandidates(Document document) {
        Set<Element> found = new LinkedHashSet<>(document.select(CANDIDATE_SELECTOR));
        return new ArrayList<>(found);
    }

    private JsonObject buildLocator(
            Element target,
            Document document,
            int controlIndex,
            Set<String> usedDefinedNames) {
        String tag = target.tagName();
        List<String[]> available = stableAttributes(target);
        List<String[]> chosen = new ArrayList<>();
        boolean positional = false;
        String css;
        String xpath;

        if (available.isEmpty()) {
            positional = true;
            String baseCss = tag;
            int position = positionInDocument(document, baseCss, target);
            xpath = "(//" + tag + ")[" + position + "]";
            css = positionalCss(document, target, baseCss);
        } else {
            int attributeIndex = 0;
            chosen.add(available.get(attributeIndex++));
            css = buildCss(tag, chosen);
            while (!attributesUniquelyIdentify(document, tag, chosen, target)
                    && attributeIndex < available.size()) {
                chosen.add(available.get(attributeIndex++));
                css = buildCss(tag, chosen);
            }
            String baseXpath = buildXpath(tag, chosen);
            if (attributesUniquelyIdentify(document, tag, chosen, target)) {
                xpath = baseXpath;
                if (!isUniqueInDocument(document, css, target)) {
                    positional = true;
                    css = positionalCss(document, target, tag);
                }
            } else {
                positional = true;
                int position = positionByAttributes(document, tag, chosen, target);
                xpath = "(" + baseXpath + ")[" + position + "]";
                css = positionalCss(document, target, tag);
            }
        }

        String resolvedSomeText = someText(target);
        String resolvedDefinedName = definedName(target, resolvedSomeText, usedDefinedNames);
        JsonObject out = new JsonObject();
        out.addProperty("controlIndex", controlIndex);
        out.addProperty("tagName", tag);
        out.addProperty("controlKind", controlKind(target));
        out.addProperty("label", label(target));
        out.addProperty("someText", resolvedSomeText);
        out.addProperty("definedName", resolvedDefinedName);
        out.addProperty("attribId", Objects.toString(attributeValue(target, "id"), ""));
        out.addProperty("attribName", Objects.toString(attributeValue(target, "name"), ""));
        out.addProperty("attributeType", chosen.isEmpty() ? "" : chosen.get(0)[0]);
        out.addProperty("attributeValue", chosen.isEmpty() ? "" : chosen.get(0)[1]);
        out.add("attributeData", attributeData(available));
        out.addProperty("xpath", xpath);
        out.addProperty("css", css);
        out.addProperty("cssSelector", css);
        out.addProperty("positional", positional);
        out.addProperty(
                "note",
                positional
                        ? "A positional CSS fallback is used and may be fragile."
                        : "Unique in the pasted HTML by tag + " + describeChosen(chosen) + ".");
        return out;
    }

    private List<String[]> stableAttributes(Element target) {
        List<String[]> available = new ArrayList<>();
        for (String attribute : STABLE_ATTRS) {
            String value = attributeValue(target, attribute);
            if (value == null) continue;
            if ("id".equals(attribute) && VOLATILE_ID.matcher(value).find()) continue;
            available.add(new String[] {attribute, value});
        }
        return available;
    }

    private static JsonArray attributeData(List<String[]> attributes) {
        JsonArray data = new JsonArray();
        for (String[] pair : attributes) {
            JsonObject item = new JsonObject();
            item.addProperty("name", pair[0]);
            item.addProperty("value", pair[1]);
            data.add(item);
        }
        return data;
    }

    private static boolean isUniqueInDocument(Document document, String css, Element target) {
        try {
            Elements matches = document.select(css);
            return matches.size() == 1 && matches.get(0) == target;
        } catch (RuntimeException invalidSelector) {
            return false;
        }
    }

    private static boolean attributesUniquelyIdentify(
            Document document, String tag, List<String[]> attributes, Element target) {
        List<Element> matches = matchingElements(document, tag, attributes);
        return matches.size() == 1 && matches.get(0) == target;
    }

    private static int positionByAttributes(
            Document document, String tag, List<String[]> attributes, Element target) {
        int index = matchingElements(document, tag, attributes).indexOf(target);
        if (index < 0) throw new IllegalArgumentException("Generated attributes do not identify the target control");
        return index + 1;
    }

    private static List<Element> matchingElements(
            Document document, String tag, List<String[]> attributes) {
        List<Element> matches = new ArrayList<>();
        for (Element candidate : document.getElementsByTag(tag)) {
            boolean matching = true;
            for (String[] pair : attributes) {
                if (!candidate.hasAttr(pair[0]) || !candidate.attr(pair[0]).trim().equals(pair[1])) {
                    matching = false;
                    break;
                }
            }
            if (matching) matches.add(candidate);
        }
        return matches;
    }

    private static int positionInDocument(Document document, String css, Element target) {
        Elements matches = document.select(css);
        int index = matches.indexOf(target);
        if (index < 0) throw new IllegalArgumentException("Generated selector does not identify the target control");
        return index + 1;
    }

    /** Produces a CSS selector that is unique in the complete pasted fragment. */
    private static String positionalCss(Document document, Element target, String targetBase) {
        String selector = targetBase + ":nth-of-type(" + sameTagSiblingPosition(target) + ")";
        if (isUniqueInDocument(document, selector, target)) return selector;

        Element ancestor = target.parent();
        while (ancestor != null && !(ancestor instanceof Document)) {
            String segment = ancestor.tagName() + ":nth-of-type(" + sameTagSiblingPosition(ancestor) + ")";
            selector = segment + " > " + selector;
            if (isUniqueInDocument(document, selector, target)) return selector;
            ancestor = ancestor.parent();
        }
        return selector;
    }

    private static int sameTagSiblingPosition(Element element) {
        Element parent = element.parent();
        if (parent == null) return 1;
        int position = 0;
        for (Element sibling : parent.children()) {
            if (!sibling.tagName().equals(element.tagName())) continue;
            position++;
            if (sibling == element) return position;
        }
        return 1;
    }

    private static String attributeValue(Element element, String attribute) {
        if (!element.hasAttr(attribute)) return null;
        String value = element.attr(attribute).trim();
        return value.isEmpty() ? null : value;
    }

    private static String buildCss(String tag, List<String[]> attributes) {
        StringBuilder selector = new StringBuilder(tag);
        for (String[] pair : attributes) {
            selector.append('[')
                    .append(pair[0])
                    .append('=')
                    .append(cssLiteral(pair[1]))
                    .append(']');
        }
        return selector.toString();
    }

    private static String buildXpath(String tag, List<String[]> attributes) {
        StringBuilder selector = new StringBuilder("//").append(tag);
        for (String[] pair : attributes) {
            selector.append("[@")
                    .append(pair[0])
                    .append('=')
                    .append(xpathLiteral(pair[1]))
                    .append(']');
        }
        return selector.toString();
    }

    /** Escapes a CSS quoted string, including slash, quote, control, and newline characters. */
    private static String cssLiteral(String value) {
        char quote = value.indexOf('\'') >= 0 && value.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder escaped = new StringBuilder().append(quote);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == quote) {
                // Prefer the delimiter absent from the value. Jsoup and browser CSS parsers then
                // agree on the ordinary escaped backslash without also having to decode an
                // escaped quote of the opposite kind.
                escaped.append('\\').append(character);
            } else if (character == '\n' || character == '\r' || character == '\f' || character == 0) {
                escaped.append('\\').append(Integer.toHexString(character)).append(' ');
            } else {
                escaped.append(character);
            }
        }
        return escaped.append(quote).toString();
    }

    private static String xpathLiteral(String value) {
        if (!value.contains("'")) return "'" + value + "'";
        if (!value.contains("\"")) return '"' + value + '"';
        StringBuilder expression = new StringBuilder("concat(");
        String[] parts = value.split("'", -1);
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) expression.append(", \"'\", ");
            expression.append('\'').append(parts[index]).append('\'');
        }
        return expression.append(')').toString();
    }

    private static String describeChosen(List<String[]> attributes) {
        List<String> names = new ArrayList<>();
        for (String[] pair : attributes) names.add(pair[0]);
        return String.join(" + ", names);
    }

    private static String controlKind(Element element) {
        String tag = element.tagName().toLowerCase();
        String type = element.hasAttr("type") ? element.attr("type").toLowerCase() : "";
        String role = element.hasAttr("role") ? element.attr("role").toLowerCase() : "";
        if ("select".equals(tag) || "mat-select".equals(tag)
                || "listbox".equals(role) || "combobox".equals(role)) return "select";
        if ("textarea".equals(tag)) return "text-area";
        if ("button".equals(tag) || "button".equals(role)) return "button";
        if (element.hasAttr("contenteditable")) return "editable";
        if ("input".equals(tag)) {
            if ("checkbox".equals(type)) return "checkbox";
            if ("radio".equals(type)) return "radio";
            if ("button".equals(type) || "submit".equals(type)) return "button";
            return "text-input";
        }
        return tag;
    }

    private static String label(Element element) {
        for (String attribute : new String[] {"aria-label", "placeholder"}) {
            String value = attributeValue(element, attribute);
            if (value != null) return truncate(value);
        }
        String own = element.ownText().trim();
        return truncate(own.isEmpty() ? element.text().trim() : own);
    }

    private static String truncate(String value) {
        return value.length() <= 60 ? value : value.substring(0, 60) + "...";
    }

    private static String someText(Element element) {
        for (String attribute : new String[] {"aria-label", "placeholder", "title", "alt", "value"}) {
            String value = cleanText(attributeValue(element, attribute));
            if (!value.isBlank()) return truncate(value, 160);
        }
        String own = cleanText(element.ownText());
        if (!own.isBlank()) return truncate(own, 160);
        String text = cleanText(element.text());
        if (!text.isBlank()) return truncate(text, 160);

        String semantic = semanticAttributeText(element);
        return semantic.isBlank() ? "" : truncate(semantic, 160);
    }

    private static String definedName(Element element, String someText, Set<String> usedDefinedNames) {
        String base = !blank(someText) ? someText : semanticAttributeText(element);
        if (blank(base)) base = controlKind(element);
        String slug = TextSimilarity.slug(base);
        String unique = TextSimilarity.uniquify(slug, usedDefinedNames);
        usedDefinedNames.add(unique);
        return unique;
    }

    private static String semanticAttributeText(Element element) {
        for (String attribute : new String[] {
            "formcontrolname", "name", "id", "data-testid", "data-test", "test-id", "role", "type"
        }) {
            String value = attributeValue(element, attribute);
            String human = humanizeSemanticAttribute(value);
            if (!human.isBlank()) return human;
        }
        return "";
    }

    private static String humanizeSemanticAttribute(String value) {
        if (value == null || value.isBlank()) return "";
        String candidate = value.trim();
        int segmentStart = Math.max(
                Math.max(candidate.lastIndexOf('.'), candidate.lastIndexOf('/')),
                candidate.lastIndexOf(':'));
        if (segmentStart >= 0 && segmentStart + 1 < candidate.length()) {
            candidate = candidate.substring(segmentStart + 1);
        }
        candidate = candidate.replaceAll("-?\\d+$", "");
        return cleanText(TextSimilarity.humanize(candidate));
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("[:*!]+$", "")
                .trim();
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum) + "...";
    }

    /** Must remain byte-for-byte compatible with the React PageScannerLocator element key. */
    private static String elementKey(ElementDTO element) {
        return String.valueOf(element.getId())
                + "||"
                + Objects.toString(element.getXPath(), "")
                + "||"
                + Objects.toString(element.getTagName(), "");
    }

    private static boolean looksLikeXPath(String xpath) {
        return xpath.indexOf('\0') < 0
                && (xpath.startsWith("/") || xpath.startsWith("(") || xpath.startsWith("./"));
    }

    private static boolean isValidXPath(String xpath) {
        try {
            XPathFactory.newInstance().newXPath().compile(xpath);
            return true;
        } catch (XPathExpressionException invalidXPath) {
            return false;
        }
    }

    private static String optionalString(JsonObject body, String field) {
        if (body == null || !body.has(field)) return "";
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return "";
        try {
            return value.getAsString();
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    private static String requireString(JsonObject body, String field, int maximum, String message) {
        String value = optionalString(body, field).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        if (value.length() > maximum) throw new IllegalArgumentException(field + " is too long");
        return value;
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static JsonObject baseResponse(String requestId) {
        JsonObject response = new JsonObject();
        response.addProperty("requestId", requestId == null ? "" : requestId);
        return response;
    }

    private static JsonObject generationFailure(JsonObject response, String warning) {
        response.addProperty("ok", false);
        response.addProperty("warning", warning);
        response.add("controls", new JsonArray());
        return response;
    }

    interface Persistence {
        PersistenceResult persist(
                int homeBankingId,
                int botJobId,
                Integer homeUrlId,
                String pageUrl,
                ElementDTO element) throws Exception;
    }

    record PersistenceResult(int inserted, int updated) {
        int affectedRows() {
            return inserted + updated;
        }
    }

    private static final class DatabasePersistence implements Persistence {
        @Override
        public PersistenceResult persist(
                int homeBankingId,
                int botJobId,
                Integer homeUrlId,
                String pageUrl,
                ElementDTO element) throws Exception {
            int updated = PerformDataBase.getInstance()
                    .updateScannedElementCustomXPathStrict(
                            homeBankingId, botJobId, pageUrl, element);
            return new PersistenceResult(0, updated);
        }
    }
}
