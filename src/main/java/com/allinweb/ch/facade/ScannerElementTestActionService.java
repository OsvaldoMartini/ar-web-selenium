package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.TargetElementHelper;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

/** JavaFX-free orchestration for running a scanner element test action. */
@Slf4j
public final class ScannerElementTestActionService {

    public void run(
            PerformActions performActions,
            TargetElementHelper targetElementHelper,
            PanePort pane,
            ElementDTO sourceElement,
            ElementDTO incomingElement,
            String testType,
            String defaultValue) {
        targetElementHelper.initialize(performActions, pane.scannerTargetContext());
        pane.setTargetSelected(targetElementHelper.extractPickClone(sourceElement));
        applyForceCoordinatesFromIncomingDto(pane.targetSelected(), incomingElement);
        pane.itPrintsElementDTO();
        pane.testingActions(pane.targetSelected(), testType, defaultValue);
    }

    private void applyForceCoordinatesFromIncomingDto(TargetElement target, ElementDTO elementDTO) {
        if (target == null || elementDTO == null) return;
        String incoming = elementDTO.getForceCoordinates();
        log.info(
                "applyForceCoordinatesFromIncomingDto - incoming='{}', existingOnTarget='{}'",
                incoming,
                target.getForceCoordinates());
        if (!Strings.isNullOrEmpty(incoming)) {
            target.setForceCoordinates(incoming);
        }
    }

    public interface PanePort {
        ScannerTargetContext scannerTargetContext();

        void setTargetSelected(TargetElement target);

        TargetElement targetSelected();

        void itPrintsElementDTO();

        void testingActions(TargetElement target, String actionType, String defaultValue);
    }
}
