package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockOptions;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ScannerBlockOptionDeduplicationServiceTest {

    private final ScannerBlockOptionDeduplicationService service = new ScannerBlockOptionDeduplicationService();

    @Test
    void detectsDistinctOptionsByText() {
        Predicate<BlockOptions> predicate = service.distinctByText();

        assertTrue(predicate.test(option("Login", 1)));
        assertFalse(predicate.test(option("Login", 2)));
        assertTrue(predicate.test(option("Logout", 1)));
    }

    @Test
    void detectsDistinctOptionsByTextAndBlockId() {
        Predicate<BlockOptions> predicate = service.distinctByTextAndBlockId();

        assertTrue(predicate.test(option("Login", 1)));
        assertTrue(predicate.test(option("Login", 2)));
        assertFalse(predicate.test(option("Login", 1)));
    }

    private static BlockOptions option(String text, Integer blockId) {
        return new BlockOptions(text, text, null, blockId, null);
    }
}
