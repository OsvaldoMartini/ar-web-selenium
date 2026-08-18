package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScannerSharedPlaywrightPageContractTest {

    private static final Path SOURCE = Path.of(
            "src", "main", "java", "com", "allinweb", "ch", "component", "pane", "ScannerRuntimeBackend.java");

    @Test
    void testClickAndInputNeverMutateThePageBeforeDispatch() throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf(
                "public void testingActions(TargetElement originTarget, String testType, String inputValueOverride)");
        int end = source.indexOf("private List<ReferenceLoadDTO> buildSyntheticReferences", start);
        assertTrue(start >= 0 && end > start, "testingActions source boundary must remain discoverable");

        String method = source.substring(start, end);
        assertTrue(method.contains("currentPlaywrightDriver()"));
        assertFalse(method.contains(".openBrowser("));
        assertFalse(method.contains(".openOrNavigate("));
        assertFalse(method.contains(".navigate("));
        assertFalse(method.contains(".reload("));
    }

    @Test
    void testRunUsesThePreserveCurrentPageStartupPath() throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("private boolean openBrowserForTestRun()");
        int end = source.indexOf("public void refreshBlocks", start);
        assertTrue(start >= 0 && end > start, "TEST RUN browser startup source boundary must remain discoverable");

        String startup = source.substring(start, end);
        assertTrue(startup.contains("openBrowserPreservingCurrentPage"));
    }
}
