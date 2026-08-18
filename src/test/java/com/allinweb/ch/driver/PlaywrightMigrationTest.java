package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the Selenium→Playwright migration additions:
 * <ul>
 *   <li>the new {@link ARPlaywrightDriver} wrapper methods (reload/content/viewportSize/screenshot/evaluate)</li>
 *   <li>the scanner catching OneTrust-style cookie buttons with their real visible text</li>
 * </ul>
 * Uses a single headed-Edge Playwright session over an in-memory page (setContent), same pattern as
 * {@link PlaywrightAutomationSmokeTest}.
 */
class PlaywrightMigrationTest {

    @BeforeEach
    void requirePlaywrightBrowser() {
        PlaywrightTestSupport.assumeBrowserLaunchAvailable();
    }

    @Test
    void newWrapperMethodsWork() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("<!doctype html><html><head><title>Wrap</title></head>"
                    + "<body><h1 id='h'>Hello</h1></body></html>");

            // content()
            String html = driver.content();
            assertNotNull(html);
            assertTrue(html.contains("Hello"), "content() should return page HTML");

            // evaluate(script) — no-arg overload
            Object title = driver.evaluate("() => document.title");
            assertEquals("Wrap", String.valueOf(title));

            // viewportSize()
            int[] vp = driver.viewportSize();
            assertNotNull(vp, "viewportSize() should not be null");
            assertTrue(vp.length == 2 && vp[0] > 0 && vp[1] > 0, "viewport should have positive dimensions");

            // screenshot(false) — viewport PNG bytes
            byte[] png = driver.screenshot(false);
            assertNotNull(png);
            assertTrue(png.length > 100, "screenshot should return PNG bytes");

