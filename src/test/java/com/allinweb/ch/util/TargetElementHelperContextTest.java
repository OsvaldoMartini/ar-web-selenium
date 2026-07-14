package com.allinweb.ch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.ScannerTargetContext;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.TargetElement;
import org.junit.jupiter.api.Test;

class TargetElementHelperContextTest {

    @Test
    void extractPickCloneUsesScannerTargetContextInsteadOfPane() {
        PerformActions performActions = mock(PerformActions.class);
        when(performActions.findWebElement(any(TargetElement.class))).thenReturn(null);
        when(performActions.defineSavedReferenced(any(TargetElement.class)))
                .thenAnswer(invocation -> invocation.<TargetElement>getArgument(0).getSavedReferences());

        RecordingContext context = new RecordingContext();
        TargetElementHelper helper = TargetElementHelper.getInstance();
        helper.initialize(performActions, context);

        ElementDTO dto = new ElementDTO();
        dto.setXPath("//button[@id='next']");
        dto.setCssSelector("#next");
        dto.setTagName("button");
        dto.setDefinedName("Next");
        dto.setSomeText("Next");

        TargetElement target = helper.extractPickClone(dto);

        assertEquals("//button[@id='next']", context.previousXPath);
        assertSame(target, context.targetElement);
        assertEquals(ARConstants.REGULAR_XPATH, target.getXPathWorkedFirst());
        assertTrue(target.getTagName().equalsIgnoreCase("button"));
    }

    private static final class RecordingContext implements ScannerTargetContext {
        private String previousXPath;
        private TargetElement targetElement;

        @Override
        public void rememberPreviousXPath(String xpath) {
            this.previousXPath = xpath;
        }

        @Override
        public void applyActionDefaults(TargetElement targetElement) {
            this.targetElement = targetElement;
        }
    }
}
