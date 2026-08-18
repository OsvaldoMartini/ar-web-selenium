package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerElementDetailsSelectionServiceTest {

    @Test
    void initializesExtractorSelectsTargetAndPrintsDetails() {
        ScannerElementDetailsSelectionService service = new ScannerElementDetailsSelectionService();
        RecordingPane pane = new RecordingPane();
        RecordingExtractor extractor = new RecordingExtractor(pane.calls);
        ElementDTO element = new ElementDTO();

        service.select(extractor, pane, element);

        assertEquals(List.of("context", "init", "extract", "select", "print"), pane.calls);
        assertSame(element, extractor.element);
        assertSame(extractor.target, pane.selected);
    }

    private static final class RecordingExtractor implements ScannerElementDetailsSelectionService.TargetExtractor {
        private final List<String> calls;
        private final TargetElement target = new TargetElement();
        private ElementDTO element;

        private RecordingExtractor(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void initialize(ScannerTargetContext scannerTargetContext) {
            calls.add("init");
        }

        @Override
        public TargetElement extractPickClone(ElementDTO elementDTO) {
            calls.add("extract");
            element = elementDTO;
            return target;
        }
    }

    private static final class RecordingPane implements ScannerElementTestActionService.PanePort {
        private final List<String> calls = new ArrayList<>();
        private final RecordingContext context = new RecordingContext(calls);
        private TargetElement selected;

        @Override
        public ScannerTargetContext scannerTargetContext() {
            calls.add("context");
            return context;
        }

        @Override
        public void setTargetSelected(TargetElement target) {
            calls.add("select");
            selected = target;
        }

        @Override
        public TargetElement targetSelected() {
            return selected;
        }

        @Override
        public void itPrintsElementDTO() {
            calls.add("print");
        }

        @Override
        public void testingActions(TargetElement target, String actionType, String defaultValue) {}
    }

    private record RecordingContext(List<String> calls) implements ScannerTargetContext {
        @Override
        public void rememberPreviousXPath(String xpath) {}

        @Override
        public void applyActionDefaults(TargetElement targetElement) {}
    }
}
