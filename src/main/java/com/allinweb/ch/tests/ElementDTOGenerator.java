package com.allinweb.ch.tests;

import com.allinweb.ch.component.model.AttributeData;
import com.allinweb.ch.component.model.ElementDTO;
import java.util.Random;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ElementDTOGenerator {
    private static final String[] TAGS = {
        "div",
        "span",
        "input",
        "button",
        "a",
        "p",
        "h1",
        "h2",
        "h3",
        "ul",
        "li",
        "table",
        "tr",
        "td",
        "form",
        "label",
        "select",
        "option",
        "textarea",
        "img"
    };
    private static final String[] TYPES = {
        "text", "password", "checkbox", "radio", "submit", "reset", "hidden", "email", "number", "date"
    };
    private static final String[] COMMON_HTML_ATTRIBUTES = {
        "id",
        "class",
        "name",
        "href",
        "src",
        "alt",
        "title",
        "style",
        "placeholder",
        "value",
        "disabled",
        "checked",
        "readonly",
        "maxlength",
        "minlength",
        "required",
        "type",
        "role",
        "aria-label"
    };
    private static final String[] COMMON_TEXTS = {
        "Click Me", "Submit", "Username", "Email", "Search", "Password", "Welcome", "Home", "Profile", "Settings"
    };
    private static final Random RANDOM = new Random();

    public static ObservableList<ElementDTO> getElementDTO() {
        ObservableList<ElementDTO> elements = FXCollections.observableArrayList();

        for (int i = 0; i < 50; i++) {
            String tagName = TAGS[RANDOM.nextInt(TAGS.length)];
            String typeElement = (tagName.equals("input")) ? TYPES[RANDOM.nextInt(TYPES.length)] : "";
            String xPath = "//" + tagName + "[" + (i + 1) + "]";
            String someText = (tagName.equals("button")
                            || tagName.equals("a")
                            || tagName.equals("label")
                            || tagName.startsWith("h"))
                    ? COMMON_TEXTS[RANDOM.nextInt(COMMON_TEXTS.length)]
                    : "";
            String attribId = "id_" + i;
            String attribName = "name_" + i;
            String coords = RANDOM.nextInt(1000) + "," + RANDOM.nextInt(800);

            AttributeData[] attributeData = generateRandomAttributes();

            ElementDTO element = new ElementDTO(
                    typeElement,
                    tagName,
                    xPath,
                    someText,
                    attribId,
                    attribName,
                    coords,
                    attributeData,
                    "//custom/xpath[" + i + "]",
                    "//iframe/xpath[" + i + "]",
                    "attribute_value_" + i,
                    "attribute_type_" + i,
                    "search_value_" + i);

            elements.add(element);
        }
        return elements;
    }

    private static AttributeData[] generateRandomAttributes() {
        int numAttributes = RANDOM.nextInt(5) + 1; // 1 to 5 attributes per element
        AttributeData[] attributes = new AttributeData[numAttributes];

        for (int j = 0; j < numAttributes; j++) {
            String attributeName = COMMON_HTML_ATTRIBUTES[RANDOM.nextInt(COMMON_HTML_ATTRIBUTES.length)];
            String attributeValue = "value_" + RANDOM.nextInt(100); // Example: value_42
            attributes[j] = new AttributeData(attributeName, attributeValue);
        }
        return attributes;
    }
}
