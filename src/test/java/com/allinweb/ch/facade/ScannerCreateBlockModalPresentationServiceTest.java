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
    }

    @Test
    void buildsReactivePresentation() {
        ScannerCreateBlockModalPresentationService.Presentation presentation = service.presentation(true);

        assertEquals("No block selected - create one", presentation.title());
        assertEquals(
                "No Block Selected - pick an existing block below or create a new one.",
                presentation.banner());
    }
}
