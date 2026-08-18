package com.allinweb.ch.facade;

import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.TargetElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScannerSyntheticReferenceService {
    public List<ReferenceLoadDTO> build(TargetElement target, Integer botJobId, Integer homeBankingId) {
        List<ReferenceLoadDTO> references = new ArrayList<>();
        if (target == null
                || target.getSavedReferences() == null
                || target.getSavedReferences().isEmpty()) {
            return references;
        }

        for (Map.Entry<String, String> entry : target.getSavedReferences().entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey().isBlank()
                    || entry.getValue() == null
                    || entry.getValue().isBlank()) {
                continue;
            }
            ReferenceLoadDTO reference = new ReferenceLoadDTO();
            reference.setReferenceType(entry.getKey());
            reference.setValue(entry.getValue());
            reference.setBotJobId(botJobId);
            reference.setHomeBankingId(homeBankingId);
            references.add(reference);
        }
        return references;
    }
}
