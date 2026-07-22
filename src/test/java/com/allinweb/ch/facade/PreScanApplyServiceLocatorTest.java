package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import org.junit.jupiter.api.Test;

class PreScanApplyServiceLocatorTest {

    @Test
    void generatedCustomXpathBecomesTheInstructionExecutionXpath() {
        ElementDTO element = element("//button[@name='continue']");
        element.setCustomXPath("//button[@test-id='primary-next']");

        InstructionLoad instruction = PreScanApplyService.getInstance().buildTestInstruction(element);

        assertEquals("//button[@test-id='primary-next']", instruction.getXpath());
    }

    @Test
    void scannerXpathRemainsTheFallbackWhenThereIsNoCustomXpath() {
        ElementDTO element = element("//button[@name='continue']");

        InstructionLoad instruction = PreScanApplyService.getInstance().buildTestInstruction(element);

        assertEquals("//button[@name='continue']", instruction.getXpath());
    }

    private static ElementDTO element(String xpath) {
        ElementDTO element = new ElementDTO();
        element.setTagName("button");
        element.setTypeElement("button");
        element.setXPath(xpath);
        element.setSomeText("Continue");
        element.setDefinedName("continue");
        return element;
    }
}
