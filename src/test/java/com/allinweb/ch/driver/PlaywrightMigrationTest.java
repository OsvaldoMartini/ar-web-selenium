package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import java.util.List;
import java.util.Optional;
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

    private static ElementDTO byId(List<ElementDTO> els, String id) {
        Optional<ElementDTO> found =
                els.stream().filter(e -> id.equals(e.getAttribId())).findFirst();
        return found.orElse(null);
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