            // reload() — must not throw and the session stays open
            driver.reload();
            assertTrue(driver.isOpen(), "driver should still be open after reload");
        } finally {
            driver.close();
        }
    }

    @Test
    void inspectsElementsWithoutSeleniumWebElementObjects() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("<!doctype html><html><body><main id='parent'>"
                    + "<input class='choice' type='checkbox' checked>"
                    + "<input class='choice' type='checkbox'>"
                    + "</main></body></html>");

            ARPlaywrightDriver.BrowserElementSnapshot snapshot = driver.inspectElement("//input[@class='choice']");

            assertTrue(snapshot.found());
            assertEquals(2, snapshot.matchCount());
            assertTrue(snapshot.displayed());
            assertTrue(snapshot.enabled());
            assertTrue(snapshot.selected());
            assertTrue(snapshot.width() > 0);
            assertTrue(snapshot.height() > 0);
            assertTrue(snapshot.outerHtml().contains("checked"));
            assertTrue(snapshot.parentHtml().contains("id=\"parent\""));

            ARPlaywrightDriver.BrowserElementSnapshot missing = driver.inspectElement("//missing");
            assertFalse(missing.found());
            assertEquals("no-match", missing.reason());

            ARPlaywrightDriver.BrowserElementSnapshot invalid = driver.inspectElement("//*[");
            assertFalse(invalid.found());
            assertFalse(invalid.reason().isBlank());

            ARPlaywrightDriver.BrowserElementSnapshot empty = driver.inspectElement(" ");
            assertFalse(empty.found());
            assertEquals("empty-xpath", empty.reason());
        } finally {
            driver.close();
        }
    }

    @Test
    void scannerCatchesOneTrustCookieButtonsWithRealText() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent(oneTrustBanner());

            List<ElementDTO> elements = driver.scanElements(new String[] {"button"}, false);

            ElementDTO reject = byId(elements, "onetrust-reject-all-handler");
            ElementDTO accept = byId(elements, "onetrust-accept-btn-handler");
            ElementDTO settings = byId(elements, "onetrust-pc-btn-handler");

            assertNotNull(reject, "reject-all button must be scanned");
            assertNotNull(accept, "accept button must be scanned");
            assertNotNull(settings, "settings button must be scanned");

            // Real visible text captured (this is what OCR noise used to clobber to "m").
            assertEquals("Rifiuta tutti", reject.getSomeText());
            assertEquals("Accetta tutti i cookie", accept.getSomeText());

            // Reliable id-based locators produced.
            assertEquals("button#onetrust-reject-all-handler", reject.getCssSelector());
            assertTrue(reject.getXPath().contains("onetrust-reject-all-handler"));
        } finally {
            driver.close();
        }
    }

    @Test
    void scannerFindsLiteralTestIdAndConfiguredCustomAttributesExactly() {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent("<!doctype html><html><body>"
                    + "<button test-id='web-banking-common.web-banking-stepper.next-button-0'>Avanti</button>"
                    + "<button qa-hook='cancel-action'>Annulla</button>"
                    + "</body></html>");

            // test-id remains an unconditional companion even for an unrelated input scan.
            List<ElementDTO> defaultElements = driver.scanElements(new String[] {"input"}, false);
            ElementDTO avanti = byAttribute(
                    defaultElements,
                    "test-id",
                    "web-banking-common.web-banking-stepper.next-button-0");
            assertNotNull(avanti, "the literal Banca Stato test-id button must be scanned automatically");
            assertEquals("button", avanti.getTagName());
            assertEquals("button", avanti.getTypeElement());
            assertEquals("Avanti", avanti.getSomeText());
            assertEquals(
                    "button[test-id=\"web-banking-common.web-banking-stepper.next-button-0\"]",
                    avanti.getCssSelector());
            assertFalse(
                    hasAttribute(avanti, "data-testid"),
                    "a literal test-id must not be relabelled as data-testid");
            assertTrue(hasAttribute(avanti, "automation.test-id.attribute", "test-id"));

            List<ElementDTO> customElements = driver.scanElements(new String[] {"attr:qa-hook"}, false);
            ElementDTO annulla = byAttribute(customElements, "qa-hook", "cancel-action");
            assertNotNull(annulla, "a configured custom attribute must become a scanner selector");
            assertEquals("button[qa-hook=\"cancel-action\"]", annulla.getCssSelector());
            assertTrue(hasAttribute(annulla, "automation.test-id.attribute", "qa-hook"));
        } finally {
            driver.close();
        }
    }

    private static ElementDTO byId(List<ElementDTO> els, String id) {
        Optional<ElementDTO> found =
                els.stream().filter(e -> id.equals(e.getAttribId())).findFirst();
        return found.orElse(null);
    }

    private static ElementDTO byAttribute(List<ElementDTO> elements, String name, String value) {
        return elements.stream()
                .filter(element -> element.getAttributeData() != null)
                .filter(element -> java.util.Arrays.stream(element.getAttributeData())
                        .anyMatch(attribute -> name.equals(attribute.getName()) && value.equals(attribute.getValue())))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasAttribute(ElementDTO element, String name) {
        return element.getAttributeData() != null
                && java.util.Arrays.stream(element.getAttributeData())
                        .anyMatch(attribute -> name.equals(attribute.getName()));
    }

    private static boolean hasAttribute(ElementDTO element, String name, String value) {
        return element.getAttributeData() != null
                && java.util.Arrays.stream(element.getAttributeData())
                        .anyMatch(attribute -> name.equals(attribute.getName()) && value.equals(attribute.getValue()));
    }

    private static String oneTrustBanner() {
        return """
                <!doctype html>
                <html><head><title>Banca</title></head>
                <body>
                  <div id="onetrust-button-group">
                    <button id="onetrust-pc-btn-handler" aria-label="Impostazioni cookie, Apre la finestra">Impostazioni cookie</button>
                    <div class="banner-actions-container">
                      <button id="onetrust-reject-all-handler">Rifiuta tutti</button>
                      <button id="onetrust-accept-btn-handler">Accetta tutti i cookie</button>
                    </div>
                  </div>
                </body></html>
                """;
    }
}
