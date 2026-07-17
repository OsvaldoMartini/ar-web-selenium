package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.util.ExcelUtils;
import com.allinweb.ch.util.ExtractedData;
import java.util.List;

public final class ScannerPreLaunchExcelLoader {

    public ExtractedData load(String excelPath, PerformLists performLists) throws Exception {
        return new ExcelReader()
                .extractData(
                        excelPath,
                        performLists.getAllActions(),
                        ExcelUtils.buildAliasMap(performLists.getListBlock()));
    }

    public ExtractedData ensureEmptyDataRow(ExtractedData extractedData) {
        ExtractedData data = extractedData == null ? new ExtractedData() : extractedData;
        if (data.getNumberOfDataRows() == null || data.getNumberOfDataRows() == 0) {
            data.addField("$EMPTY");
            data.addFieldValue("$EMPTY", "$EMPTY", 0);
        }
        return data;
    }

    public boolean hasExcelError(ExtractedData extractedData) {
        return extractedData != null && extractedData.getErrorMessage() != null;
    }

    public boolean requiresMultipleRowsConfirmation(ExtractedData extractedData, List<InstructionLoad> excelDataGoto) {
        return extractedData.getNumberOfDataRows() != null
                && extractedData.getNumberOfDataRows() > 1
                && excelDataGoto.isEmpty();
    }
}
