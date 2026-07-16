package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.TargetElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScannerSyntheticReferenceServiceTest {
    private final ScannerSyntheticReferenceService service = new ScannerSyntheticReferenceService();

    @Test
    void buildsReferencesFromSavedReferences() {
        TargetElement target = new TargetElement();
        target.setSavedReferences(new LinkedHashMap<>(Map.of("xpath", "//button", "css", ".login")));

        List<ReferenceLoadDTO> references = service.build(target, 7, 11);

        assertEquals(2, references.size());
        assertTrue(references.stream().anyMatch(ref ->
                ref.getReferenceType().equals("xpath")
                        && ref.getValue().equals("//button")
                        && ref.getBotJobId().equals(7)
                        && ref.getHomeBankingId().equals(11)));
        assertTrue(references.stream().anyMatch(ref -> ref.getReferenceType().equals("css")));
    }

    @Test
    void skipsBlankKeysAndValues() {
        TargetElement target = new TargetElement();
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("", "//button");
        saved.put("css", " ");
        saved.put("xpath", "//input");
        target.setSavedReferences(saved);

        List<ReferenceLoadDTO> references = service.build(target, 7, 11);

        assertEquals(1, references.size());
        assertEquals("xpath", references.get(0).getReferenceType());
    }

    @Test
    void returnsEmptyForMissingTargetOrReferences() {
        assertTrue(service.build(null, 7, 11).isEmpty());
        assertTrue(service.build(new TargetElement(), 7, 11).isEmpty());
    }
}
