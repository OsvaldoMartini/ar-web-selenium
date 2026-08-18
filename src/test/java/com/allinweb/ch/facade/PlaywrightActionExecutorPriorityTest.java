package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaywrightActionExecutorPriorityTest {
    @Test
    void clientTestIdPrecedesXpathCssAndOrdinaryAttributes() {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setXpath("//button[@id='continue']");
        instruction.setCssSelector("button#continue");
        instruction.setReferenceLoadDTOList(List.of(
                reference("AttrData:automation.test-id.attribute", "qa-hook"),
                reference("AttrData:qa-hook", "continue-action"),
                reference("AttrData:title", "Continue")));

        List<String> selectors = PlaywrightActionExecutor.selectorsFor(instruction);

        assertEquals("[qa-hook=\"continue-action\"]", selectors.get(0));
        assertEquals("xpath=//button[@id='continue']", selectors.get(1));
        assertEquals("button#continue", selectors.get(2));
        assertEquals("[title=\"Continue\"]", selectors.get(3));
    }

    private static ReferenceLoadDTO reference(String type, String value) {
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType(type);
        reference.setValue(value);
        return reference;
    }
}
