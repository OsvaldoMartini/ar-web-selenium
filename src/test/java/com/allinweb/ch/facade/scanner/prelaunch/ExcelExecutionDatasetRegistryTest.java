package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.util.ExtractedData;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExcelExecutionDatasetRegistryTest {

    @Test
    void loadsWorkbookOnlyOnceUntilClientClosesIt() throws Exception {
        ExcelExecutionDatasetRegistry registry = new ExcelExecutionDatasetRegistry(4);
        AtomicInteger reads = new AtomicInteger();
        Path workbook = Path.of("Bot Job 32.xlsx");

        ExcelExecutionDatasetRegistry.Dataset first = registry.load(workbook, () -> data(reads));
        ExcelExecutionDatasetRegistry.Dataset second = registry.load(workbook, () -> data(reads));

        assertSame(first, second);
        assertEquals(1, reads.get());
        assertTrue(registry.close(workbook));
        assertFalse(registry.close(workbook));
        registry.load(workbook, () -> data(reads));
        assertEquals(2, reads.get());
    }

    @Test
    void evictsLeastRecentlyUsedDatasetToBoundMemory() throws Exception {
        ExcelExecutionDatasetRegistry registry = new ExcelExecutionDatasetRegistry(2);
        Path first = Path.of("First.xlsx");
        Path second = Path.of("Second.xlsx");
        Path third = Path.of("Third.xlsx");

        registry.load(first, ExtractedData::new);
        registry.load(second, ExtractedData::new);
        registry.find(first);
        registry.load(third, ExtractedData::new);

        assertEquals(2, registry.size());
        assertNull(registry.find(second));
        assertNotNull(registry.find(first));
        assertNotNull(registry.find(third));
    }

    @Test
    void replacesWorkingDatasetWithoutInvokingTheDiskLoader() throws Exception {
        ExcelExecutionDatasetRegistry registry = new ExcelExecutionDatasetRegistry(2);
        Path workbook = Path.of("Working.xlsx");
        ExtractedData original = new ExtractedData();
        ExtractedData replacement = new ExtractedData();
        registry.load(workbook, () -> original);

        registry.replace(workbook, replacement);

        assertSame(replacement, registry.find(workbook).data());
    }

    private ExtractedData data(AtomicInteger reads) {
        reads.incrementAndGet();
        ExtractedData data = new ExtractedData();
        data.addFieldValue("USER", "martini", 0);
        return data;
    }
}
