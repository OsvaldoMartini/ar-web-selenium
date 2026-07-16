package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ScannerCreateBlockModalPresentationServiceTest {

    private final ScannerCreateBlockModalPresentationService service =
            new ScannerCreateBlockModalPresentationService();

    @Test
    void buildsProactivePresentation() {
        ScannerCreateBlockModalPresentationService.Presentation presentation = service.presentation(false);

        assertEquals("Create new block", presentation.title());
        assertNull(presentation.banner());
        assertEquals("Block name:", presentation.nameLabel());
        assertEquals("e.g. Login Flow", presentation.namePrompt());
        assertEquals("Insert position:", presentation.positionLabel());
        assertEquals("Preview:", presentation.previewLabel());
        assertEquals("Create", presentation.createButton());
        assertEquals(10, presentation.verticalSpacing());
        assertEquals(460, presentation.minWidth());
        assertEquals("-fx-text-fill:#6A1B9A; -fx-font-style:italic;", presentation.previewStyle());
    }

    @Test
    void buildsReactivePresentation() {
        ScannerCreateBlockModalPresentationService.Presentation presentation = service.presentation(true);

        assertEquals("No block selected - create one", presentation.title());
        assertEquals(
                "No Block Selected - pick an existing block below or create a new one.",
                presentation.banner());
        assertEquals(
                "-fx-background-color:#ffebee; -fx-text-fill:#C62828; -fx-font-weight:bold; "
                        + "-fx-padding:8; -fx-border-color:#EF9A9A; -fx-border-width:1; -fx-border-radius:4;",
                presentation.bannerStyle());
    }
}
