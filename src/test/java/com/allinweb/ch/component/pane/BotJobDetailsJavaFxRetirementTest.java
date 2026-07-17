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
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerTestActionCheckboxesAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerTestActionCheckboxStateAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerTextFieldsAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPreLaunchStatusTextAreaAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerRefreshBlocksButtonAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerSearchHiddenFieldsButtonAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerSupportButtonAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginStatusButtonAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginUpdateButtonAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginUpdateButtonRefreshAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginUpdateDialogPublisherAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginPickerManifestFetchAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginPickerDialogPublisherAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginManifestListFlowAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginListDialogPublisherAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginManifestResultAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginManifestFetchTaskAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginManifestClient.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginHintAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("control/ARComponentBuilder.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginDownloadFlowAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginDownloadProgressPublisherAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginDownloadResultAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginSingleDownloadTaskAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginBatchDownloadTaskAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerPluginBackgroundThreadAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerLayoutNodeAdapter.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ScannerElementFocusComboBoxAdapter.java")));
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

    @Test
    void scannerPaneDoesNotUseJavaFxTestActionCheckboxes() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerTestActionCheckboxesAdapter"));
        assertFalse(compiledSource.contains("ScannerTestActionCheckboxStateAdapter"));
        assertFalse(compiledSource.contains("checkClickElement"));
        assertFalse(compiledSource.contains("checkInputText"));
        assertFalse(compiledSource.contains("checkOutputText"));
        assertFalse(compiledSource.contains("CheckBox"));
    }

    @Test
    void scannerPaneDoesNotUseJavaFxTextFields() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerTextFieldsAdapter"));
        assertFalse(compiledSource.contains("searchTermsField"));
        assertFalse(compiledSource.contains("testActionsField"));
        assertFalse(compiledSource.contains("coordsTextField"));
        assertFalse(compiledSource.contains("searchAttribValueField"));
        assertFalse(compiledSource.contains("javafx.scene.control.TextField"));
    }

    @Test
    void scannerPaneDoesNotUseJavaFxStatusTextArea() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerPreLaunchStatusTextAreaAdapter"));
        assertFalse(compiledSource.contains("countdownTextField"));
        assertFalse(compiledSource.contains("javafx.scene.control.TextArea"));
    }

    @Test
    void scannerPaneDoesNotUseRetiredButtonAdapters() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerRefreshBlocksButtonAdapter"));
        assertFalse(compiledSource.contains("ScannerSearchHiddenFieldsButtonAdapter"));
        assertFalse(compiledSource.contains("ScannerSupportButtonAdapter"));
        assertFalse(compiledSource.contains("refreshBlocksButton"));
        assertFalse(compiledSource.contains("turnOnOffButton"));
        assertFalse(compiledSource.contains("sendDomButton"));
        assertFalse(compiledSource.contains("requestSupportButton"));
    }

    @Test
    void scannerPaneDoesNotUseRetiredPluginButtonAdapters() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerPluginStatusButtonAdapter"));
        assertFalse(compiledSource.contains("ScannerPluginUpdateButtonAdapter"));
        assertFalse(compiledSource.contains("ScannerPluginUpdateButtonRefreshAdapter"));
        assertFalse(compiledSource.contains("pluginUpdateButton"));
        assertFalse(compiledSource.contains("updatePluginsButton"));
        assertFalse(compiledSource.contains("buildPluginUpdateButton"));
        assertFalse(compiledSource.contains("buildUpdatePluginsButton"));
        assertFalse(compiledSource.contains("refreshPluginUpdateButton"));
        assertFalse(compiledSource.contains("ScannerPluginHintAdapter"));
        assertFalse(compiledSource.contains("lblPluginHint"));
        assertFalse(compiledSource.contains("ARComponentBuilder"));
        assertFalse(compiledSource.contains("ScannerPluginDownloadFlowAdapter"));
        assertFalse(compiledSource.contains("ScannerLayoutNodeAdapter"));
        assertFalse(compiledSource.contains("ScannerElementFocusComboBoxAdapter"));
        assertFalse(compiledSource.contains("ScannerSearchCleanupService"));
        assertFalse(compiledSource.contains("PaneSearchCleanupOperations"));
        assertFalse(compiledSource.contains("searchTermsBtn("));
        assertFalse(compiledSource.contains("handleSearchTermClick("));
        assertTrue(compiledSource.contains("runScannerWorkspaceAction(\"PAGE_SCANNER\""));
        assertTrue(compiledSource.contains("runScannerWorkspaceAction(\"PRE_LAUNCH\""));
        assertTrue(compiledSource.contains("runScannerWorkspaceAction(\"STOP_PRE_LAUNCH\""));
        assertTrue(compiledSource.contains("runScannerWorkspaceAction(\"REFRESH_PAGE\""));
        assertTrue(compiledSource.contains("runScannerWorkspaceAction(\"CLEAR_GRID\""));
    }
}
