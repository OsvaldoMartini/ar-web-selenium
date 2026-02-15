package com.allinweb.ch.vision;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;

public final class VisionElementMapper {

    private VisionElementMapper() {
        // utility
    }

    public static ElementDTO toElementDTO(VisionElement ve, String visionXPathBase) {
        ElementDTO dto = new ElementDTO();

        dto.setId(ve.getId());
        dto.setSomeText(ve.getText());
        dto.setCoordinates(String.format("%.2f,%.2f", ve.getDeviceX(), ve.getDeviceY()));

        // base typeElement/tagName
        dto.setTypeElement("tagName-Found");
        dto.setTagName(mapTypeToTagName(ve.getType()));

        String textValue = escapeXpath(normalizeNull(ve.getText()));
        int idx = (ve.getId() != null ? ve.getId() : 0) + 1000;
        String specialXpath = "(" + visionXPathBase + "[@text=\"" + textValue + "\"])[" + idx + "]";
        dto.setXPath(specialXpath);
        dto.setAttribId(specialXpath);

        dto.setAttribName("");
        dto.setCustomXPath("");
        dto.setIFrameXPath("");
        dto.setShadowHost("");
        dto.setShadowRoot("false");
        dto.setNestedShadow("false");
        dto.setCssSelector("");
        dto.setAttributeValue("");
        dto.setAttributeType("");
        dto.setSearchAttributeValue("");
        dto.setAutoScroll("");
        dto.setAutoEnter("");

        if (ve.getAttributes() != null && !ve.getAttributes().isEmpty()) {
            AttributeData[] attrs = ve.getAttributes().entrySet().stream()
                    .map(e -> new AttributeData(e.getKey(), String.valueOf(e.getValue())))
                    .toArray(AttributeData[]::new);
            dto.setAttributeData(attrs);
        }

        return dto;
    }

    private static String mapTypeToTagName(UiElementType type) {
        // TagName is a Selenium concept; for pure vision we just map roughly
        switch (type) {
            case BUTTON:
                return "button";
            case INPUT:
                return "input";
            case CHECKBOX:
                return "input-checkbox";
            case RADIO:
                return "input-radio";
            case TOGGLE:
                return "switch";
            case ICON:
            case IMAGE:
                return "img";
            case TEXT:
            case UNKNOWN:
            default:
                return "span";
        }
    }

    private static String normalizeNull(String s) {
        return s == null ? "" : s;
    }

    private static String escapeXpath(String s) {
        if (s == null) return "";
        // simple escape; if you already have a better impl, reuse it
        return s.replace("\"", "\\\"");
    }

    public static void overrideClassAttribute(ElementDTO dto, String newClassValue) {
        AttributeData[] attrs = dto.getAttributeData();
        if (attrs == null) return;

        for (AttributeData a : attrs) {
            if ("class".equalsIgnoreCase(a.getName())) {
                a.setValue(newClassValue);
                return;
            }
        }
        // if not found, you could append a new AttributeData if you want
    }
}
