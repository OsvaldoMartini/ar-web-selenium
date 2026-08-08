package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DesktopAppBrowserLauncherTest {

    @Test
    void launchesDetectedChromeDirectlyWithDesktopAppArguments() {
        String chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        AtomicReference<List<String>> launchedCommand = new AtomicReference<>();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Windows 11",
                Map.of("ProgramFiles", "C:\\Program Files"),
                candidate -> candidate.equals(Path.of(chrome)),
                command -> launchedCommand.set(List.copyOf(command)));

        assertTrue(launcher.launch("http://127.0.0.1:53972/"));
        assertEquals(
                List.of(
                        chrome,
                        "--app=http://127.0.0.1:53972/?desktopShell=1",
                        "--window-size=1240,820",
                        "--new-window",
                        "--no-first-run",
                        "--no-default-browser-check"),
                launchedCommand.get());
        assertFalse(launchedCommand.get().contains("cmd"));
    }

    @Test
    void triesTheNextDetectedChromiumBrowserWhenTheFirstLaunchFails() {
        String chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        String edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
        List<List<String>> attempts = new ArrayList<>();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Windows 11",
                Map.of(
                        "ProgramFiles", "C:\\Program Files",
                        "ProgramFiles(x86)", "C:\\Program Files (x86)"),
                candidate -> candidate.equals(Path.of(chrome)) || candidate.equals(Path.of(edge)),
                command -> {
                    attempts.add(List.copyOf(command));
                    if (command.get(0).equals(chrome)) throw new IOException("Chrome unavailable");
                });

        assertTrue(launcher.launch("http://127.0.0.1:54525"));
        assertEquals(2, attempts.size());
        assertEquals(chrome, attempts.get(0).get(0));
        assertEquals(edge, attempts.get(1).get(0));
    }

    @Test
    void reportsUnavailableSoTheCallerCanUseItsDefaultBrowserFallback() {
        AtomicBoolean processStarted = new AtomicBoolean();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Windows 11",
                Map.of(),
                candidate -> false,
                command -> processStarted.set(true));

        assertFalse(launcher.launch("http://127.0.0.1:54525"));
        assertFalse(processStarted.get());
    }

    @Test
    void detectsChromiumOnLinuxAndStillStartsItWithoutAShell() {
        Path chromium = Path.of("/usr/bin/chromium");
        AtomicReference<List<String>> launchedCommand = new AtomicReference<>();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Linux",
                Map.of(),
                candidate -> candidate.equals(chromium),
                command -> launchedCommand.set(List.copyOf(command)));

        assertTrue(launcher.launch("http://127.0.0.1:54525"));
        assertEquals(chromium.toString(), launchedCommand.get().get(0));
        assertEquals("--app=http://127.0.0.1:54525?desktopShell=1", launchedCommand.get().get(1));
        assertFalse(launchedCommand.get().contains("sh"));
    }

    @Test
    void preservesExistingQueryAndFragmentWhenAddingDesktopShellFlag() {
        assertEquals(
                "http://127.0.0.1:54525/?session=main&desktopShell=1#content",
                DesktopAppBrowserLauncher.withDesktopShellFlag(
                        "http://127.0.0.1:54525/?session=main#content"));
        assertEquals(
                "http://127.0.0.1:54525/?desktopShell=1",
                DesktopAppBrowserLauncher.withDesktopShellFlag(
                        "http://127.0.0.1:54525/?desktopShell=0"));
        assertEquals(
                "http://127.0.0.1:54525/?openBotJob=42&desktopShell=1",
                DesktopAppBrowserLauncher.withDesktopShellFlag(
                        "http://127.0.0.1:54525/?openBotJob=42"));
    }

    @Test
    void launchesStrictOcrConfigRouteAsASeparateChromiumAppWindow() {
        String chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        AtomicReference<List<String>> launchedCommand = new AtomicReference<>();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Windows 11",
                Map.of("ProgramFiles", "C:\\Program Files"),
                candidate -> candidate.equals(Path.of(chrome)),
                command -> launchedCommand.set(List.copyOf(command)));
        String url = ARWebSocketServer.ocrWorkspaceDesktopUrl(
                53972,
                OcrWorkspaceCoordinator.Kind.CONFIG,
                "ocr-config-window-42");

        assertTrue(launcher.launch(url));
        assertEquals(
                "--app=http://127.0.0.1:53972/"
                        + "?desktopShell=1&openOcr=config&ocrSession=ocr-config-window-42",
                launchedCommand.get().get(1));
        assertEquals("--window-size=1240,820", launchedCommand.get().get(2));
        assertEquals("--new-window", launchedCommand.get().get(3));
        assertFalse(launchedCommand.get().contains("cmd"));
    }

    @Test
    void rejectsOcrRouteWhenSessionPrefixDoesNotMatchWindowKind() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ARWebSocketServer.ocrWorkspaceDesktopUrl(
                        53972,
                        OcrWorkspaceCoordinator.Kind.CONFIG,
                        "not-an-ocr-workspace"));
    }

    @Test
    void launchesStrictPageScannerRouteAsASeparateChromiumAppWindow() {
        String chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        AtomicReference<List<String>> launchedCommand = new AtomicReference<>();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Windows 11",
                Map.of("ProgramFiles", "C:\\Program Files"),
                candidate -> candidate.equals(Path.of(chrome)),
                command -> launchedCommand.set(List.copyOf(command)));
        String url = ARWebSocketServer.pageScannerDesktopUrl(53972, "page-scanner-window-42");

        assertTrue(launcher.launchMaximized(url));
        assertEquals(
                "--app=http://127.0.0.1:53972/"
                        + "?desktopShell=1&openPageScanner=preScan&pageScannerSession=page-scanner-window-42",
                launchedCommand.get().get(1));
        assertEquals("--start-maximized", launchedCommand.get().get(2));
        assertEquals("--new-window", launchedCommand.get().get(3));
        assertFalse(launchedCommand.get().contains("cmd"));
    }

    @Test
    void launchesStrictBotJobDetailsRouteWithPersistentWindowControlSession() {
        String chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
        AtomicReference<List<String>> launchedCommand = new AtomicReference<>();
        DesktopAppBrowserLauncher launcher = new DesktopAppBrowserLauncher(
                "Windows 11",
                Map.of("ProgramFiles", "C:\\Program Files"),
                candidate -> candidate.equals(Path.of(chrome)),
                command -> launchedCommand.set(List.copyOf(command)));
        String controlSession = "bot-job-window-123e4567-e89b-42d3-a456-426614174000";
        String url = ARWebSocketServer.botJobDetailsDesktopUrl(53972, 42, controlSession);

        assertTrue(launcher.launch(url));
        assertEquals(
                "--app=http://127.0.0.1:53972/"
                        + "?desktopShell=1&openBotJob=42&botJobWindowSession=" + controlSession,
                launchedCommand.get().get(1));
        assertEquals("--window-size=1240,820", launchedCommand.get().get(2));
        assertEquals("--new-window", launchedCommand.get().get(3));
        assertFalse(launchedCommand.get().contains("cmd"));
    }

    @Test
    void rejectsMalformedPageScannerRoutes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ARWebSocketServer.pageScannerDesktopUrl(53972, "preScannerGrid"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ARWebSocketServer.pageScannerDesktopUrl(0, "page-scanner-window-42"));
    }

    @Test
    void rejectsMalformedBotJobDetailsWindowRoutes() {
        String controlSession = "bot-job-window-123e4567-e89b-42d3-a456-426614174000";
        assertThrows(
                IllegalArgumentException.class,
                () -> ARWebSocketServer.botJobDetailsDesktopUrl(0, 42, controlSession));
        assertThrows(
                IllegalArgumentException.class,
                () -> ARWebSocketServer.botJobDetailsDesktopUrl(53972, 0, controlSession));
        assertThrows(
                IllegalArgumentException.class,
                () -> ARWebSocketServer.botJobDetailsDesktopUrl(53972, 42, "botJobTasks"));
    }
}
