package com.allinweb.ch.util;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import java.util.*;

public final class ElementDetailsMatcher {

    private static final String ATTR_PREFIX = "AttrData:";

    // Java 8-safe (replace Set.of)
    private static final Set<String> HIGH_VALUE_KEYS =
            new HashSet<>(Arrays.asList("id", "resource-id", "name", "class", "text", "content-desc", "href", "type"));

    private ElementDetailsMatcher() {
        // utility class
    }

    /** ✅ Overload: accept List<ElementDTO> */
    public static ElementDTO findMatchingElement(List<ElementDTO> elementDetails, InstructionLoad currentInstruction) {
        if (elementDetails == null || elementDetails.isEmpty()) return null;
        return findMatchingElement(elementDetails.toArray(new ElementDTO[0]), currentInstruction);
    }

    /** Existing array version (unchanged logic) */
    public static ElementDTO findMatchingElement(ElementDTO[] elementDetails, InstructionLoad currentInstruction) {
        if (elementDetails == null || elementDetails.length == 0 || currentInstruction == null) {
            return null;
        }

        String instrTag = normalize(currentInstruction.getTagName());
        Map<String, String> instrAttrs = normalizeAttrsFromInstruction(currentInstruction);

        ElementDTO best = null;
        int bestScore = Integer.MIN_VALUE;

        for (ElementDTO el : elementDetails) {
            if (el == null) continue;

            String elTag = normalize(el.getTagName());
            Map<String, String> elAttrs = normalizeAttrs(el.getAttributeData());

            int score = scoreMatch(instrTag, instrAttrs, elTag, elAttrs);

            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }

        return bestScore >= 20 ? best : null;
    }

    // ------------------------- scoring -------------------------

    private static int scoreMatch(
            String instrTag, Map<String, String> instrAttrs, String elTag, Map<String, String> elAttrs) {

        int score = 0;

        if (!isBlank(instrTag) && !isBlank(elTag)) {
            if (instrTag.equalsIgnoreCase(elTag)) score += 100;
            else score -= 50;
        } else if (!isBlank(instrTag) || !isBlank(elTag)) {
            score -= 5;
        }

        score += matchAttr(instrAttrs, elAttrs, "id", 60);
        score += matchAttr(instrAttrs, elAttrs, "resource-id", 60);
        score += matchAttr(instrAttrs, elAttrs, "name", 40);
        score += matchAttr(instrAttrs, elAttrs, "class", 30);
        score += matchAttr(instrAttrs, elAttrs, "text", 25);
        score += matchAttr(instrAttrs, elAttrs, "content-desc", 25);
        score += matchAttr(instrAttrs, elAttrs, "href", 20);
        score += matchAttr(instrAttrs, elAttrs, "type", 15);

        for (Map.Entry<String, String> e : instrAttrs.entrySet()) {
            String k = e.getKey();
            if (isHighValueKey(k)) continue;
            String v = e.getValue();
            if (v == null) continue;
            if (v.equalsIgnoreCase(elAttrs.get(k))) score += 5;
        }

        return score;
    }

    private static int matchAttr(Map<String, String> a, Map<String, String> b, String key, int weight) {
        String va = a.get(key);
        String vb = b.get(key);
        if (isBlank(va) || isBlank(vb)) return 0;
        return va.equalsIgnoreCase(vb) ? weight : -10;
    }

    private static boolean isHighValueKey(String k) {
        if (k == null) return false;
        return HIGH_VALUE_KEYS.contains(k.toLowerCase(Locale.ROOT));
    }

    // ------------------------- attribute extraction -------------------------

    public static Map<String, String> normalizeAttrs(AttributeData[] arr) {
        Map<String, String> out = new HashMap<>();
        if (arr == null) return out;

        for (AttributeData ad : arr) {
            if (ad == null) continue;
            String k = normalizeKey(ad.getName());
            String v = normalize(ad.getValue());
            if (isBlank(k) || isBlank(v)) continue;
            out.put(k, v);
        }

        alias(out, "resource-id", "id");
        return out;
    }

    public static Map<String, String> normalizeAttrsFromInstruction(InstructionLoad instr) {
        Map<String, String> out = new HashMap<>();
        if (instr == null) return out;

        List<ReferenceLoadDTO> refs = instr.getReferenceLoadDTOList();
        if (refs != null) {
            for (ReferenceLoadDTO r : refs) {
                if (r == null || r.getReferenceType() == null || r.getValue() == null) continue;
                String t = r.getReferenceType().trim();
                if (t.startsWith(ATTR_PREFIX)) {
                    String k = normalizeKey(t.substring(ATTR_PREFIX.length()).trim());
                    String v = normalize(r.getValue());
                    if (!isBlank(k) && !isBlank(v)) out.put(k, v);
                }
            }
        }

        alias(out, "resource-id", "id");
        return out;
    }

    private static void alias(Map<String, String> m, String primary, String alias) {
        if (!m.containsKey(primary) && m.containsKey(alias)) {
            m.put(primary, m.get(alias));
        }
        if (!m.containsKey(alias) && m.containsKey(primary)) {
            m.put(alias, m.get(primary));
        }
    }

    // ------------------------- normalization helpers -------------------------

    public static String normalize(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
