package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.ElementTextResolver;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for the Angular Material controls used by the BancaStato forex form. */
class PlaywrightAngularMaterialForexScannerTest {

    private static final String PREFIX =
            "web-banking-trading-forex-entry-banklet.trading-forex-entry.forex-trade-details.";
    private static final List<String> FOREX_CONTROL_NAMES = List.of(
            "debitCurrency",
            "debitAmount",
            "debitAccount",
            "creditCurrency",
            "creditAmount",
            "creditAccount",
            "executionType");

    @BeforeEach
    void requirePlaywrightBrowser() {
        PlaywrightTestSupport.assumeBrowserLaunchAvailable();
    }

    @Test
    void controlsAndOptionsStayStableAcrossDynamicIdsAndClosedOverlay(@TempDir Path dir) throws Exception {
        ARPlaywrightDriver driver = new ARPlaywrightDriver();
        try {
            driver.open(ARConstantsEngine.EDGE, "about:blank", "");
            driver.setContent(forexFixture());

            List<ElementDTO> first = scanForex(driver);
            Map<String, ElementDTO> firstControls = controlsByFormControlName(first);
            assertEquals(7, firstControls.size());
            assertStableControlLocators(firstControls);
            assertFalse(first.stream().anyMatch(element -> "mat-form-field".equals(element.getTagName())));
            assertFalse(first.stream().anyMatch(element -> "mat-icon".equals(element.getTagName())));
            ElementDTO blankHook = first.stream()
                    .filter(element -> "blank-hook".equals(element.getAttribId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("button#blank-hook", blankHook.getCssSelector());
            assertFalse(blankHook.getCssSelector().contains("test-id"));
            ElementDTO duplicateOwnerControl = first.stream()
                    .filter(element -> "duplicateLeft".equals(attribute(element, "formcontrolname")))
                    .findFirst()
                    .orElseThrow();
            assertEquals("input[formcontrolname=\"duplicateLeft\"]", duplicateOwnerControl.getCssSelector());
            assertFalse(duplicateOwnerControl.getCssSelector().contains("duplicate-owner"));

            ElementDTO firstChf = option(first, "CHF");
            assertEquals(
                    "mat-option[test-id=\"" + PREFIX + "credit-currency-CHF\"]",
                    firstChf.getCssSelector());
            assertEquals(
                    "//mat-option[@test-id='" + PREFIX + "credit-currency-CHF']",
                    firstChf.getXPath());
            assertEquals("CHF", attribute(firstChf, "option-text"));
            assertEquals("CHF", attribute(firstChf, "option-value"));
            assertEquals(
                    "mat-form-field[test-id=\"" + PREFIX
                            + "credit-currency\"] mat-select[formcontrolname=\"creditCurrency\"]",
                    attribute(firstChf, "trigger-selector"));
            List<ElementDTO> nativeChf = first.stream()
                    .filter(element -> "option".equals(attribute(element, "original-tag")))
                    .filter(element -> "CHF".equals(attribute(element, "option-value")))
                    .filter(element -> "//select[@name='nativeFx']".equals(attribute(element, "select-xpath")))
                    .toList();
            assertEquals(2, nativeChf.size(), "native option should have one action and one output DTO");
            assertTrue(nativeChf.stream()
                    .allMatch(element -> "//select[@name='nativeFx']/option[@value='CHF']"
                            .equals(element.getXPath())));
            assertFalse(first.stream().anyMatch(element -> "option".equals(element.getTagName())));

            driver.evaluate("""
                    () => {
                      let selectId = 800;
                      let inputId = 900;
                      let optionId = 1000;
                      document.querySelectorAll('mat-select[id]').forEach(el => el.id = 'mat-select-' + selectId++);
                      document.querySelectorAll('input[id]').forEach(el => el.id = 'mat-input-' + inputId++);
                      document.querySelectorAll('mat-option[id]').forEach(el => el.id = 'mat-option-' + optionId++);
                    }
                    """);

            List<ElementDTO> second = scanForex(driver);
            Map<String, ElementDTO> secondControls = controlsByFormControlName(second);
            assertEquals(firstControls.keySet(), secondControls.keySet());
            for (String controlName : firstControls.keySet()) {
                ElementDTO before = firstControls.get(controlName);
                ElementDTO after = secondControls.get(controlName);
                assertNotEquals(before.getAttribId(), after.getAttribId(), controlName + " id should have changed");
                assertEquals(before.getCssSelector(), after.getCssSelector(), controlName + " CSS drifted");
                assertEquals(before.getXPath(), after.getXPath(), controlName + " XPath drifted");
            }
            assertStableControlLocators(secondControls);
            assertSelectorsResolveExactlyOnce(driver, secondControls.values());

            ElementDTO secondChf = option(second, "CHF");
            assertNotEquals(firstChf.getAttribId(), secondChf.getAttribId());
            assertEquals(firstChf.getCssSelector(), secondChf.getCssSelector());
            assertEquals(firstChf.getXPath(), secondChf.getXPath());

            Path ocr = writeCorruptForexOcr(dir, secondControls.values(), secondChf);
            // Resolve the Angular forex controls as one page-scanner batch. The fixture also
            // contains a separate native <select> below solely to verify native option synthesis;
            // including those unrelated duplicate CHF/EUR labels here would intentionally invoke
            // the resolver's global _2/_3 display-name disambiguation and obscure this OCR check.
            List<ElementDTO> angularForexElements = new ArrayList<>(secondControls.values());
            angularForexElements.add(secondChf);
            ElementTextResolver.resolveAll(angularForexElements.toArray(new ElementDTO[0]), ocr, null);
            assertResolved(secondControls.get("debitCurrency"), "Valuta", "valuta_debit_currency");
            assertResolved(secondControls.get("debitAmount"), "Importo", "importo_debit_amount");
            assertResolved(
                    secondControls.get("debitAccount"),
                    "Conto di addebito",
                    "conto_di_addebito_debit_account");
            assertResolved(secondControls.get("creditCurrency"), "Valuta", "valuta_credit_currency");
            assertResolved(secondControls.get("creditAmount"), "Importo", "importo_credit_amount");
            assertResolved(
                    secondControls.get("creditAccount"),
                    "Conto di accredito",
                    "conto_di_accredito_credit_account");
            assertResolved(
                    secondControls.get("executionType"),
                    "Tipo di esecuzione",
                    "tipo_di_esecuzione_execution_type");
            assertResolved(secondChf, "CHF", "chf");

            driver.evaluate("""
                    () => {
                      document.querySelectorAll('.overlay').forEach(overlay => overlay.remove());
                      openDebitCurrency();
                    }
                    """);
            assertTrue(driver.content().contains(PREFIX + "debit-currency-CHF"));
            assertFalse(driver.content().contains(PREFIX + "credit-currency-CHF"));
            assertTrue(driver.click(optionInstruction(secondChf)), "closed option overlay should be reopened");
            assertEquals("CHF", String.valueOf(driver.evaluate("() => document.body.dataset.chosen")));
            assertEquals("credit", String.valueOf(driver.evaluate("() => document.body.dataset.chosenSide")));
            assertEquals(
                    "CHF",
                    String.valueOf(driver.evaluate(
                            "() => document.querySelector('[formcontrolname=creditCurrency] [data-value]').textContent")));
            assertEquals(
                    "CAD",
                    String.valueOf(driver.evaluate(
                            "() => document.querySelector('[formcontrolname=debitCurrency] [data-value]').textContent")));
        } finally {
            driver.close();
        }
    }

    private static List<ElementDTO> scanForex(ARPlaywrightDriver driver) {
        return driver.scanElements(
                new String[] {
                    "input", "select", "mat-select", "mat-option", "button", "mat-form-field", "mat-icon", "label"
                },
                false);
    }

    private static Map<String, ElementDTO> controlsByFormControlName(List<ElementDTO> elements) {
        return elements.stream()
                .filter(element -> FOREX_CONTROL_NAMES.contains(attribute(element, "formcontrolname")))
                .collect(Collectors.toMap(
                        element -> attribute(element, "formcontrolname"),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static void assertStableControlLocators(Map<String, ElementDTO> controls) {
        Map<String, String> owners = Map.of(
                "debitCurrency", "debit-currency",
                "debitAmount", "debit-amount",
                "debitAccount", "debit-money-account",
                "creditCurrency", "credit-currency",
                "creditAmount", "credit-amount",
                "creditAccount", "credit-money-account",
                "executionType", "execution-type");
        for (Map.Entry<String, String> expected : owners.entrySet()) {
            ElementDTO element = controls.get(expected.getKey());
            assertNotNull(element, "Missing " + expected.getKey());
            String tag = expected.getKey().endsWith("Amount") ? "input" : "mat-select";
            assertEquals(
                    "mat-form-field[test-id=\"" + PREFIX + expected.getValue() + "\"] " + tag
                            + "[formcontrolname=\"" + expected.getKey() + "\"]",
                    element.getCssSelector());
            assertEquals(
                    "//mat-form-field[@test-id='" + PREFIX + expected.getValue() + "']//" + tag
                            + "[@formcontrolname='" + expected.getKey() + "']",
                    element.getXPath());
            assertFalse(element.getCssSelector().contains(element.getAttribId()));
            assertFalse(element.getXPath().contains(element.getAttribId()));
        }
        assertEquals(
                controls.size(),
                controls.values().stream().map(ElementDTO::getCssSelector).distinct().count());
        assertEquals(
                controls.size(),
                controls.values().stream().map(ElementDTO::getXPath).distinct().count());
    }

    private static void assertSelectorsResolveExactlyOnce(ARPlaywrightDriver driver, Iterable<ElementDTO> controls) {
        List<String> css = new ArrayList<>();
        List<String> xpath = new ArrayList<>();
        controls.forEach(element -> {
            css.add(element.getCssSelector());
            xpath.add(element.getXPath());
        });
        assertEquals(
                Boolean.TRUE,
                driver.evaluate("selectors => selectors.every(selector => document.querySelectorAll(selector).length === 1)", css));
        assertEquals(
                Boolean.TRUE,
                driver.evaluate(
                        "selectors => selectors.every(selector => document.evaluate(selector, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null).snapshotLength === 1)",
                        xpath));
    }

    private static ElementDTO option(List<ElementDTO> elements, String text) {
        return elements.stream()
                .filter(element -> "mat-option".equals(element.getTagName()))
                .filter(element -> text.equals(attribute(element, "option-text")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing mat-option " + text));
    }

    private static String attribute(ElementDTO element, String name) {
        if (element == null || element.getAttributeData() == null) return "";
        return Arrays.stream(element.getAttributeData())
                .filter(attribute -> attribute != null && name.equalsIgnoreCase(attribute.getName()))
                .map(AttributeData::getValue)
                .filter(value -> value != null)
                .findFirst()
                .orElse("");
    }

    private static Path writeCorruptForexOcr(
            Path dir, Iterable<ElementDTO> controls, ElementDTO option) throws Exception {
        List<ElementDTO> entries = new ArrayList<>();
        controls.forEach(entries::add);
        entries.add(option);
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) json.append(',');
            ElementDTO element = entries.get(index);
            String corruptText = element == option ? "CHE" : "eur_gbp_sigatorio";
            json.append("{\"xpath\":\"")
                    .append(element.getXPath())
                    .append("\",\"matchQuality\":\"EXACT_CONTAIN\",\"ocrText\":\"")
                    .append(corruptText)
                    .append("\"}");
        }
        json.append(']');
        Path file = dir.resolve("ocr-correlation-HP.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static void assertResolved(ElementDTO element, String text, String definedName) {
        assertEquals(text, element.getSomeText());
        assertEquals(definedName, element.getDefinedName());
    }

    private static InstructionLoad optionInstruction(ElementDTO option) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName(option.getDefinedName());
        instruction.setXpath(option.getXPath());
        instruction.setCssSelector(option.getCssSelector());
        instruction.setCoordinates(option.getCoordinates());
        instruction.setTagName("button");
        List<ReferenceLoadDTO> references = Arrays.stream(option.getAttributeData())
                .filter(attribute -> attribute != null && attribute.getName() != null)
                .map(attribute -> {
                    ReferenceLoadDTO reference = new ReferenceLoadDTO();
                    reference.setReferenceType("AttrData:" + attribute.getName());
                    reference.setValue(attribute.getValue());
                    return reference;
                })
                .toList();
        instruction.setReferenceLoadDTOList(references);
        return instruction;
    }

    private static String forexFixture() {
        return """
                <!doctype html>
                <html>
                  <head>
                    <style>
                      mat-form-field, mat-select, mat-option, label { display: block; }
                      mat-form-field { margin: 4px; padding: 3px; width: 300px; }
                      mat-select, input, select { display: block; width: 240px; height: 28px; }
                      mat-option { width: 220px; height: 24px; }
                      button { width: 44px; height: 32px; }
                    </style>
                  </head>
                  <body>
                    <section><span>Vendere</span>
                      %s
                      %s
                      %s
                    </section>
                    <button test-id="%sswipe-currency" type="button"><mat-icon>swap_horiz</mat-icon></button>
                    <button id="blank-hook" test-id="" type="button">Blank hook</button>
                    <mat-form-field test-id="duplicate-owner">
                      <label id="duplicate-label-left">Duplicate left</label>
                      <input id="mat-input-70" formcontrolname="duplicateLeft" aria-labelledby="duplicate-label-left">
                    </mat-form-field>
                    <mat-form-field test-id="duplicate-owner">
                      <label id="duplicate-label-right">Duplicate right</label>
                      <input id="mat-input-71" formcontrolname="duplicateRight" aria-labelledby="duplicate-label-right">
                    </mat-form-field>
                    <section><span>Acquistare</span>
                      %s
                      %s
                      %s
                    </section>
                    %s
                    <label>Native currency</label>
                    <select name="nativeFx">
                      <option value="CHF">CHF</option>
                      <option value="EUR">EUR</option>
                    </select>
                    <script>
                      const forexPrefix = '%s';
                      window.openCurrency = side => {
                        const panelId = side + '-panel';
                        if (document.getElementById(panelId)) return;
                        const panel = document.createElement('div');
                        panel.id = panelId;
                        panel.className = 'overlay';
                        panel.setAttribute('role', 'listbox');
                        const firstId = side === 'debit' ? 73 : 83;
                        ['CHF', 'EUR'].forEach((value, index) => {
                          const option = document.createElement('mat-option');
                          option.id = 'mat-option-' + (firstId + index);
                          option.setAttribute('role', 'option');
                          option.setAttribute('value', value);
                          option.setAttribute('test-id', forexPrefix + side + '-currency-' + value);
                          option.textContent = value;
                          option.addEventListener('click', () => chooseCurrency(side, value));
                          panel.appendChild(option);
                        });
                        document.body.appendChild(panel);
                      };
                      window.openDebitCurrency = () => openCurrency('debit');
                      window.openCreditCurrency = () => openCurrency('credit');
                      window.chooseCurrency = (side, value) => {
                        document.body.dataset.chosen = value;
                        document.body.dataset.chosenSide = side;
                        document.querySelector('[formcontrolname="' + side + 'Currency"] [data-value]').textContent = value;
                        document.getElementById(side + '-panel')?.remove();
                      };
                      openCreditCurrency();
                    </script>
                  </body>
                </html>
                """.formatted(
                selectField("debit-currency", "Valuta", "debitCurrency", "mat-select-35", "CAD", "debit"),
                inputField("debit-amount", "Importo", "debitAmount", "mat-input-18"),
                selectField("debit-money-account", "Conto di addebito", "debitAccount", "mat-select-36", "", ""),
                PREFIX,
                selectField("credit-currency", "Valuta", "creditCurrency", "mat-select-37", "EUR", "credit"),
                inputField("credit-amount", "Importo", "creditAmount", "mat-input-19"),
                selectField("credit-money-account", "Conto di accredito", "creditAccount", "mat-select-38", "", ""),
                selectField("execution-type", "Tipo di esecuzione", "executionType", "mat-select-39", "Al meglio", ""),
                PREFIX);
    }

    private static String selectField(
            String owner, String label, String control, String id, String value, String overlaySide) {
        String labelId = "mat-mdc-form-field-label-" + id.substring(id.lastIndexOf('-') + 1);
        String click = overlaySide.isBlank()
                ? ""
                : " aria-controls=\"" + overlaySide + "-panel\" onclick=\"openCurrency('" + overlaySide + "')\"";
        return "<mat-form-field test-id=\"" + PREFIX + owner + "\">"
                + "<label id=\"" + labelId + "\">" + label + "</label>"
                + "<div><mat-select role=\"combobox\" formcontrolname=\"" + control + "\" id=\"" + id
                + "\" aria-labelledby=\"" + labelId + "\"" + click + ">"
                + "<span data-value>" + value + "</span></mat-select></div></mat-form-field>";
    }

    private static String inputField(String owner, String label, String control, String id) {
        String labelId = "mat-mdc-form-field-label-" + id.substring(id.lastIndexOf('-') + 1);
        return "<mat-form-field test-id=\"" + PREFIX + owner + "\">"
                + "<label id=\"" + labelId + "\">" + label + "</label>"
                + "<div><input type=\"text\" formcontrolname=\"" + control + "\" id=\"" + id
                + "\" aria-labelledby=\"" + labelId + "\"></div></mat-form-field>";
    }
}
