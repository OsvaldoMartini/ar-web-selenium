package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ExtractedData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates ARPropertyManager and PerformLists singletons")
class ScannerPreLaunchExcelLoaderTest {

    @TempDir
    Path temporaryDirectory;

    private String previousExcelPath;
    private List<BlockLoadDTO> previousBlocks;

    @BeforeEach
    void captureExcelState() {
        previousExcelPath = ARPropertyManager.getInstance()
                .getProperties()
                .getProperty(ARPropertyEnum.PATH_EXCEL.getValue());
        previousBlocks = PerformLists.getInstance().getListBlock();
    }

    @AfterEach
    void restoreExcelState() {
        Properties properties = ARPropertyManager.getInstance().getProperties();
        if (previousExcelPath == null) {
            properties.remove(ARPropertyEnum.PATH_EXCEL.getValue());
        } else {
            properties.setProperty(ARPropertyEnum.PATH_EXCEL.getValue(), previousExcelPath);
        }
        if (previousBlocks != null) PerformLists.getInstance().setListBlock(previousBlocks);
    }

    @Test
    void loadCreatesMissingWorkbookUsingActiveBotJobNameBeforeReading() throws Exception {
        Path excelFolder = temporaryDirectory.resolve("excel");
        Path workbook = excelFolder.resolve("Bot Job 32.xlsx");
        Properties properties = ARPropertyManager.getInstance().getProperties();
        properties.setProperty(ARPropertyEnum.PATH_EXCEL.getValue(), excelFolder.toString());
        PerformLists lists = PerformLists.getInstance();
        lists.setListBlock(new ArrayList<>());

        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData result = loader.load(workbook.toString(), lists);

        assertTrue(Files.isRegularFile(workbook));
        assertNotNull(result);
        assertTrue(loader.close(workbook.toString()));
    }

    @Test
    void ensureEmptyDataRowAddsLegacyEmptyField() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();

        loader.ensureEmptyDataRow(extractedData);

        assertEquals(1, extractedData.getNumberOfDataRows());
        assertEquals("$EMPTY", extractedData.getFieldValue("$EMPTY", 0));
    }

    @Test
    void ensureEmptyDataRowLeavesExistingRowsUnchanged() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.addFieldValue("username", "martini", 0);

        loader.ensureEmptyDataRow(extractedData);

        assertEquals(1, extractedData.getNumberOfDataRows());
        assertEquals("martini", extractedData.getFieldValue("username", 0));
    }

    @Test
    void ensureEmptyDataRowCreatesDataWhenReaderReturnedNothing() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();

        ExtractedData extractedData = loader.ensureEmptyDataRow(null);

        assertEquals(1, extractedData.getNumberOfDataRows());
        assertEquals("$EMPTY", extractedData.getFieldValue("$EMPTY", 0));
    }

    @Test
    void hasExcelErrorDetectsReaderErrorMessage() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.setErrorMessage("Missing field");

        assertTrue(loader.hasExcelError(extractedData));
    }

    @Test
    void requiresMultipleRowsConfirmationWhenMultipleRowsWithoutExcelGoto() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.addFieldValue("username", "first", 0);
        extractedData.addFieldValue("username", "second", 1);

        assertTrue(loader.requiresMultipleRowsConfirmation(extractedData, List.of()));
        assertFalse(loader.requiresMultipleRowsConfirmation(extractedData, List.of(new InstructionLoad())));
    }

    @Test
    void requiresMultipleRowsConfirmationIgnoresSingleRow() {
        ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
        ExtractedData extractedData = new ExtractedData();
        extractedData.addFieldValue("username", "first", 0);

        assertFalse(loader.requiresMultipleRowsConfirmation(extractedData, List.of()));
    }
}
