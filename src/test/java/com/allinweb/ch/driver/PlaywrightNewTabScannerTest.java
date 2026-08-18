package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies execution adopts a popup and Page Scanner operations use that same active tab. */
class PlaywrightNewTabScannerTest {

    @BeforeEach
    void requirePlaywrightBrowser() {
        PlaywrightTestSupport.assumeBrowserLaunchAvailable();
    }

    @Test
    void ordinaryClickAndInputContinueOnCurrentTab() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("""
                    <input id="name" /><button id="save" onclick="this.dataset.clicked='yes'">Save</button>
                    """);
            InstructionLoad input = new InstructionLoad();
            input.setCssSelector("#name");
            InstructionLoad click = new InstructionLoad();
            click.setCssSelector("#save");

            assertTrue(driver.fill(input, new FieldData("Test", "working")));
            assertTrue(driver.click(click));
            assertEquals(1, driver.pageCount());
            assertTrue(driver.content().contains("data-clicked=\"yes\""));
        } finally {
            driver.close();
        }
    }

    @Test
    void clickAdoptsNewTabAndScannerReadsItsElements() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("""
                    <!doctype html>
                    <html><body>
                      <button id="openDetails" onclick="
                        const popup=window.open('about:blank','details');
                        popup.document.write('<!doctype html><html><head><title>Details</title></head>'
                          + '<body><label for=&quot;account&quot;>Account</label>'
                          + '<input id=&quot;account&quot; value=&quot;CH93&quot; />'
                          + '<button id=&quot;confirm&quot;>Confirm</button></body></html>');
                        popup.document.close();
                      ">Open details</button>
                    </body></html>
                    """);

            InstructionLoad open = new InstructionLoad();
            open.setName("Open details");
            open.setCssSelector("#openDetails");

            assertTrue(driver.click(open));
            assertEquals(2, driver.pageCount());
            assertEquals("Details", driver.title());

            List<ElementDTO> scanned = driver.scanElements(new String[] {"input", "button", "label"}, false);
            assertTrue(scanned.stream().anyMatch(element -> "account".equals(element.getAttribId())));
            assertTrue(scanned.stream().anyMatch(element -> "confirm".equals(element.getAttribId())));
            assertTrue(driver.content().contains("CH93"));

            assertTrue(driver.selectPageRelative(-1));
            assertTrue(driver.content().contains("openDetails"));
            assertTrue(driver.selectPageRelative(1));
            assertEquals("Details", driver.title());
        } finally {
            driver.close();
        }
    }
}
