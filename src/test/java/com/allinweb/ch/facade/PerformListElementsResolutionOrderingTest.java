package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformListElementsResolutionOrderingTest {

    @AfterEach
    void clearSharedPickerCache() {
        PerformLists.getInstance().resetListElements();
    }

    @Test
    void pickerCacheIsBuiltFromResolvedElementsNotPreResolutionSnapshot(@TempDir Path dir) throws Exception {
        PerformLists lists = PerformLists.getInstance();
        lists.resetListElements();

        ElementDTO option = new ElementDTO();
        option.setTagName("mat-option");
        option.setTypeElement("button");
        option.setAttribId("mat-option-83");
        option.setXPath("//mat-option[@test-id='forex.credit-currency-CHF']");
        option.setCssSelector("mat-option[test-id=\"forex.credit-currency-CHF\"]");
        option.setCoordinates("10.00,20.00");
        option.setSomeText("CHF");
        option.setAttributeType("select-option");
        option.setAttributeData(new AttributeData[] {
            new AttributeData("test-id", "forex.credit-currency-CHF"),
            new AttributeData("role", "option"),
            new AttributeData("control.kind", "select-option"),
            new AttributeData("option-text", "CHF"),
            new AttributeData("option-value", "CHF")
        });

        Path ocr = dir.resolve("ocr-correlation-HP.json");
        Files.writeString(
                ocr,
                "[{\"xpath\":\"//mat-option[@test-id='forex.credit-currency-CHF']\","
                        + "\"matchQuality\":\"EXACT_CONTAIN\",\"ocrText\":\"CHE\"}]",
                StandardCharsets.UTF_8);

        PerformListElements.resolveAndPopulateTargetCache(new ElementDTO[] {option}, ocr, null);

        assertEquals("CHF", option.getSomeText());
        assertEquals("chf", option.getDefinedName());
        assertEquals(1, lists.getListTargetElements().size());
        TargetElement cached = lists.getListTargetElements().get(0);
        assertEquals("CHF", cached.getSomeText());
        assertEquals("chf", cached.getDefinedName());
    }
}
