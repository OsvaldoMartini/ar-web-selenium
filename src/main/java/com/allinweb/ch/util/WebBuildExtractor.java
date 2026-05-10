package com.allinweb.ch.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the on-disk location of the bundled React build under
 * {@code src/main/resources/build/}. When the app runs from an exploded
 * classpath (IDE / mvn javafx:run), the original {@code file:} URL is returned
 * unchanged. When packaged as a fat JAR, the entire {@code /build/} subtree is
 * extracted once per JVM run to a temp directory so that JavaFX WebView can
 * resolve relative paths, fetch JSON locales, and load hashed media URLs that
 * would otherwise fail under a {@code jar:} scheme.
 */
@Slf4j
public final class WebBuildExtractor {

    private static final String RESOURCE_ROOT = "/build";
    private static final String INDEX_FILE = "index.html";

    private static volatile URL cachedIndexUrl;

    private WebBuildExtractor() {}

    /** External-form URL of {@code build/index.html} suitable for {@code WebEngine.load(...)}. */
    public static String getIndexUrl() {
        URL url = cachedIndexUrl;
        if (url == null) {
            synchronized (WebBuildExtractor.class) {
                url = cachedIndexUrl;
                if (url == null) {
                    url = resolveIndexUrl();
                    cachedIndexUrl = url;
                }
            }
        }
        return url.toExternalForm();
    }

    private static URL resolveIndexUrl() {
        URL resource = WebBuildExtractor.class.getResource(RESOURCE_ROOT + "/" + INDEX_FILE);
        if (resource == null) {
            throw new IllegalStateException("Resource " + RESOURCE_ROOT + "/" + INDEX_FILE + " not found on classpath");
        }
        if (!"jar".equals(resource.getProtocol())) {
            log.info("WebBuildExtractor: serving build from exploded classpath: {}", resource);
            return resource;
        }
        try {
            Path indexOnDisk = extractFromJar();
            log.info("WebBuildExtractor: extracted JAR build to {}", indexOnDisk);
            return indexOnDisk.toUri().toURL();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract /build/ from JAR", e);
        }
    }

    private static Path extractFromJar() throws IOException {
        Path tempDir = Files.createTempDirectory("ar-scanner-build-");
        registerCleanup(tempDir);

        URI rootUri;
        try {
            rootUri = WebBuildExtractor.class.getResource(RESOURCE_ROOT).toURI();
        } catch (Exception e) {
            throw new IOException("Cannot resolve URI of " + RESOURCE_ROOT, e);
        }

        try (FileSystem fs = FileSystems.newFileSystem(rootUri, Collections.emptyMap())) {
            Path inJarRoot = fs.getPath(RESOURCE_ROOT);
            try (Stream<Path> walk = Files.walk(inJarRoot)) {
                walk.forEach(src -> copyEntry(src, inJarRoot, tempDir));
            }
        }

        Path index = tempDir.resolve(INDEX_FILE);
        if (!Files.isRegularFile(index)) {
            throw new IOException("index.html missing after extraction at " + index);
        }
        return index;
    }

    private static void copyEntry(Path src, Path inJarRoot, Path destRoot) {
        try {
            String relative = inJarRoot.relativize(src).toString();
            Path dest = destRoot.resolve(relative);
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest);
            } else {
                Path parent = dest.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = Files.newInputStream(src)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract " + src, e);
        }
    }

    private static void registerCleanup(Path dir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }, "WebBuildExtractor-cleanup"));
    }
}
