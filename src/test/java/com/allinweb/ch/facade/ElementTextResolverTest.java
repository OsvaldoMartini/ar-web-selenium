package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for {@link ElementTextResolver} — specifically the OCR-noise guard added after
 * the OneTrust cookie buttons were mis-named "m"/"m_2": a stray single-glyph OCR result (weight
 * 0.70 OVERLAP) was overriding the element's real DOM text (weight 0.40). Single-char OCR results
 * are now dropped as noise so genuine DOM text wins.
 */
class ElementTextResolverTest {

    private static ElementDTO button(String id, String someText) {
        ElementDTO dto = new ElementDTO();
        dto.setTagName("button");
        dto.setTypeElement("button");
        dto.setAttribId(id);
        dto.setXPath("//*[@id='" + id + "']");
        dto.setCssSelector("button#" + id);
        dto.setSomeText(someText);
        dto.setAttributeData(new com.allinweb.ch.model.AttributeData[0]);
        return dto;
    }

    private static Path writeOcr(Path dir, String json) throws Exception {
        Path f = dir.resolve("ocr-correlation-HP.json");
        Files.writeString(f, json, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void singleGlyphOcrDoesNotOverrideRealDomText(@TempDir Path dir) throws Exception {
        ElementDTO reject = button("onetrust-reject-all-handler", "Rifiuta tutti");
        ElementDTO accept = button("onetrust-accept-btn-handler", "Accetta tutti i cookie");

        // Reproduce the live failure: OCR mis-correlated a stray "m" onto both buttons.
        Path ocr = writeOcr(
                dir,
                "[{\"xpath\":\"//*[@id='onetrust-reject-all-handler']\",\"matchQuality\":\"OVERLAP\",\"ocrText\":\"m\"},"
                        + "{\"xpath\":\"//*[@id='onetrust-accept-btn-handler']\",\"matchQuality\":\"OVERLAP\",\"ocrText\":\"m\"}]");

        ElementTextResolver.resolveAll(new ElementDTO[] {reject, accept}, ocr, null);

        // DOM text must survive — not be overwritten by the "m" noise.
        assertEquals("Rifiuta tutti", reject.getSomeText());
        assertEquals("Accetta tutti i cookie", accept.getSomeText());
        assertTrue(
                reject.getDefinedName().startsWith("rifiuta"),
                "definedName should derive from real text, was: " + reject.getDefinedName());
        assertTrue(
                accept.getDefinedName().startsWith("accetta"),
                "definedName should derive from real text, was: " + accept.getDefinedName());
    }

    @Test
    void legitimateOcrStillAppliesWhenDomTextIsBlank(@TempDir Path dir) throws Exception {
        // An icon button with no DOM text — OCR is the only signal and should win.
        ElementDTO iconBtn = button("save-icon", "");
        Path ocr = writeOcr(
                dir, "[{\"xpath\":\"//*[@id='save-icon']\",\"matchQuality\":\"EXACT_CONTAIN\",\"ocrText\":\"Salva\"}]");

        ElementTextResolver.resolveAll(new ElementDTO[] {iconBtn}, ocr, null);

        assertEquals("Salva", iconBtn.getSomeText());
        assertTrue(iconBtn.getDefinedName().startsWith("salva"));
    }

    @Test
    void genericCheckboxLabelFallsBackToSemanticAttribute() {
        ElementDTO checkbox = new ElementDTO();
        checkbox.setTagName("input");
        checkbox.setTypeElement("input");
        checkbox.setAttribId("chkbox-id");
        checkbox.setXPath("//*[@id='chkbox-id']");
        checkbox.setSomeText("checkbox label");
        checkbox.setAttributeType("checkbox-option");
        checkbox.setAttributeData(new AttributeData[] {
            new AttributeData("id", "chkbox-id"),
            new AttributeData("type", "checkbox"),
            new AttributeData("class", "category-filter-handler"),
            new AttributeData("control.kind", "checkbox-option")
        });

        ElementTextResolver.resolveAll(new ElementDTO[] {checkbox}, null, null);

        // someText carries display casing (TextSimilarity.humanize Title-Cases by design,
        // matching visible-text names elsewhere); definedName is the lowercase slug.
        assertEquals("Category Filter", checkbox.getSomeText());
        assertEquals("category_filter", checkbox.getDefinedName());
    }

    @Test
    void currencyOptionDomTextCannotBeReplacedBySimilarOcr(@TempDir Path dir) throws Exception {
        ElementDTO chf = button("mat-option-83", "CHF");
        chf.setTagName("mat-option");
        chf.setXPath("//mat-option[@test-id='forex.credit-currency-CHF']");
        chf.setAttributeData(new AttributeData[] {
            new AttributeData("test-id", "forex.credit-currency-CHF"),
            new AttributeData("role", "option"),
            new AttributeData("control.kind", "select-option"),
            new AttributeData("option-text", "CHF"),
            new AttributeData("option-value", "CHF")
        });
        Path ocr = writeOcr(
                dir,
                "[{\"xpath\":\"//mat-option[@test-id='forex.credit-currency-CHF']\","
                        + "\"matchQuality\":\"EXACT_CONTAIN\",\"ocrText\":\"CHE\"}]");

        ElementTextResolver.resolveAll(new ElementDTO[] {chf}, ocr, null);

        assertEquals("CHF", chf.getSomeText());
        assertEquals("chf", chf.getDefinedName());
    }

    @Test
    void formControlDomLabelsSurviveOcrAndUseStableControlNames(@TempDir Path dir) throws Exception {
        ElementDTO debit = button("mat-select-35", "Valuta");
        debit.setTagName("mat-select");
        debit.setXPath("//mat-form-field[@test-id='forex.debit-currency']//mat-select[@formcontrolname='debitCurrency']");
        debit.setAttributeData(new AttributeData[] {
            new AttributeData("formcontrolname", "debitCurrency"),
            new AttributeData("text-source", "aria-labelledby"),
            new AttributeData("dom-label", "Valuta"),
            new AttributeData("control.kind", "select")
        });

        ElementDTO credit = button("mat-select-38", "Conto di accredito");
        credit.setTagName("mat-select");
        credit.setXPath("//mat-form-field[@test-id='forex.credit-account']//mat-select[@formcontrolname='creditAccount']");
        credit.setAttributeData(new AttributeData[] {
            new AttributeData("formcontrolname", "creditAccount"),
            new AttributeData("text-source", "aria-labelledby"),
            new AttributeData("dom-label", "Conto di accredito"),
            new AttributeData("control.kind", "select")
        });

        Path ocr = writeOcr(
                dir,
                "[{\"xpath\":\"" + debit.getXPath() + "\",\"matchQuality\":\"EXACT_CONTAIN\","
                        + "\"ocrText\":\"eur_gbp_sigatorio\"},"
                        + "{\"xpath\":\"" + credit.getXPath() + "\",\"matchQuality\":\"EXACT_CONTAIN\","
                        + "\"ocrText\":\"L di accredito\"}]");

        ElementTextResolver.resolveAll(new ElementDTO[] {debit, credit}, ocr, null);

        assertEquals("Valuta", debit.getSomeText());
        assertEquals("valuta_debit_currency", debit.getDefinedName());
        assertEquals("Conto di accredito", credit.getSomeText());
        assertEquals("conto_di_accredito_credit_account", credit.getDefinedName());
    }

    @Test
    void unlabeledFormControlSelfTextDoesNotBecomeAuthoritative(@TempDir Path dir) throws Exception {
        ElementDTO unlabeled = button("mat-select-40", "EUR");
        unlabeled.setTagName("mat-select");
        unlabeled.setXPath("//mat-select[@formcontrolname='creditCurrency']");
        unlabeled.setAttributeData(new AttributeData[] {
            new AttributeData("formcontrolname", "creditCurrency"),
            new AttributeData("control.kind", "select")
        });
        Path ocr = writeOcr(
                dir,
                "[{\"xpath\":\"//mat-select[@formcontrolname='creditCurrency']\","
                        + "\"matchQuality\":\"EXACT_CONTAIN\",\"ocrText\":\"Valuta\"}]");

        ElementTextResolver.resolveAll(new ElementDTO[] {unlabeled}, ocr, null);

        assertEquals("Valuta", unlabeled.getSomeText());
        assertEquals("valuta_credit_currency", unlabeled.getDefinedName());
    }
}
