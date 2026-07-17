package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BotJobDetailsJavaFxRetirementTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "com", "allinweb", "ch");

    @Test
    void legacyBotJobDetailsPaneAndSceneAreDeleted() {
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ARViewBotJobPane.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/ARViewBotJobScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ARMainDashboardPane.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/ARMainScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/JavaFxScannerModalStageFactory.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("facade/ScannerModalStageService.java")));
    }

    @Test
    void workspaceHostHasNoCompiledJavaFxDependency() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/BotJobDetailsWorkspaceHost.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("import javafx."));
        assertFalse(compiledSource.contains("extends ARPane"));
        assertFalse(compiledSource.contains("ARViewBotJobScene"));
        assertTrue(compiledSource.contains("BotJobDetailsPresentationGateway"));
    }
}
