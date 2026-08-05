package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.util.ExtractedData;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Process-local owner of Excel datasets used by execution and detached inspection workspaces.
 *
 * <p>A workbook is parsed once and retained until the client explicitly closes it. The bounded
 * access-order map prevents abandoned detached pages from producing unbounded process memory.
 */
public final class ExcelExecutionDatasetRegistry {
    private static final int MAX_OPEN_DATASETS = 16;
    private static final ExcelExecutionDatasetRegistry INSTANCE =
            new ExcelExecutionDatasetRegistry(MAX_OPEN_DATASETS);

    private final int maximumEntries;
    private final LinkedHashMap<Path, Dataset> datasets =
            new LinkedHashMap<>(16, 0.75f, true);

    ExcelExecutionDatasetRegistry(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("Maximum entries must be positive");
        this.maximumEntries = maximumEntries;
    }

    public static ExcelExecutionDatasetRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized Dataset load(Path workbook, DatasetLoader loader) throws Exception {
        Path key = normalize(workbook);
        Dataset existing = datasets.get(key);
        if (existing != null) return existing;

        ExtractedData data = Objects.requireNonNull(loader.load(), "Excel dataset is required");
        Dataset loaded = new Dataset(key, data, Instant.now());
        datasets.put(key, loaded);
        evictOldestIfRequired();
        return loaded;
    }

    public synchronized Dataset find(Path workbook) {
        return datasets.get(normalize(workbook));
    }

    public synchronized Dataset replace(Path workbook, ExtractedData data) {
        Path key = normalize(workbook);
        Dataset replacement = new Dataset(key, Objects.requireNonNull(data, "Excel dataset is required"), Instant.now());
        datasets.put(key, replacement);
        evictOldestIfRequired();
        return replacement;
    }

    public synchronized boolean close(Path workbook) {
        return datasets.remove(normalize(workbook)) != null;
    }

    public synchronized int size() {
        return datasets.size();
    }

    synchronized void clear() {
        datasets.clear();
    }

    private void evictOldestIfRequired() {
        while (datasets.size() > maximumEntries) {
            Path oldest = datasets.entrySet().iterator().next().getKey();
            datasets.remove(oldest);
        }
    }

    private Path normalize(Path workbook) {
        if (workbook == null) throw new IllegalArgumentException("Excel workbook path is required");
        return workbook.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    public interface DatasetLoader {
        ExtractedData load() throws Exception;
    }

    public record Dataset(Path workbook, ExtractedData data, Instant loadedAt) {}
}
