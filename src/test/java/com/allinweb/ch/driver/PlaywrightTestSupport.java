package com.allinweb.ch.driver;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class PlaywrightTestSupport {
    private static Boolean browserLaunchAvailable;
    private static String browserLaunchFailure;

    private PlaywrightTestSupport() {}

    public static void assumeBrowserLaunchAvailable() {
        assumeTrue(browserLaunchAvailable(), () -> "Playwright browser launch unavailable: " + browserLaunchFailure);
    }

    private static synchronized boolean browserLaunchAvailable() {
        if (browserLaunchAvailable != null) {
            return browserLaunchAvailable;
        }
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(
                Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")))) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);
            locateBrowserExecutable().ifPresent(options::setExecutablePath);
            try (Browser ignored = playwright.chromium().launch(options)) {
                browserLaunchAvailable = true;
                return true;
            }
        } catch (RuntimeException error) {
            browserLaunchFailure = error.getMessage();
            browserLaunchAvailable = false;
            return false;
        }
    }

    public static java.util.Optional<Path> locateBrowserExecutable() {
        String override = System.getenv("CHROME_EXECUTABLE_PATH");
        List<Path> candidates = new java.util.ArrayList<>();
        if (override != null && !override.isBlank()) {
            candidates.add(Path.of(override));
        }
        candidates.add(Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
        candidates.add(Path.of("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"));
        candidates.add(Path.of("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"));
        candidates.add(Path.of("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"));
        candidates.add(Path.of("/usr/bin/google-chrome"));
        candidates.add(Path.of("/usr/bin/chromium"));
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(path -> path.toFile().isFile())
                .findFirst();
    }
}
