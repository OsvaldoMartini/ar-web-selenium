package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BotJobDetailsDesktopUiRetirementTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "com", "allinweb", "ch");
    private static final String RETIRED_UI_PACKAGE = "java" + "fx";
    private static final String RETIRED_UI_CLASS_PREFIX = "Java" + "Fx";

    @Test
    void legacyBotJobDetailsPaneAndSceneAreDeleted() {
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ARViewBotJobPane.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/ARViewBotJobScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ARMainDashboardPane.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/ARMainScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/ARScannedElementScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve(
                "component/scene/" + RETIRED_UI_CLASS_PREFIX + "ScannerModalStageFactory.java")));
        assertFalse(
                Files.exists(SOURCE_ROOT.resolve("component/scene/" + RETIRED_UI_CLASS_PREFIX + "ShellBootstrap.java")));
        assertFalse(Files.exists(
                SOURCE_ROOT.resolve("component/scene/" + RETIRED_UI_CLASS_PREFIX + "ConfigSceneShutdownPort.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/base/ARScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/base/IARScene.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/scene/base/IconLoader.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("facade/" + RETIRED_UI_CLASS_PREFIX + "ScannerTargetContext.java")));
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
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ARScannedElementPaneProvider.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("component/pane/ARScannedElementPanePort.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("socket/MainFrame.java")));
        assertFalse(Files.exists(SOURCE_ROOT.resolve("socket/JavaCEFExample.java")));
    }

    @Test
    void workspaceHostHasNoCompiledRetiredUiDependency() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/BotJobDetailsWorkspaceHost.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("import " + RETIRED_UI_PACKAGE + "."));
        assertFalse(compiledSource.contains("extends ARPane"));
        assertFalse(compiledSource.contains("ARViewBotJobScene"));
        assertTrue(compiledSource.contains("BotJobDetailsPresentationGateway"));
    }

    @Test
    void controlPanelHasNoCompiledRetiredUiDependency() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("ARControlPanel.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("import " + RETIRED_UI_PACKAGE + "."));
        assertFalse(compiledSource.contains("extends Application"));
        assertFalse(compiledSource.contains("Platform.runLater"));
    }

    @Test
    void scannerPaneDoesNotUsePlatformRunLater() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("import " + RETIRED_UI_PACKAGE + ".application.Platform"));
        assertFalse(compiledSource.contains("Platform.runLater"));
    }

    @Test
    void scannerPaneDoesNotUseRetiredUiBooleanProperties() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".beans.property.BooleanProperty"));
        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".beans.property.SimpleBooleanProperty"));
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
    void scannerPaneDoesNotUseRetiredUiTextNodes() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".scene.text.Text"));
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
    void scannerPaneDoesNotUseRetiredUiTestActionCheckboxes() throws IOException {
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
    void scannerPaneDoesNotUseRetiredUiTextFields() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerTextFieldsAdapter"));
        assertFalse(compiledSource.contains("searchTermsField"));
        assertFalse(compiledSource.contains("testActionsField"));
        assertFalse(compiledSource.contains("coordsTextField"));
        assertFalse(compiledSource.contains("searchAttribValueField"));
        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".scene.control.TextField"));
    }

    @Test
    void scannerPaneDoesNotUseRetiredUiStatusTextArea() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("component/pane/ARScannedElementPane.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("ScannerPreLaunchStatusTextAreaAdapter"));
        assertFalse(compiledSource.contains("countdownTextField"));
        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".scene.control.TextArea"));
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

        assertFalse(compiledSource.contains("import " + RETIRED_UI_PACKAGE + "."));
        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".scene.control"));
        assertFalse(compiledSource.contains(RETIRED_UI_PACKAGE + ".scene.layout"));
        assertFalse(compiledSource.contains("ComboBox"));
        assertFalse(compiledSource.contains("ListCell"));
        assertFalse(compiledSource.contains("AnchorPane"));
        assertFalse(compiledSource.contains("GridPane"));
        assertFalse(compiledSource.contains("StackPane"));
        assertFalse(compiledSource.contains("Separator"));
        assertFalse(compiledSource.contains("FXCollections"));
        assertFalse(compiledSource.contains("ObservableList"));
        assertFalse(compiledSource.contains("comboBoxBlocks"));
        assertFalse(compiledSource.contains("elementFocusComboBox"));
        assertFalse(compiledSource.contains("buildElementFocusComboBox"));
        assertFalse(compiledSource.contains("ElementScanProfileCell"));
        assertFalse(compiledSource.contains("createTopPanel"));
        assertFalse(compiledSource.contains("createContentPanel"));
        assertFalse(compiledSource.contains("addScannerGridContainer"));
        assertFalse(compiledSource.contains("loadAllBlocks"));
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
        assertFalse(compiledSource.contains("comboBoxBlocks.getValue()"));
        assertFalse(compiledSource.contains("elementFocusComboBox.getValue()"));
        assertTrue(compiledSource.contains("selectedBlockOption"));
        assertTrue(compiledSource.contains("selectedElementScanProfile"));
        assertTrue(compiledSource.contains("ScannerRuntimePort"));
        assertFalse(compiledSource.contains("ARScannedElementPanePort"));
        assertFalse(compiledSource.contains("launchBotJobButton.setOnMouseClicked"));
        assertFalse(compiledSource.contains("stopBotJobButton.setOnMouseClicked"));
        assertFalse(compiledSource.contains("launchBotJobButton.setDisable"));
        assertFalse(compiledSource.contains("stopBotJobButton.setDisable"));
        assertFalse(compiledSource.contains("launchBotJobButton"));
        assertFalse(compiledSource.contains("stopBotJobButton"));
        assertFalse(compiledSource.contains("launchButtonRow"));
        assertFalse(compiledSource.contains("cloneElementsButton"));
        assertFalse(compiledSource.contains("configureButton"));
        assertFalse(compiledSource.contains("pageScannerButton"));
        assertFalse(compiledSource.contains("ocrConfigButton"));
        assertFalse(compiledSource.contains("refreshWebPageButton"));
        assertFalse(compiledSource.contains("cleanListButton"));
        assertFalse(compiledSource.contains("searchButton"));
        assertFalse(compiledSource.contains("buildButton("));
        assertFalse(compiledSource.contains("buildImageView("));
        assertFalse(compiledSource.contains("pageScannerRow("));
        assertTrue(compiledSource.contains("preLaunchActionEnabled"));
    }

    @Test
    void legacyMessageDialogsPublishToReactInsteadOfSwing() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("facade/PerformMessage.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("javax.swing"));
        assertFalse(compiledSource.contains("java.awt"));
        assertFalse(compiledSource.contains("JDialog"));
        assertFalse(compiledSource.contains("JButton"));
        assertFalse(compiledSource.contains("JLabel"));
        assertFalse(compiledSource.contains("JPanel"));
        assertTrue(compiledSource.contains("ScannerDialogPublisher"));
    }

    @Test
    void priorityWarningsPublishToReactInsteadOfOptionPane() throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve("util/ARPriorities.java"));
        String compiledSource = source.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledSource.contains("javax.swing"));
        assertFalse(compiledSource.contains("JOptionPane"));
        assertTrue(compiledSource.contains("ScannerDialogPublisher"));
    }

    @Test
    void toolbarFileActionsDoNotUseDesktopAwtOpeners() throws IOException {
        String nativeOperations = Files.readString(SOURCE_ROOT.resolve("facade/BotJobNativeOperationService.java"));
        String excelUtils = Files.readString(SOURCE_ROOT.resolve("util/ExcelUtils.java"));
        String excelWriter = Files.readString(SOURCE_ROOT.resolve("readersAndWriters/ExcelWriter.java"));
        String compiledNativeOperations = nativeOperations.replaceAll("(?s)/\\*.*?\\*/", "");
        String compiledExcelUtils = excelUtils.replaceAll("(?s)/\\*.*?\\*/", "");
        String compiledExcelWriter = excelWriter.replaceAll("(?s)/\\*.*?\\*/", "");

        assertFalse(compiledNativeOperations.contains("java.awt.Desktop"));
        assertFalse(compiledNativeOperations.contains("Desktop.getDesktop"));
        assertFalse(compiledExcelUtils.contains("import java.awt.*"));
        assertFalse(compiledExcelUtils.contains("Desktop.getDesktop"));
        assertFalse(compiledExcelWriter.contains("import java.awt.*"));
        assertFalse(compiledExcelWriter.contains("new Robot()"));
        assertFalse(compiledExcelWriter.contains("Toolkit.getDefaultToolkit"));
    }

    @Test
    void ocrDomainUsesNeutralBoundsInsteadOfAwtTypes() throws IOException {
        String ocrWord = Files.readString(SOURCE_ROOT.resolve("ocr/bridge/OcrWord.java"));
        String visionElement = Files.readString(SOURCE_ROOT.resolve("vision/VisionElement.java"));
        String ocrBox = Files.readString(SOURCE_ROOT.resolve("ocr/bridge/OcrBox.java"));
        String openCvUtils = Files.readString(SOURCE_ROOT.resolve("vision/ocr/OcrOpenCvUtils.java"));
        String webPageOcrService = Files.readString(SOURCE_ROOT.resolve("vision/WebPageOcrService.java"));
        String buttonDetectionService = Files.readString(SOURCE_ROOT.resolve("vision/ButtonDetectionService.java"));
        String tess4jAdapter = Files.readString(SOURCE_ROOT.resolve("vision/Tess4jOcrBoxAdapter.java"));
        String annotatedRenderer = Files.readString(SOURCE_ROOT.resolve("vision/AnnotatedImageRenderer.java"));
        String webScreenshotCapture = Files.readString(SOURCE_ROOT.resolve("util/WebScreenshotCapture.java"));
        String ocrTestService = Files.readString(SOURCE_ROOT.resolve("facade/OcrTestService.java"));
        String pageOcrDumper = Files.readString(SOURCE_ROOT.resolve("util/PageOcrDumper.java"));

        assertFalse(ocrWord.contains("java.awt.Rectangle"));
        assertFalse(visionElement.contains("java.awt.Rectangle"));
        assertFalse(ocrBox.contains("java.awt.Rectangle"));
        assertFalse(webPageOcrService.contains("java.awt.Rectangle"));
        assertFalse(buttonDetectionService.contains("java.awt.Rectangle"));
        assertFalse(tess4jAdapter.contains("java.awt.Rectangle"));
        assertFalse(tess4jAdapter.contains("import java.awt"));
        assertFalse(openCvUtils.contains("DataBufferByte"));
        assertFalse(annotatedRenderer.contains("BasicStroke"));
        assertFalse(annotatedRenderer.contains("java.awt.Color"));
        assertFalse(annotatedRenderer.contains("Graphics2D"));
        assertFalse(annotatedRenderer.contains("RenderingHints"));
        assertFalse(webScreenshotCapture.contains("Graphics2D"));
        assertFalse(annotatedRenderer.contains("BufferedImage"));
        assertFalse(ocrTestService.contains("BufferedImage"));
        assertFalse(pageOcrDumper.contains("BufferedImage"));
        assertTrue(ocrWord.contains("OcrBox"));
        assertTrue(visionElement.contains("OcrBox"));
    }

    @Test
    void mavenBuildDoesNotDeclareRetiredUiDependenciesOrPlugin() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertFalse(pom.contains("org.open" + "jfx"));
        assertFalse(pom.contains(RETIRED_UI_PACKAGE + "-controls"));
        assertFalse(pom.contains(RETIRED_UI_PACKAGE + "-web"));
        assertFalse(pom.contains(RETIRED_UI_PACKAGE + "-maven-plugin"));
        assertFalse(pom.contains("jcefmaven"));
        assertFalse(pom.contains("org.cef"));
    }
}
