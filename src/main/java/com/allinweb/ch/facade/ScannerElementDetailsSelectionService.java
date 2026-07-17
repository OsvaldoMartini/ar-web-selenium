package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;

/** Selects a scanner element for details/delete display outside the scene shell. */
public final class ScannerElementDetailsSelectionService {

    public void select(TargetExtractor targetExtractor, ScannerElementTestActionService.PanePort pane, ElementDTO elementDTO) {
        targetExtractor.initialize(pane.scannerTargetContext());
        pane.setTargetSelected(targetExtractor.extractPickClone(elementDTO));
        pane.itPrintsElementDTO();
    }

    public interface TargetExtractor {
        void initialize(ScannerTargetContext scannerTargetContext);

        TargetElement extractPickClone(ElementDTO elementDTO);
    }

}
