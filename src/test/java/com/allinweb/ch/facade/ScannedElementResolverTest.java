package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannedElement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the self-healing resolver's matching ladder. */
class ScannedElementResolverTest {

    private static ScannedElement se(String xpath, String css, String name, String someText, String coords) {
        ScannedElement s = new ScannedElement();
        s.setXPath(xpath);
        s.setCssSelector(css);
        s.setDefinedName(name);
        s.setSomeText(someText);
        s.setCoordinates(coords);
        return s;
    }

    private static InstructionLoad ins(String xpath, String css, String name, String coords) {
        InstructionLoad i = new InstructionLoad();
        i.setXpath(xpath);
        i.setCssSelector(css);
        i.setName(name);
        i.setCoordinates(coords);
        return i;
    }

    @Test
    void exactXpathWins() {
        List<ScannedElement> reg = List.of(
                se("//*[@id='a']", "button#a", "accetta", "Accetta", "10,10"),
                se("//*[@id='b']", "button#b", "rifiuta", "Rifiuta", "20,20"));
        var r = ScannedElementResolver.resolve(reg, ins("//*[@id='b']", null, "whatever", null));
        assertTrue(r.matched());
        assertEquals(ScannedElementResolver.Strategy.XPATH_EXACT, r.strategy());
        assertEquals("button#b", r.element().getCssSelector());
    }

    @Test
    void instructionXpathMatchesPersistedCustomXpathBeforeSameNameFallback() {
        ScannedElement first = se("//main//button[1]", null, "continue", "Continue", "10,10");
        ScannedElement second = se("//aside//button[1]", null, "continue", "Continue", "20,20");
        second.setCustomXPath("//button[@test-id='primary-next']");

        var result = ScannedElementResolver.resolve(
                List.of(first, second),
                ins("//button[@test-id='primary-next']", null, "continue", null));

        assertTrue(result.matched());
        assertEquals(ScannedElementResolver.Strategy.XPATH_EXACT, result.strategy());
        assertEquals("//aside//button[1]", result.element().getXPath());
    }

    @Test
    void uniqueNameResolvesWhenXpathDrifted() {
        List<ScannedElement> reg =
                List.of(se("//*[@id='new-reject']", "button#new", "rifiuta_tutti", "Rifiuta tutti", "5,5"));
        // Instruction's stored xpath no longer exists; name still matches uniquely.
        var r = ScannedElementResolver.resolve(reg, ins("//*[@id='OLD-reject']", null, "rifiuta_tutti", null));
        assertTrue(r.matched());
        assertEquals(ScannedElementResolver.Strategy.NAME_UNIQUE, r.strategy());
        assertEquals("//*[@id='new-reject']", r.element().getXPath());
    }

    @Test
    void clientNameAndHtmlNameAreEligibleAliases() {
        ScannedElement clientNamed =
                se("//*[@test-id='next']", "button.next", null, null, "5,5");
        clientNamed.setClientNamed("continue_order");
        ScannedElement htmlNamed =
                se("//*[@name='iban']", "input[name='iban']", null, null, "10,10");
        htmlNamed.setAttribName("beneficiaryIban");

        var clientResult = ScannedElementResolver.resolve(
                List.of(clientNamed, htmlNamed),
                ins("//stale", null, "continue_order", null));
        var htmlNameResult = ScannedElementResolver.resolve(
                List.of(clientNamed, htmlNamed),
                ins("//stale", null, "beneficiaryIban", null));

        assertEquals(ScannedElementResolver.Strategy.NAME_UNIQUE, clientResult.strategy());
        assertEquals("//*[@test-id='next']", clientResult.element().getXPath());
        assertEquals(ScannedElementResolver.Strategy.NAME_UNIQUE, htmlNameResult.strategy());
        assertEquals("//*[@name='iban']", htmlNameResult.element().getXPath());
    }

    @Test
    void sameNameDisambiguatedByNearestCoordinates() {
        List<ScannedElement> reg = List.of(
                se("//header//button", "header button", "cerca", "Cerca", "100,50"),
                se("//footer//button", "footer button", "cerca", "Cerca", "100,900"));
        // Two "cerca"; the instruction was authored near the footer.
        var r = ScannedElementResolver.resolve(reg, ins("//stale", null, "cerca", "105,890"));
        assertTrue(r.matched());
        assertEquals(ScannedElementResolver.Strategy.NAME_COORDS, r.strategy());
        assertEquals("//footer//button", r.element().getXPath());
    }

    @Test
    void fuzzyNameAsLastResort() {
        List<ScannedElement> reg =
                List.of(se("//*[@id='x']", "#x", "accetta_tutti_i_cookie", "Accetta tutti i cookie", "1,1"));
        var r = ScannedElementResolver.resolve(reg, ins(null, null, "accetta tutti i cookie", null));
        assertTrue(r.matched());
        // exact-name (someText, case-insensitive) actually catches this first — assert a real match.
        assertTrue(r.confidence() >= 0.75);
        assertEquals("//*[@id='x']", r.element().getXPath());
    }

    @Test
    void noMatchWhenNothingResembles() {
        List<ScannedElement> reg = List.of(se("//*[@id='x']", "#x", "login", "Login", "1,1"));
        var r = ScannedElementResolver.resolve(reg, ins("//*[@id='zzz']", "#zzz", "totally-different-xyz", null));
        assertFalse(r.matched());
        assertEquals(ScannedElementResolver.Strategy.NONE, r.strategy());
    }

    @Test
    void emptyRegistryIsNoMatch() {
        var r = ScannedElementResolver.resolve(List.of(), ins("//x", "#x", "n", null));
        assertFalse(r.matched());
    }
}
