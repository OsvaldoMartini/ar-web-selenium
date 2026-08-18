package com.allinweb.ch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BlockLoadDTO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates ARPropertyManager and PerformLists singletons")
class ExcelUtilsTest {

    @TempDir
    Path temporaryDirectory;

    private final ARPropertyManager propertyManager = ARPropertyManager.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private String previousExcelPath;
    private List<BlockLoadDTO> previousBlocks;

    @BeforeEach
    void isolateExcelState() {
        Properties properties = propertyManager.getProperties();
        previousExcelPath = properties.getProperty(ARPropertyEnum.PATH_EXCEL.getValue());
        previousBlocks = performLists.getListBlock();
        performLists.setListBlock(new ArrayList<>());
    }

    @AfterEach
    void restoreExcelState() {
        Properties properties = propertyManager.getProperties();
        if (previousExcelPath == null) {
            properties.remove(ARPropertyEnum.PATH_EXCEL.getValue());
        } else {
            properties.setProperty(ARPropertyEnum.PATH_EXCEL.getValue(), previousExcelPath);
        }
        performLists.setListBlock(previousBlocks);
    }

    @Test
    void generatesAReadableNonEmptyWorkbook() throws Exception {
        Path outputFolder = temporaryDirectory.resolve("excel");
        setExcelPath(outputFolder);

        File generated = new ExcelUtils().generateExcelFiles(null, "Payments", null, false);

        assertTrue(generated.isFile());
        assertTrue(generated.length() > 0L);
        assertEquals(outputFolder.resolve("Payments.xlsx").toRealPath(), generated.toPath());
        try (XSSFWorkbook workbook = new XSSFWorkbook(generated)) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("#Payments default block", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void rejectsAnExcelOutputPathThatIsARegularFile() throws Exception {
        Path regularFile = Files.writeString(temporaryDirectory.resolve("excel.txt"), "not a folder");
        setExcelPath(regularFile);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ExcelUtils().generateExcelFiles(null, "Payments", null, false));

        assertTrue(error.getMessage().contains("must point to a folder"));
    }

    @Test
    void propagatesWorkbookPublicationFailure() throws Exception {
        Path outputFolder = Files.createDirectories(temporaryDirectory.resolve("excel"));
        Files.writeString(outputFolder.resolve("blocked"), "not a directory");
        setExcelPath(outputFolder);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ExcelUtils().generateExcelFiles(
                        null, "blocked" + File.separator + "Payments", null, false));

        assertTrue(error.getMessage().contains("Excel output folder does not exist"));
    }

    private void setExcelPath(Path path) {
        propertyManager.getProperties().setProperty(ARPropertyEnum.PATH_EXCEL.getValue(), path.toString());
    }
}
