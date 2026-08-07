package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import java.util.List;

/** Builds instruction rows for scanner update-all requests before the database write. */
public final class ScannerUpdatePreparationService {

    public void prepare(
            ScannerInsertPreparationService.ActionsPort actions,
            ScannerInsertPreparationService.TargetExtractor targetExtractor,
            ScannerInsertPreparationService.PanePort pane,
            List<InstructionLoad> instructionList,
            ElementDTO[] elements,
            int blockId,
            int firstOrder) {
        instructionList.clear();
        targetExtractor.initialize(pane.scannerTargetContext());

        int nextOrder = firstOrder;
        if (elements == null) {
            return;
        }
        for (ElementDTO elementDTO : elements) {
            TargetElement target = targetExtractor.extractPickClone(elementDTO);
            if (ScannerExecutionTypeOverride.apply(elementDTO, target)
                    == ScannerExecutionTypeOverride.Status.INVALID) {
                continue;
            }
            pane.prepareToInsertElementDTO(instructionList, blockId, nextOrder, target, true);
            nextOrder++;
        }
    }
}
