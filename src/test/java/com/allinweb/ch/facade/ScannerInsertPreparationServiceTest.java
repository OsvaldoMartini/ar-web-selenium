package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;

class ScannerInsertPreparationServiceTest {

    @Test
    void preparesRowsInOrderAndKeepsManySelectionUnchanged() {
        ScannerInsertPreparationService service = new ScannerInsertPreparationService();
        RecordingActions actions = new RecordingActions();
        RecordingExtractor extractor = new RecordingExtractor();
        RecordingPane pane = new RecordingPane();
        List<InstructionLoad> rows = new ArrayList<>();
        rows.add(new InstructionLoad());

        service.prepare(actions, extractor, pane, rows, elements("F", ""), 9, 4, true);

        assertEquals(2, rows.size());
        assertEquals(List.of(4, 5), pane.orders);
        assertEquals("F", pane.preparedTargets.get(0).getForceCoordinates());
        assertEquals("", pane.preparedTargets.get(1).getForceCoordinates());
        assertEquals(2, actions.savedReferenceCount);
        assertEquals(1, extractor.initializeCount);
        assertTrue(pane.selectedTargets.isEmpty());
    }

    @Test
    void selectsTargetForSingleInsert() {
        ScannerInsertPreparationService service = new ScannerInsertPreparationService();
        RecordingPane pane = new RecordingPane();
        List<InstructionLoad> rows = new ArrayList<>();

        service.prepare(new RecordingActions(), new RecordingExtractor(), pane, rows, elements("S"), 7, 1, false);

        assertEquals(1, rows.size());
        assertEquals(1, pane.selectedTargets.size());
        assertSame(pane.preparedTargets.get(0), pane.selectedTargets.get(0));
    }

    private static ElementDTO[] elements(String... forceCoordinates) {
        ElementDTO[] elements = new ElementDTO[forceCoordinates.length];
        for (int i = 0; i < forceCoordinates.length; i++) {
            ElementDTO element = new ElementDTO();
            element.setId(i + 1);
            element.setForceCoordinates(forceCoordinates[i]);
            elements[i] = element;
        }
        return elements;
    }

    private static final class RecordingActions implements ScannerInsertPreparationService.ActionsPort {
        private int savedReferenceCount;

        @Override
        public WebElement findWebElement(TargetElement target) {
            return null;
        }

        @Override
        public void defineSavedReferenced(TargetElement target) {
            savedReferenceCount++;
        }
    }

    private static final class RecordingExtractor implements ScannerInsertPreparationService.TargetExtractor {
        private int initializeCount;

        @Override
        public void initialize(ScannerTargetContext scannerTargetContext) {
            initializeCount++;
        }

        @Override
        public TargetElement extractPickClone(ElementDTO elementDTO) {
            TargetElement target = new TargetElement();
            target.setDefinedName("element-" + elementDTO.getId());
            return target;
        }
    }

    private static final class RecordingPane implements ScannerInsertPreparationService.PanePort {
        private final List<TargetElement> selectedTargets = new ArrayList<>();
        private final List<TargetElement> preparedTargets = new ArrayList<>();
        private final List<Integer> orders = new ArrayList<>();

        @Override
        public ScannerTargetContext scannerTargetContext() {
            return new ScannerTargetContext() {
                @Override
                public void rememberPreviousXPath(String xpath) {}

                @Override
                public void applyActionDefaults(TargetElement targetElement) {}
            };
        }

        @Override
        public void setTargetSelected(TargetElement target) {
            selectedTargets.add(target);
        }

        @Override
        public void prepareToInsertElementDTO(
                List<InstructionLoad> instructionList,
                int currentBlockId,
                int nextOrder,
                TargetElement target,
                boolean fromElementDto) {
            orders.add(nextOrder);
            preparedTargets.add(target);
            InstructionLoad row = new InstructionLoad();
            row.setName(target.getDefinedName());
            row.setBlockId(currentBlockId);
            instructionList.add(row);
        }
    }
}
