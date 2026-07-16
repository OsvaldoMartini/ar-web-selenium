package com.allinweb.ch.facade;

public final class ScannerCreateBlockModalPresentationService {

    public Presentation presentation(boolean reactive) {
        return new Presentation(
                reactive ? "No block selected - create one" : "Create new block",
                reactive ? "No Block Selected - pick an existing block below or create a new one." : null,
                "Block name:",
                "e.g. Login Flow",
                "Insert position:",
                "Preview:",
                "Create");
    }

    public record Presentation(
            String title,
            String banner,
            String nameLabel,
            String namePrompt,
            String positionLabel,
            String previewLabel,
            String createButton) {}
}
