package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(selector.contains("input"));
        assertTrue(selector.contains("textarea"));
        assertTrue(selector.contains("[role='textbox']"));
    }

    @Test
    void attributeSearchSyntaxBuildsSafeSelectorsWithoutLeakingPseudoCss() throws Exception {
        String selector = buildSelector(new String[] {
            "input", "attr:test-id", "attr:qa-hook", "attr:qa-hook='save'"
        });

        assertTrue(selector.contains("[test-id]"));
        assertTrue(selector.contains("[qa-hook]"));
        assertTrue(selector.contains("[qa-hook=\"save\"]"));
        assertTrue(selector.contains("[data-testid]"));
        assertFalse(selector.contains("attr:"));
        assertEquals(
                java.util.List.of("test-id", "qa-hook"),
                PlaywrightElementScanner.configuredAttributeNames(
                        new String[] {"attr:test-id", "ATTR:qa-hook", "attr:qa-hook=save"}));
    }

    @Test
    void unsafeAttributeSearchFallsBackToDefaultSelector() throws Exception {
        String selector = buildSelector(new String[] {"attr:test-id], button"});

        assertFalse(selector.contains("attr:"));
        assertFalse(selector.contains("test-id], button"));
        assertTrue(selector.contains("[test-id]"));
        assertTrue(selector.contains("button"));
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
