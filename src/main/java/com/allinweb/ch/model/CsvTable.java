package com.allinweb.ch.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CsvTable {

    private final List<CsvRow> rows = new ArrayList<>();

    /** Returns the internal rows (read-only if you prefer you can wrap it). */
    public List<CsvRow> getRows() {
        return rows;
    }

    /** Ensures row exists; if rowIndex is beyond current size, adds new rows up to that index. */
    private CsvRow ensureRow(int rowIndex) {
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be >= 0");
        }
        while (rows.size() <= rowIndex) {
            rows.add(new CsvRow());
        }
        return rows.get(rowIndex);
    }

    /**
     * Add/update ONE column in the given rowIndex.
     * - If row doesn't exist, it is created.
     * - If column exists, value is updated (column order stays as first insertion order).
     * - If column is new, it is appended in insertion order.
     */
    public void put(int rowIndex, String column, String value) {
        CsvRow row = ensureRow(rowIndex);
        row.set(column, value);
    }

    /**
     * Add/update MULTIPLE columns in the given rowIndex, in the iteration order of the map.
     * IMPORTANT: if you want strict "order of addition", pass a LinkedHashMap here.
     */
    public void putAll(int rowIndex, Map<String, String> columns) {
        CsvRow row = ensureRow(rowIndex);
        for (Map.Entry<String, String> e : columns.entrySet()) {
            row.set(e.getKey(), e.getValue());
        }
    }

    /** Convenience: get value (returns "" if missing). */
    public String get(int rowIndex, String column) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return "";
        return rows.get(rowIndex).get(column);
    }
}
