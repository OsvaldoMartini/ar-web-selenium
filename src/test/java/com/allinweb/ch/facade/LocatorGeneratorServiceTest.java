package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/** Pure HTML-analysis tests for the paste-HTML locator generator -- no network, no database. */
class LocatorGeneratorServiceTest {

    private final LocatorGeneratorService service = LocatorGeneratorService.getInstance();

    private JsonObject generate(String html) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "req-1");
        body.addProperty("html", html);
        return service.generate(body);
    }

    private JsonArray controls(String html) {
        JsonObject response = generate(html);
        assertTrue(response.get("ok").getAsBoolean(), () -> "generation should succeed: " + response);
        assertEquals("req-1", response.get("requestId").getAsString());
        return response.getAsJsonArray("controls");
    }

    @Test
    void sameNameSelectAndInputGetDistinctLocators() {
        String html = "<div>"
                + "<select name='amount'><option>CHF</option></select>"
                + "<input name='amount' type='text'/>"
                + "</div>";
        JsonArray controls = controls(html);
        assertEquals(2, controls.size());

        JsonObject select = controls.get(0).getAsJsonObject();
        JsonObject input = controls.get(1).getAsJsonObject();

        assertEquals(0, select.get("controlIndex").getAsInt());
        assertEquals(1, input.get("controlIndex").getAsInt());
        assertEquals("select", select.get("tagName").getAsString());
        assertEquals("input", input.get("tagName").getAsString());

        // Same name, but each locator pins its own tag.
        assertNotEquals(input.get("xpath").getAsString(), select.get("xpath").getAsString());
        assertTrue(select.get("css").getAsString().startsWith("select["));
        assertTrue(input.get("css").getAsString().startsWith("input["));
        assertFalse(select.get("positional").getAsBoolean());
        assertFalse(input.get("positional").getAsBoolean());
    }

    @Test
    void nextJsSharedNameDifferentTagUsesStableAttr() {
        // A React/Next.js style pair: shared name, distinguished by data-testid.
        String html = "<div>"
                + "<button role='combobox' name='ccy' data-testid='sell-currency'>CHF</button>"
                + "<input name='ccy' data-testid='sell-amount'/>"
                + "</div>";
        JsonArray controls = controls(html);
        assertEquals(2, controls.size());

        JsonObject dropdown = controls.get(0).getAsJsonObject();
        JsonObject field = controls.get(1).getAsJsonObject();

        assertEquals("select", dropdown.get("controlKind").getAsString());
        assertEquals("text-input", field.get("controlKind").getAsString());
        // data-testid is the strongest stable attribute and should be enough on its own.
        assertTrue(dropdown.get("css").getAsString().contains("data-testid='sell-currency'"));
        assertTrue(field.get("css").getAsString().contains("data-testid='sell-amount'"));
    }

    @Test
    void generatedLocatorIncludesSomeTextAndDefinedName() {
        String html = "<div>"
                + "<button test-id='web-banking-common.web-banking-stepper.next-button-0'>Avanti</button>"
                + "<input placeholder='User number'/>"
                + "</div>";

        JsonArray controls = controls(html);

        JsonObject button = controls.get(0).getAsJsonObject();
        JsonObject input = controls.get(1).getAsJsonObject();
        assertEquals("Avanti", button.get("someText").getAsString());
        assertEquals("avanti", button.get("definedName").getAsString());
        assertEquals("test-id", button.get("attributeType").getAsString());
        assertEquals(
                "web-banking-common.web-banking-stepper.next-button-0",
                button.get("attributeValue").getAsString());
        assertEquals(button.get("css").getAsString(), button.get("cssSelector").getAsString());
        assertEquals("User number", input.get("someText").getAsString());
        assertEquals("user_number", input.get("definedName").getAsString());
    }

    @Test
    void angularMatSelectVsInputSameFormControlName() {
        String html = "<div>"
                + "<mat-select formcontrolname='rate' id='mat-select-42'></mat-select>"
                + "<input formcontrolname='rate' id='mat-input-7'/>"
                + "</div>";
        JsonArray controls = controls(html);
        assertEquals(2, controls.size());

        JsonObject matSelect = controls.get(0).getAsJsonObject();
        JsonObject input = controls.get(1).getAsJsonObject();

        // Volatile mat-* ids must NOT be used; tag + formcontrolname disambiguates.
        assertFalse(matSelect.get("css").getAsString().contains("mat-select-42"));
        assertFalse(input.get("css").getAsString().contains("mat-input-7"));
        assertTrue(matSelect.get("css").getAsString().startsWith("mat-select["));
        assertTrue(input.get("css").getAsString().startsWith("input["));
    }

    @Test
    void identicalControlsFallBackToPosition() {
        // Truly identical tag + name + type -> only position can separate them.
        String html = "<div>"
                + "<input name='q' type='text'/>"
                + "<input name='q' type='text'/>"
                + "</div>";
        JsonArray controls = controls(html);
        assertEquals(2, controls.size());

        JsonObject first = controls.get(0).getAsJsonObject();
        JsonObject second = controls.get(1).getAsJsonObject();

        assertTrue(first.get("positional").getAsBoolean());
        assertTrue(second.get("positional").getAsBoolean());
        assertNotEquals(first.get("xpath").getAsString(), second.get("xpath").getAsString());
        assertTrue(first.get("xpath").getAsString().endsWith("[1]"));
        assertTrue(second.get("xpath").getAsString().endsWith("[2]"));
    }

    @Test
    void emptyHtmlIsRejected() {
        JsonObject response = generate("   ");
        assertFalse(response.get("ok").getAsBoolean());
        assertEquals(0, response.getAsJsonArray("controls").size());
        assertFalse(response.get("warning").getAsString().isEmpty());
    }

    @Test
    void nullAndOversizeBodiesAreRejectedWithCorrelatedShape() {
        JsonObject missing = service.generate(null);
        assertFalse(missing.get("ok").getAsBoolean());
        assertEquals("", missing.get("requestId").getAsString());
        assertEquals(0, missing.getAsJsonArray("controls").size());

        JsonObject body = new JsonObject();
        body.addProperty("requestId", "large-1");
        body.addProperty("html", "x".repeat(1_500_001));
        JsonObject oversize = service.generate(body);
        assertFalse(oversize.get("ok").getAsBoolean());
        assertEquals("large-1", oversize.get("requestId").getAsString());
        assertEquals(0, oversize.getAsJsonArray("controls").size());
    }

    @Test
    void noControlsProducesWarning() {
        JsonObject response = generate("<div><span>just text</span></div>");
        assertTrue(response.get("ok").getAsBoolean());
        assertEquals(0, response.getAsJsonArray("controls").size());
        assertTrue(response.get("warning").getAsString().toLowerCase().contains("no interactive"));
    }

    @Test
    void nonControlIdsAreNotInventedAsInteractiveControls() {
        JsonObject response = generate(
                "<div id='account-summary' name='layout'><span>Balance</span></div>");

        assertTrue(response.get("ok").getAsBoolean());
        assertEquals(0, response.getAsJsonArray("controls").size());
        assertTrue(response.get("warning").getAsString().toLowerCase().contains("no interactive"));
    }

    @Test
    void positionalSelectorsAreUniqueAcrossTheCompletePastedFragment() {
        String html = "<div><input name='q' type='text'/></div>"
                + "<section><input name='q' type='text'/></section>";
        JsonArray controls = controls(html);
        var document = Jsoup.parseBodyFragment(html);

        assertEquals(2, controls.size());
        for (int index = 0; index < controls.size(); index++) {
            JsonObject control = controls.get(index).getAsJsonObject();
            assertTrue(control.get("positional").getAsBoolean());
            assertEquals(1, document.select(control.get("css").getAsString()).size());
            assertTrue(control.get("xpath").getAsString().endsWith("[" + (index + 1) + "]"));
        }
        assertNotEquals(
                controls.get(0).getAsJsonObject().get("css").getAsString(),
                controls.get(1).getAsJsonObject().get("css").getAsString());
    }

    @Test
    void selectorLiteralsEscapeQuotesAndBackslashes() {
        String html = "<button data-testid=\"client's\\action\">Send</button>";
        JsonObject control = controls(html).get(0).getAsJsonObject();

        assertTrue(control.get("xpath").getAsString().contains("client's\\action"));
        assertEquals(1, Jsoup.parseBodyFragment(html).select(control.get("css").getAsString()).size());
    }

    @Test
    void applyPersistsAndReturnsTheAuthoritativeSelectedElement() {
        AtomicReference<ElementDTO> saved = new AtomicReference<>();
        LocatorGeneratorService applying = new LocatorGeneratorService(
                (homeBankingId, botJobId, homeUrlId, pageUrl, element) -> {
                    assertEquals(7, homeBankingId);
                    assertEquals(42, botJobId);
                    assertEquals(8, homeUrlId);
                    assertEquals("https://bank.test", pageUrl);
                    saved.set(element);
                    return new LocatorGeneratorService.PersistenceResult(0, 1);
                });
        JsonObject body = applyBody("//button[@test-id='next']");

        JsonObject response = applying.apply(body, 7, 42, 8, "https://bank.test");

        assertTrue(response.get("ok").getAsBoolean());
        assertTrue(response.get("persisted").getAsBoolean());
        assertEquals("1||//button[1]||button", response.get("elementKey").getAsString());
        assertEquals(
                "//button[@test-id='next']",
                response.getAsJsonObject("element").get("customXPath").getAsString());
        assertEquals("//button[@test-id='next']", saved.get().getCustomXPath());
    }

    @Test
    void applyRejectsCssAndDoesNotClaimFailedPersistence() {
        LocatorGeneratorService applying = new LocatorGeneratorService(
                (homeBankingId, botJobId, homeUrlId, pageUrl, element) -> {
                    throw new AssertionError("invalid locator must not be persisted");
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> applying.apply(applyBody("button[test-id='next']"), 7, 42, 8, "https://bank.test"));
        assertThrows(
                IllegalArgumentException.class,
                () -> applying.apply(applyBody("(//button["), 7, 42, 8, "https://bank.test"));

        LocatorGeneratorService failing = new LocatorGeneratorService(
                (homeBankingId, botJobId, homeUrlId, pageUrl, element) -> {
                    throw new IllegalStateException("database unavailable");
                });
        assertThrows(
                IllegalStateException.class,
                () -> failing.apply(applyBody("//button[@test-id='next']"), 7, 42, 8, "https://bank.test"));
    }

    @Test
    void applyRejectsAStaleOrForgedElementKeyBeforePersistence() {
        AtomicReference<ElementDTO> saved = new AtomicReference<>();
        LocatorGeneratorService applying = new LocatorGeneratorService(
                (homeBankingId, botJobId, homeUrlId, pageUrl, element) -> {
                    saved.set(element);
                    return new LocatorGeneratorService.PersistenceResult(0, 1);
                });
        JsonObject body = applyBody("//button[@test-id='next']");
        body.addProperty("elementKey", "999||//button[1]||button");

        assertThrows(
                IllegalArgumentException.class,
                () -> applying.apply(body, 7, 42, 8, "https://bank.test"));
        assertNull(saved.get(), "a mismatched row key must never reach persistence");
    }

    private static JsonObject applyBody(String xpath) {
        ElementDTO element = new ElementDTO();
        element.setId(1);
        element.setTagName("button");
        element.setXPath("//button[1]");
        JsonArray details = new JsonArray();
        details.add(new com.google.gson.Gson().toJsonTree(element));
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "apply-1");
        body.addProperty("elementKey", "1||//button[1]||button");
        body.addProperty("xpath", xpath);
        body.add("elementDetails", details);
        return body;
    }
}
