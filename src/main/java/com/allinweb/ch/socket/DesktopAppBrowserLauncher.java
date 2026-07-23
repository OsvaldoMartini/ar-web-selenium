package com.allinweb.ch.socket;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/** Opens the React desktop shell in a compact Chromium application window. */
@Slf4j
final class DesktopAppBrowserLauncher {
    private static final String DESKTOP_SHELL_PARAMETER = "desktopShell=1";
    private static final String WINDOW_SIZE = "1240,820";

    private final String osName;
    private final Map<String, String> environment;
    private final Predicate<Path> executableExists;
    private final ProcessStarter processStarter;

    DesktopAppBrowserLauncher() {
        this(
                System.getProperty("os.name", ""),
                System.getenv(),
                path -> path.toFile().isFile(),
                command -> new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start());
    }

    DesktopAppBrowserLauncher(
            String osName,
            Map<String, String> environment,
            Predicate<Path> executableExists,
            ProcessStarter processStarter) {
        this.osName = osName == null ? "" : osName;
        this.environment = environment == null ? Map.of() : environment;
        this.executableExists = executableExists;
        this.processStarter = processStarter;
    }

    boolean launch(String baseUrl) {
        return launch(baseUrl, WINDOW_SIZE);
    }

    boolean launch(String baseUrl, int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) {
            throw new IllegalArgumentException("Desktop shell dimensions must be positive");
        }
        return launch(baseUrl, windowWidth + "," + windowHeight);
    }

    private boolean launch(String baseUrl, String windowSize) {
        String appUrl = withDesktopShellFlag(baseUrl);
        for (Path executable : browserCandidates()) {
            if (!executableExists.test(executable)) continue;

            List<String> command = List.of(
                    executable.toString(),
                    "--app=" + appUrl,
                    "--window-size=" + windowSize,
                    "--new-window",
                    "--no-first-run",
                    "--no-default-browser-check");
            try {
                processStarter.start(command);
                log.info("Opened AR Web Scanner desktop shell with {}", executable);
                return true;
            } catch (IOException | SecurityException error) {
                log.warn("Could not open desktop shell with {}: {}", executable, error.getMessage());
            }
        }
        return false;
    }

    static String withDesktopShellFlag(String url) {
        int fragmentIndex = url.indexOf('#');
        String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
        String withoutFragment = fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;
        int queryIndex = withoutFragment.indexOf('?');
        String query = queryIndex >= 0 ? withoutFragment.substring(queryIndex + 1) : "";
        List<String> parameters = new ArrayList<>(List.of(query.split("&")));
        for (int index = 0; index < parameters.size(); index++) {
            String parameter = parameters.get(index);
            if (!parameter.equals("desktopShell") && !parameter.startsWith("desktopShell=")) continue;
            if (parameter.equals(DESKTOP_SHELL_PARAMETER)) return url;
            parameters.set(index, DESKTOP_SHELL_PARAMETER);
            String base = queryIndex >= 0 ? withoutFragment.substring(0, queryIndex + 1) : withoutFragment + "?";
            return base + String.join("&", parameters) + fragment;
        }

        String separator;
        if (queryIndex < 0) {
            separator = "?";
        } else if (withoutFragment.endsWith("?") || withoutFragment.endsWith("&")) {
            separator = "";
        } else {
            separator = "&";
        }
        return withoutFragment + separator + DESKTOP_SHELL_PARAMETER + fragment;
    }

    private List<Path> browserCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();
        addEnvironmentPath(candidates, "ARWEB_DESKTOP_BROWSER");
        addEnvironmentPath(candidates, "CHROME_EXECUTABLE_PATH");

        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        if (normalizedOs.contains("win")) {
            addUnder(candidates, "ProgramFiles", "Google\\Chrome\\Application\\chrome.exe");
            addUnder(candidates, "ProgramFiles(x86)", "Google\\Chrome\\Application\\chrome.exe");
            addUnder(candidates, "LOCALAPPDATA", "Google\\Chrome\\Application\\chrome.exe");
            addUnder(candidates, "ProgramFiles", "Microsoft\\Edge\\Application\\msedge.exe");
            addUnder(candidates, "ProgramFiles(x86)", "Microsoft\\Edge\\Application\\msedge.exe");
            addUnder(candidates, "LOCALAPPDATA", "Microsoft\\Edge\\Application\\msedge.exe");
            addUnder(candidates, "LOCALAPPDATA", "Chromium\\Application\\chrome.exe");
            addPathCandidates(candidates, List.of("chrome.exe", "msedge.exe", "chromium.exe"));
        } else if (normalizedOs.contains("mac")) {
            addPath(candidates, "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            addPath(candidates, "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
            addPath(candidates, "/Applications/Chromium.app/Contents/MacOS/Chromium");
            addUnder(candidates, "HOME", "Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            addUnder(candidates, "HOME", "Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
            addUnder(candidates, "HOME", "Applications/Chromium.app/Contents/MacOS/Chromium");
        } else {
            addPath(candidates, "/usr/bin/google-chrome");
            addPath(candidates, "/usr/bin/google-chrome-stable");
            addPath(candidates, "/usr/bin/microsoft-edge");
            addPath(candidates, "/usr/bin/microsoft-edge-stable");
            addPath(candidates, "/usr/bin/chromium");
            addPath(candidates, "/usr/bin/chromium-browser");
            addPath(candidates, "/snap/bin/chromium");
            addPathCandidates(candidates, List.of(
                    "google-chrome",
                    "google-chrome-stable",
                    "microsoft-edge",
                    "microsoft-edge-stable",
                    "chromium",
                    "chromium-browser"));
        }
        return new ArrayList<>(candidates);
    }

    private void addEnvironmentPath(Set<Path> candidates, String variableName) {
        addPath(candidates, environmentValue(variableName));
    }

    private void addUnder(Set<Path> candidates, String variableName, String relativePath) {
        String base = environmentValue(variableName);
        if (base == null || base.isBlank()) return;
        try {
            candidates.add(Paths.get(base).resolve(relativePath));
        } catch (InvalidPathException ignored) {
            log.debug("Ignoring invalid browser base path from {}", variableName);
        }
    }

    private void addPathCandidates(Set<Path> candidates, List<String> executableNames) {
        String searchPath = environmentValue("PATH");
        if (searchPath == null || searchPath.isBlank()) return;
        for (String directory : searchPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (directory.isBlank()) continue;
            for (String executableName : executableNames) {
                try {
                    candidates.add(Paths.get(directory).resolve(executableName));
                } catch (InvalidPathException ignored) {
                    log.debug("Ignoring invalid browser PATH entry");
                }
            }
        }
    }

    private void addPath(Set<Path> candidates, String candidate) {
        if (candidate == null || candidate.isBlank()) return;
        try {
            candidates.add(Paths.get(candidate));
        } catch (InvalidPathException ignored) {
            log.debug("Ignoring invalid browser executable path");
        }
    }

    private String environmentValue(String name) {
        String exact = environment.get(name);
        if (exact != null) return exact;
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @FunctionalInterface
    interface ProcessStarter {
        void start(List<String> command) throws IOException;
    }
}
