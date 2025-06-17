package com.allinweb.ch.socket;


import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simulates a CSV file with dynamic columns, missing values, and row numbering.
 */
public class CsvSimulatorWithColumns {
    private List<String> columns; // Column headers
    private List<List<String>> rows; // Data rows
    private static final String END_OF_FILE_MARKER = "END OF FILE";

    public CsvSimulatorWithColumns(List<String> columns) {
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>();
    }

    /**
     * Adds a row with values matching the columns.
     * Missing values are filled with empty strings.
     * @param values Array of values; may be less than columns.
     */
    public void addRow(String... values) {
        List<String> row = new ArrayList<>();
        int maxCols = columns.size();

        for (int i = 0; i < maxCols; i++) {
            if (i < values.length) {
                row.add(values[i]);
            } else {
                row.add(""); // fill missing with empty string
            }
        }
        rows.add(row);
    }

    /**
     * Returns the CSV content as a string, with row numbers and aligned columns.
     * @return String representing the CSV content.
     */
    public String getCsvContent() {
        StringBuilder sb = new StringBuilder();
        // Add header row
        sb.append("0: ").append(String.join(",", columns)).append("\n");

        int rowNumber = 1;
        for (List<String> row : rows) {
            sb.append(rowNumber).append(": ");
            sb.append(String.join(",", row));
            sb.append("\n");
            rowNumber++;
        }
        sb.append(END_OF_FILE_MARKER);
        return sb.toString();
    }

    /**
     * Returns the CSV content as a string, with row numbers and aligned columns.
     * @return String representing the CSV content.
     */
    public String getBancaStatoCsvContent() {
        StringBuilder sb = new StringBuilder();

        // Header row
        sb.append("KEY,").append(String.join(",", columns)).append("\n");

        // Data rows
        for (List<String> row : rows) {
            sb.append("EXTERNAL,");
            sb.append(String.join(",", row));
            sb.append("\n");
        }

        sb.append(END_OF_FILE_MARKER);
        return sb.toString();
    }

    /**
     * Prints the CSV content.
     */
    public void printCsv() {
        System.out.println(getCsvContent());
    }

    // Example usage
    public static void main(String[] args) {
        List<String> headers = Arrays.asList("Name", "Age", "Country", "Email");
        CsvSimulatorWithColumns csv = new CsvSimulatorWithColumns(headers);

        csv.addRow("Alice", "30", "USA", "alice@example.com");
        csv.addRow("Bob", "25");
        csv.addRow("Charlie", "35", "Canada");
        csv.addRow("Dana", "28", "UK", "dana@example.com");
        csv.addRow("Eve");

        // Write to a file
        String csvContent = csv.getBancaStatoCsvContent();
        CsvFileWriter.writeToFile("output.csv", csvContent);
    }

    public class CsvFileWriter {
        public static void writeToFile(String filename, String content) {
            try (FileWriter writer = new FileWriter(filename)) {
                writer.write(content);
                System.out.println("CSV written to file: " + filename);
            } catch (IOException e) {
                System.err.println("Error writing file: " + e.getMessage());
            }
        }
    }





}