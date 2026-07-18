package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerUpdatePreparationServiceTest {

    @Test
    void preparesRowsInOrderWithoutInsertOnlySideEffects() {
        ScannerUpdatePreparationService service = new ScannerUpdatePreparationService();
        RecordingActions actions = new RecordingActions();
        RecordingExtractor extractor = new RecordingExtractor();
        RecordingPane pane = new RecordingPane();
        List<InstructionLoad> rows = new ArrayList<>();
        rows.add(new InstructionLoad());

        service.prepare(actions, extractor, pane, rows, elements(2), 9, 4);

        assertEquals(2, rows.size());
        assertEquals(List.of(4, 5), pane.orders);
        assertEquals(0, actions.savedReferenceCount);
        assertEquals(0, pane.selectedTargetCount);
        assertEquals(1, extractor.initializeCount);
    }

    private static ElementDTO[] elements(int count) {
        ElementDTO[] elements = new ElementDTO[count];
        for (int i = 0; i < count; i++) {
            ElementDTO element = new ElementDTO();
            element.setId(i + 1);
            elements[i] = element;
        }
        return elements;
    }

    private static final class RecordingActions implements ScannerInsertPreparationService.ActionsPort {
        private int savedReferenceCount;

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
        private final List<Integer> orders = new ArrayList<>();
        private int selectedTargetCount;

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
            selectedTargetCount++;
        }

        @Override
        public void prepareToInsertElementDTO(
                List<InstructionLoad> instructionList,
                int currentBlockId,
                int nextOrder,
                TargetElement target,
                boolean fromElementDto) {
            orders.add(nextOrder);
            InstructionLoad row = new InstructionLoad();
            row.setName(target.getDefinedName());
            row.setBlockId(currentBlockId);
            instructionList.add(row);
        }
    }
}
