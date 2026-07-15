package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Locator-cascade coverage for the Playwright action replay used by TEST RUN
 * ({@code PlaywrightActionExecutor} via {@link ARPlaywrightDriver}). Verifies the instruction is
 * located and acted on through each rung — reference-only, CSS-only, and inside an iframe — using a
 * single headed-Edge session over in-memory pages. Input values are read back with evaluate() since
 * text() (innerText) is empty for inputs.
 */
class PlaywrightActionExecutorTest {

    @BeforeEach
    void requirePlaywrightBrowser() {
        PlaywrightTestSupport.assumeBrowserLaunchAvailable();
    }

    @Test
    void clicksByReferenceWhenXpathAbsent() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("<!doctype html><html><body>"
                    + "<button data-testid='go' onclick=\"document.getElementById('out').innerText='clicked'\">Go</button>"
                    + "<div id='out'>idle</div></body></html>");

            InstructionLoad ins = new InstructionLoad();
            ins.setName("Go");
            // No xpath / no css — only a test-id reference, so the reference rung must resolve it.
            ReferenceLoadDTO ref = new ReferenceLoadDTO();
            ref.setReferenceType("test-id");
            ref.setValue("go");
            ins.setReferenceLoadDTOList(List.of(ref));

            assertTrue(driver.click(ins), "click via test-id reference should succeed");
            assertEquals("clicked", String.valueOf(driver.evaluate("() => document.getElementById('out').innerText")));
        } finally {
            driver.close();
        }
    }

    @Test
    void fillsByCssSelector() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("<!doctype html><html><body><input id='amount' /></body></html>");

            InstructionLoad ins = new InstructionLoad();
            ins.setName("amount");
            ins.setCssSelector("#amount");

            assertTrue(driver.fill(ins, new FieldData("amount", "1250")), "fill by css should succeed");
            assertEquals("1250", String.valueOf(driver.evaluate("() => document.getElementById('amount').value")));
        } finally {
            driver.close();
        }
    }

    @Test
    void fillsInputInsideIframe() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            // Same-origin iframe via srcdoc so frameLocator can reach into it.
            driver.setContent("<!doctype html><html><body>"
                    + "<iframe id='pay' srcdoc=\"<input id='iban' />\"></iframe></body></html>");

            InstructionLoad ins = new InstructionLoad();
            ins.setName("iban");
            ins.setIFrameXPath("//iframe[@id='pay']");
            ins.setXpath("//*[@id='iban']");

            assertTrue(driver.fill(ins, new FieldData("iban", "CH93")), "fill inside iframe should succeed");
            Object value = driver.evaluate(
                    "() => document.getElementById('pay').contentDocument.getElementById('iban').value");
            assertEquals("CH93", String.valueOf(value));
        } finally {
            driver.close();
        }
    }
}
