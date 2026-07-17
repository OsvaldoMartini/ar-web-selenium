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
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/ARScannedElementScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/JavaFxScannerModalStageFactory.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/JavaFxShellBootstrap.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/JavaFxConfigSceneShutdownPort.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/base/ARScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/base/IARScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/base/IconLoader.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/base/ARPane.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/base/IARPane.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("facade/ScannerModalStageService.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("vision/OcrTestResultRow.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerStageAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerCurrentUrlTextAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerIframeIndicatorAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerFieldLabelsAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerTestActionLabelAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerHiddenCloneCheckboxAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerTestMessageSuppressionCheckboxAdapter.java")));
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

    @Test
    void controlPanelHasNoCompiledJavaFxDependency() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("ARControlPanel.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("import javafx."));
        assertFalse(compiledSource.contains("extends Application"));
        assertFalse(compiledSource.contains("Platform.runLater"));
    }

    @Test
    void scannerPaneDoesNotUsePlatformRunLater() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("import javafx.application.Platform"));
        assertFalse(compiledSource.contains("Platform.runLater"));
    }

    @Test
    void scannerPaneDoesNotUseJavaFxBooleanProperties() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("javafx.beans.property.BooleanProperty"));
        assertFalse(compiledSource.contains("javafx.beans.property.SimpleBooleanProperty"));
    }

    @Test
    void scannerPaneDoesNotUsePaneBaseInheritance() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("extends ARPane"));
        assertFalse(compiledSource.contains("component.pane.base.ARPane"));
    }

    @Test
    void scannerPaneDoesNotExposeLegacyPaneShellApi() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("createPane("));
        assertFalse(compiledSource.contains("getPaneReference("));
        assertFalse(compiledSource.contains("protected Pane pane"));
    }

    @Test
    void scannerPaneDoesNotUseJavaFxTextNodes() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("javafx.scene.text.Text"));
        assertFalse(compiledSource.contains("ScannerCurrentUrlTextAdapter"));
        assertFalse(compiledSource.contains("ScannerIframeIndicatorAdapter"));
    }

    @Test
    void scannerPaneDoesNotUseStaticLabelAdapters() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerFieldLabelsAdapter"));
        assertFalse(compiledSource.contains("ScannerTestActionLabelAdapter"));
        assertFalse(compiledSource.contains("definedNameLabel"));
    }

    @Test
    void scannerPaneDoesNotUseRetiredHiddenCheckboxAdapters() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerHiddenCloneCheckboxAdapter"));
        assertFalse(compiledSource.contains("ScannerTestMessageSuppressionCheckboxAdapter"));
        assertFalse(compiledSource.contains("checkCloneElement"));
        assertFalse(compiledSource.contains("checkNotShowTestMsg"));
    }
}
