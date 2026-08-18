package com.allinweb.ch.facade;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.TargetElement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScannerActionDefaultsService {
    private static final Pattern ATTRIBUTE_TAG_PATTERN = Pattern.compile("/([^/\\[]+)");

    public Decision decide(TargetElement target, boolean seleniumClickable) {
        if (target.getCloned() != null) {
            return clonedDecision(target.getTagType());
        }

        boolean tagClickable = hasClickableAttributeTag(target.getAttributeData());
        String tagName = target.getTagName() == null ? "" : target.getTagName().toLowerCase();
        boolean inputOrSelect = tagName.contains("input") || tagName.contains("select");

        if (tagClickable || seleniumClickable) {
            return Decision.clickAction();
        }
        if (inputOrSelect) {
            return Decision.inputAction();
        }
        return seleniumClickable ? Decision.clickAction() : Decision.outputAction();
    }

    private Decision clonedDecision(WebElementTagNameEnum tagType) {
        if (WebElementTagNameEnum.BUTTON.equals(tagType)) {
            return Decision.clickAction();
        }
        if (WebElementTagNameEnum.INPUT.equals(tagType)) {
            return Decision.inputAction();
        }
        if (WebElementTagNameEnum.OUTPUT.equals(tagType)) {
            return Decision.outputAction();
        }
        return new Decision(false, false, false);
    }

    private boolean hasClickableAttributeTag(AttributeData[] attributes) {
        if (attributes == null) {
            return false;
        }
        for (AttributeData attribute : attributes) {
            if (attribute == null || attribute.getValue() == null) {
                continue;
            }
            Matcher matcher = ATTRIBUTE_TAG_PATTERN.matcher(attribute.getValue());
            while (matcher.find()) {
                String tag = matcher.group(1);
                if ("a".equals(tag) || "button".equals(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record Decision(boolean click, boolean input, boolean output) {
        public static Decision clickAction() {
            return new Decision(true, false, false);
        }

        public static Decision inputAction() {
            return new Decision(false, true, false);
        }

        public static Decision outputAction() {
            return new Decision(false, false, true);
        }
    }
}
