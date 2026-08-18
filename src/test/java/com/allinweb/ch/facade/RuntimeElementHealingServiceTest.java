package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.ScannedElement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeElementHealingServiceTest {

    @Test
    void exactXpathOutranksASharedCssSelector() {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setXpath("/html/body/main/a/span");
        instruction.setCssSelector("span.btn-text");

        ScannedElement exact = new ScannedElement();
        exact.setXPath("/html/body/main/a/span");
        exact.setCssSelector("span.btn-text");

        ScannedElement broad = new ScannedElement();
        broad.setXPath("/html/body/footer/a/span");
        broad.setCssSelector("span.btn-text");

        assertEquals(3, RuntimeElementHealingService.locatorMatchStrength(
                instruction, exact, Map.of()));
        assertEquals(1, RuntimeElementHealingService.locatorMatchStrength(
                instruction, broad, Map.of()));
    }

    @Test
    void stableIdentityOutranksCssFallback() {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setCssSelector("span.btn-text");
        ReferenceLoadDTO id = new ReferenceLoadDTO();
        id.setReferenceType("locator.best.byid");
        id.setValue("personal-link");
        instruction.setReferenceLoadDTOList(List.of(id));

        ScannedElement stable = new ScannedElement();
        stable.setAttribId("personal-link");
        stable.setCssSelector("span.btn-text");

        assertEquals(2, RuntimeElementHealingService.locatorMatchStrength(
                instruction, stable, Map.of()));
    }

    @Test
    void configuredClientTestIdOutranksXpath() {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setXpath("//button[9]");
        instruction.setReferenceLoadDTOList(List.of(
                reference("AttrData:automation.test-id.attribute", "qa-hook"),
                reference("AttrData:qa-hook", "continue")));
        ScannedElement row = new ScannedElement();
        row.setXPath("//button[2]");

        assertEquals(4, RuntimeElementHealingService.locatorMatchStrength(
                instruction, row, Map.of(
                        "automation.test-id.attribute", "qa-hook",
                        "qa-hook", "continue")));
    }

    private static ReferenceLoadDTO reference(String type, String value) {
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType(type);
        reference.setValue(value);
        return reference;
    }
}
