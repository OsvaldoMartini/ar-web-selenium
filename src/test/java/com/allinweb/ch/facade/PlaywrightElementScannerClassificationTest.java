package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.facade.actions.ElementDtoMapper;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PlaywrightElementScannerClassificationTest {

    @Test
    void radioInputFromPageScannerStaysInputNotButton() throws Exception {
        AttributeData[] attrs = {new AttributeData("type", "radio"), new AttributeData("control.kind", "radio-option")};

        assertEquals("input", classifyTag("input", "button", attrs));

        ElementDTO dto = new ElementDTO();
        dto.setTagName("input");
        dto.setTypeElement("input");
        dto.setAttributeType("radio-option");
        dto.setAttributeData(attrs);
        dto.setSomeText("email_2");
        dto.setXPath("/html/body/form/label/input[1]");

        TargetElement target = ElementDtoMapper.defineSearchReturn(dto, null);

        assertEquals(WebElementTagNameEnum.INPUT, target.getTagType());
        assertEquals("input", target.getTagName());

        TargetElement retyped = new TargetElement();
        retyped.setTagName("input");
        retyped.setAttributeType("radio-option");
        ElementDtoMapper.defineTagType(retyped);

        assertEquals(WebElementTagNameEnum.INPUT, retyped.getTagType());
    }

    @Test
    void selectOptionCloneRemainsButton() throws Exception {
        AttributeData[] attrs = {
            new AttributeData("original-tag", "option"), new AttributeData("control.kind", "select-option")
        };

        assertEquals("button", classifyTag("button", "button", attrs));
    }

    @Test
    void textareaStaysInputAndInputSearchIncludesTextarea() throws Exception {
        assertEquals("input", classifyTag("textarea", "input", new AttributeData[0]));

        String selector = buildSelector(new String[] {"input"});

        org.junit.jupiter.api.Assertions.assertTrue(selector.contains("input"));
        org.junit.jupiter.api.Assertions.assertTrue(selector.contains("textarea"));
        org.junit.jupiter.api.Assertions.assertTrue(selector.contains("[role='textbox']"));
    }

    private static String classifyTag(String tagName, String scannedTypeElement, AttributeData[] attrs)
            throws Exception {
        Method method = PlaywrightElementScanner.class.getDeclaredMethod(
                "classifyTag", String.class, String.class, AttributeData[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, tagName, scannedTypeElement, attrs);
    }

    private static String buildSelector(String[] searchTerms) throws Exception {
        Method method = PlaywrightElementScanner.class.getDeclaredMethod("buildSelector", String[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, (Object) searchTerms);
    }
}
