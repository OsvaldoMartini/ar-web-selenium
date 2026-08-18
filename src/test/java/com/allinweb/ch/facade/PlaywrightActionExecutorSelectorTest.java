package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaywrightActionExecutorSelectorTest {

    @Test
    void keepsTestIdAndDataTestIdAsDistinctLocatorContracts() {
        List<String> literal = PlaywrightActionExecutor.selectorsFor(referenceOnly("test-id", "next-0"));
        List<String> data = PlaywrightActionExecutor.selectorsFor(referenceOnly("data-testid", "next-0"));

        assertTrue(literal.contains("[test-id=\"next-0\"]"));
        assertFalse(literal.contains("[data-testid=\"next-0\"]"));
        assertTrue(data.contains("[data-testid=\"next-0\"]"));
        assertFalse(data.contains("[test-id=\"next-0\"]"));
    }

    @Test
    void attrDataUsesTheExactCustomAttributeInsteadOfSubstringIdMatching() {
        List<String> selectors =
                PlaywrightActionExecutor.selectorsFor(referenceOnly("AttrData:martini-id", "same"));

        assertTrue(selectors.contains("[martini-id=\"same\"]"));
        assertFalse(selectors.contains("#same"));
    }

    private static InstructionLoad referenceOnly(String type, String value) {
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType(type);
        reference.setValue(value);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setReferenceLoadDTOList(List.of(reference));
        return instruction;
    }
}
