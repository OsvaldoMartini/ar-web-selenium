package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.InputFlags;
import org.junit.jupiter.api.Test;

class ScannerTestActionFormatterTest {

    @Test
    void describeInputFlagsListsActiveFlagsInDisplayOrder() {
        ScannerTestActionFormatter formatter = new ScannerTestActionFormatter();
        InputFlags flags = InputFlags.of(InputFlags.SCROLL | InputFlags.NEXT | InputFlags.TAB
                | InputFlags.ENTER | InputFlags.FORCE);

        assertEquals(
                "Scroll, Next (mobile), Tab, Enter, Force Coordinates",
                formatter.describeInputFlags(flags));
    }

    @Test
    void describeInputFlagsShowsEmptyLabelWhenNoFlagsAreActive() {
        ScannerTestActionFormatter formatter = new ScannerTestActionFormatter();

        assertEquals("(no flags)", formatter.describeInputFlags(InputFlags.of(0)));
    }

    @Test
    void safeTargetLabelUsesDefinedNameAndTagWhenPresent() {
        ScannerTestActionFormatter formatter = new ScannerTestActionFormatter();
        TargetElement target = new TargetElement();
        target.setDefinedName("Submit");
        target.setTagName("button");

        assertEquals("Submit &lt;button&gt;", formatter.safeTargetLabel(target));
    }

    @Test
    void safeTargetLabelFallsBackToTagOrUnnamed() {
        ScannerTestActionFormatter formatter = new ScannerTestActionFormatter();
        TargetElement tagOnly = new TargetElement();
        tagOnly.setTagName("input");
        TargetElement unnamed = new TargetElement();

        assertEquals("&lt;input&gt;", formatter.safeTargetLabel(tagOnly));
        assertEquals("(unnamed)", formatter.safeTargetLabel(unnamed));
    }

    @Test
    void sameUrlIgnoresCaseTrailingSlashAndFragment() {
        ScannerTestActionFormatter formatter = new ScannerTestActionFormatter();

        assertTrue(formatter.sameUrl(" HTTPS://Example.test/path/#section ", "https://example.test/path"));
    }

    @Test
    void sameUrlDistinguishesDifferentPathsAndTreatsNullAsEmpty() {
        ScannerTestActionFormatter formatter = new ScannerTestActionFormatter();

        assertFalse(formatter.sameUrl("https://example.test/a", "https://example.test/b"));
        assertTrue(formatter.sameUrl(null, ""));
    }
}
