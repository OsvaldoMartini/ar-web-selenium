package com.allinweb.ch.util;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import com.google.common.base.Strings;
import java.util.List;

public final class InstructionLoadMatcher {

    private InstructionLoadMatcher() {
        // utility class
    }

    public static TargetElement findMatchingTargetElementByXPath(
            List<TargetElement> currentElements, InstructionLoad currentInstruction) {

        if (currentElements == null || currentElements.isEmpty() || currentInstruction == null) {
            return null;
        }

        String targetXPath = normalize(currentInstruction.getXpath());
        if (isBlank(targetXPath)) {
            return null;
        }

        boolean isShadowInstruction = !Strings.isNullOrEmpty(currentInstruction.getShadowHost())
                && !Strings.isNullOrEmpty(currentInstruction.getCssSelector());

        // Only needed if shadow instruction
        String targetCssSelector = isShadowInstruction ? normalize(currentInstruction.getCssSelector()) : null;

        for (TargetElement el : currentElements) {
            if (el == null) continue;

            // 1) XPath must always match
            if (!equalsIgnoreIgnoreBlank(targetXPath, el.getXPath())) {
                continue;
            }

            // 2) If shadow instruction, also require CSS selector match
            if (isShadowInstruction) {
                if (equalsIgnoreIgnoreBlank(targetCssSelector, el.getCssSelector())) {
                    return el;
                }
                continue;
            }

            // Non-shadow: XPath match is enough
            return el;
        }

        return null;
    }

    /** ✅ Overload: accept List<TargetElement> */
    public static TargetElement findMatchingTargetElement(
            List<TargetElement> currentElements, InstructionLoad currentInstruction) {

        if (currentElements == null || currentElements.isEmpty() || currentInstruction == null) {
            return null;
        }

        String targetName = normalize(currentInstruction.getName());

        // Nothing meaningful to match
        if (isBlank(targetName)) {
            return null;
        }

        for (TargetElement el : currentElements) {
            if (el == null) continue;

            if (equalsIgnoreIgnoreBlank(targetName, el.getDefinedName())) {
                return el;
            }
        }

        // Searches as Field "someText"
        for (TargetElement el : currentElements) {
            if (el == null) continue;

            if (equalsIgnoreIgnoreBlank(targetName, el.getSomeText())) {
                return el;
            }
        }

        // Searches  someText in AttributeData
        for (TargetElement el : currentElements) {
            if (el == null) continue;

            if (hasSomeTextAttribute(el, targetName)) {
                return el;
            }
        }

        // Searches  At Least Parts of someText in AttributeData
        for (TargetElement el : currentElements) {
            if (el == null) continue;

            if (hasSomeTextContainsAttribute(el, targetName)) {
                return el;
            }
        }

        return null;
    }

    private static boolean hasSomeTextAttribute(TargetElement el, String targetName) {
        if (el == null || isBlank(targetName)) return false;

        AttributeData[] attrs = el.getAttributeData();
        if (attrs == null || attrs.length == 0) return false;

        String target = normalize(targetName);

        for (AttributeData ad : attrs) {
            if (ad == null) continue;

            if ("someText".equalsIgnoreCase(normalize(ad.getName()))
                    && equalsIgnoreIgnoreBlank(target, ad.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSomeTextContainsAttribute(TargetElement el, String targetName) {
        if (el == null || isBlank(targetName)) return false;

        AttributeData[] attrs = el.getAttributeData();
        if (attrs == null || attrs.length == 0) return false;

        String target = normalize(targetName);

        for (AttributeData ad : attrs) {
            if (ad == null) continue;

            if ("sometext".equalsIgnoreCase(normalize(ad.getName())) && containsIgnoreCase(ad.getValue(), target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String source, String part) {
        if (isBlank(source) || isBlank(part)) return false;
        return normalize(source).contains(normalize(part));
    }

    /**
     * true if:
     * - expected is blank → ignore constraint
     * - otherwise equalsIgnoreCase after trim
     */
    private static boolean equalsIgnoreIgnoreBlank(String expected, String actual) {
        if (isBlank(expected)) return true;
        if (isBlank(actual)) return false;
        return expected.equalsIgnoreCase(normalize(actual));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
