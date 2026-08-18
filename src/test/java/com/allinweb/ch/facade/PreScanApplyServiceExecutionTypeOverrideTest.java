package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import org.junit.jupiter.api.Test;

class PreScanApplyServiceExecutionTypeOverrideTest {

    @Test
    void outputDomElementCanBeAppliedAsInputWithoutChangingItsLocatorTag() {
        ElementDTO element = element("div", "output", "account_number");
        element.setExecutionTypeOverride("INPUT");

        InstructionLoad instruction = PreScanApplyService.getInstance().buildTestInstruction(element);

        assertEquals("I:account_number", instruction.getActions());
        assertEquals("div", instruction.getTagName());
        assertEquals("//div[@id='account_number']", instruction.getXpath());
    }

    @Test
    void buttonDomElementCanBeAppliedAsOutputWithoutChangingItsLocatorTag() {
        ElementDTO element = element("button", "button", "balance");
        element.setExecutionTypeOverride("OUTPUT");

        InstructionLoad instruction = PreScanApplyService.getInstance().buildTestInstruction(element);

        assertEquals("O:balance", instruction.getActions());
        assertEquals("button", instruction.getTagName());
    }

    @Test
    void outputDomElementCanBeAppliedAsClick() {
        ElementDTO element = element("span", "output", "continue");
        element.setExecutionTypeOverride("CLICK");

        InstructionLoad instruction = PreScanApplyService.getInstance().buildTestInstruction(element);

        assertEquals("C", instruction.getActions());
        assertEquals("span", instruction.getTagName());
    }

    @Test
    void invalidOverrideFailsClosed() {
        ElementDTO element = element("input", "input", "username");
        element.setExecutionTypeOverride("COMMAND");

        assertNull(PreScanApplyService.getInstance().buildTestInstruction(element));
    }

    @Test
    void copyOperationsPreserveTheTransientOverride() {
        ElementDTO element = element("input", "input", "username");
        element.setExecutionTypeOverride("CLICK");

        assertEquals("CLICK", new ElementDTO(element).getExecutionTypeOverride());
        assertEquals("CLICK", element.deepCopy().getExecutionTypeOverride());
    }

    private static ElementDTO element(String tagName, String typeElement, String definedName) {
        ElementDTO element = new ElementDTO();
        element.setTagName(tagName);
        element.setTypeElement(typeElement);
        element.setXPath("//" + tagName + "[@id='" + definedName + "']");
        element.setSomeText(definedName);
        element.setDefinedName(definedName);
        return element;
    }
}
