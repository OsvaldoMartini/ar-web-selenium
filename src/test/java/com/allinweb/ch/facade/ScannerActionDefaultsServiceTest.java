package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.TargetElement;
import org.junit.jupiter.api.Test;

class ScannerActionDefaultsServiceTest {
    private final ScannerActionDefaultsService service = new ScannerActionDefaultsService();

    @Test
    void seleniumClickableDefaultsToClick() {
        assertEquals(ScannerActionDefaultsService.Decision.clickAction(), service.decide(target("div"), true));
    }

    @Test
    void attributePathClickableDefaultsToClick() {
        TargetElement target = target("div");
        target.setAttributeData(new AttributeData[] {attribute("/button[1]")});

        assertEquals(ScannerActionDefaultsService.Decision.clickAction(), service.decide(target, false));
    }

    @Test
    void inputOrSelectDefaultsToInputWhenNotClickable() {
        assertEquals(ScannerActionDefaultsService.Decision.inputAction(), service.decide(target("input"), false));
        assertEquals(ScannerActionDefaultsService.Decision.inputAction(), service.decide(target("select"), false));
    }

    @Test
    void plainElementDefaultsToOutput() {
        assertEquals(ScannerActionDefaultsService.Decision.outputAction(), service.decide(target("span"), false));
    }

    @Test
    void clonedTargetsUseForcedTagType() {
        TargetElement button = cloned(WebElementTagNameEnum.BUTTON);
        TargetElement input = cloned(WebElementTagNameEnum.INPUT);
        TargetElement output = cloned(WebElementTagNameEnum.OUTPUT);

        assertEquals(ScannerActionDefaultsService.Decision.clickAction(), service.decide(button, false));
        assertEquals(ScannerActionDefaultsService.Decision.inputAction(), service.decide(input, false));
        assertEquals(ScannerActionDefaultsService.Decision.outputAction(), service.decide(output, false));
    }

    private static TargetElement target(String tagName) {
        TargetElement target = new TargetElement();
        target.setTagName(tagName);
        return target;
    }

    private static TargetElement cloned(WebElementTagNameEnum tagType) {
        TargetElement target = target("div");
        target.setCloned(true);
        target.setTagType(tagType);
        return target;
    }

    private static AttributeData attribute(String value) {
        AttributeData attribute = new AttributeData();
        attribute.setValue(value);
        return attribute;
    }
}
