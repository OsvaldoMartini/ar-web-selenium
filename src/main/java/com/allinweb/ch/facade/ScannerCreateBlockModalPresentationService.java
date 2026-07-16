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
                "Create",
                10,
                460,
                "-fx-background-color:#ffebee; -fx-text-fill:#C62828; -fx-font-weight:bold; "
                        + "-fx-padding:8; -fx-border-color:#EF9A9A; -fx-border-width:1; -fx-border-radius:4;",
                "-fx-text-fill:#6A1B9A; -fx-font-style:italic;");
    }

    public record Presentation(
            String title,
            String banner,
            String nameLabel,
            String namePrompt,
            String positionLabel,
            String previewLabel,
            String createButton,
            int verticalSpacing,
            int minWidth,
            String bannerStyle,
            String previewStyle) {}
}
