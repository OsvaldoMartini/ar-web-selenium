package com.allinweb.ch.facade;

import com.allinweb.ch.model.CsvRow;
import com.allinweb.ch.model.CsvTable;

public final class ScannerCsvContentService {
    public String headerOnlyContent(CsvTable tableCSV) {
        StringBuilder sb = new StringBuilder();
        sb.append("0: ").append(String.join(",", tableCSV.getColumns())).append("\n");
        return sb.toString();
    }

    public String bancaStatoContent(CsvTable tableCSV, String delimiter) {
        StringBuilder sb = new StringBuilder();
        sb.append("KEY")
                .append(delimiter)
                .append(String.join(delimiter, tableCSV.getColumns()))
                .append("\n");

        int totalRows = tableCSV.getRows() == null ? 0 : tableCSV.getRows().size();
        for (int i = 0; i < totalRows; i++) {
            CsvRow row = tableCSV.getRows().get(i);
            String externalKey = totalRows == 1 ? "EXTERNAL" : "EXTERNAL_" + (i + 1);

            sb.append(externalKey).append(delimiter);
            for (int c = 0; c < tableCSV.getColumns().size(); c++) {
                String col = tableCSV.getColumns().get(c);
                sb.append(row != null ? row.get(col) : "");
                if (c < tableCSV.getColumns().size() - 1) {
                    sb.append(delimiter);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
