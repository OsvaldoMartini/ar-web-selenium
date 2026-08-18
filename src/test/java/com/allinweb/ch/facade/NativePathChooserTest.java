package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativePathChooserTest {

    @TempDir
    Path temporary;

    @Test
    void windowsFolderChooserUsesFixedScriptAndEnvironmentForInitialPath() {
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        File selected = NativePathChooser.choose(
                temporary.toFile(),
                true,
                "Windows 11",
                (command, variables) -> {
                    environment.set(variables);
                    assertEquals("powershell.exe", command.get(0));
                    assertTrue(command.contains("-STA"));
                    assertTrue(command.get(command.size() - 1).contains("FolderBrowserDialog"));
                    return new NativePathChooser.ProcessResult(0, temporary + System.lineSeparator());
                });

        assertEquals(temporary.toFile(), selected);
        assertEquals(temporary.toFile().getAbsolutePath(), environment.get().get("ARWEB_CHOOSER_INITIAL"));
    }

    @Test
    void reportChooserUsesFileDialogAndTreatsEmptySuccessAsCancellation() {
        File selected = NativePathChooser.choose(
                temporary.toFile(),
                false,
                "Windows 11",
                (command, environment) -> {
                    String script = command.get(command.size() - 1);
                    assertTrue(script.contains("OpenFileDialog"));
                    assertTrue(script.contains("*.pdf;*.txt"));
                    assertTrue(!script.contains("All files"));
                    return new NativePathChooser.ProcessResult(0, "");
                });

        assertNull(selected);

        File osCancellation = NativePathChooser.choose(
                temporary.toFile(),
                false,
                "Mac OS X",
                (command, environment) -> new NativePathChooser.ProcessResult(1, "User canceled."));
        assertNull(osCancellation);
    }

    @Test
    void buildsPlatformSpecificCommandsWithoutJavaDesktopApis() {
        assertEquals("osascript", NativePathChooser.command(false, "Mac OS X").arguments().get(0));
        assertEquals("zenity", NativePathChooser.command(true, "Linux").arguments().get(0));
        assertTrue(NativePathChooser.command(true, "Linux").arguments().contains("--directory"));
    }
}
