package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import com.google.common.base.Strings;
import java.util.List;
import org.openqa.selenium.WebElement;

/** Builds instruction rows for scanner insert requests before the database write. */
public final class ScannerInsertPreparationService {

    public void prepare(
            ActionsPort actions,
            TargetExtractor targetExtractor,
            PanePort pane,
            List<InstructionLoad> instructionList,
            ElementDTO[] elements,
            int blockId,
            int firstOrder,
            boolean isMany) {
        instructionList.clear();
        targetExtractor.initialize(pane.scannerTargetContext());

        int nextOrder = firstOrder;
        if (elements == null) {
            return;
        }
        for (ElementDTO elementDTO : elements) {
            TargetElement target = targetExtractor.extractPickClone(elementDTO);
            WebElement elementFound = actions.findWebElement(target);
            if (target.getElement() == null && elementFound != null) {
                target.setElement(elementFound);
            }
            actions.defineSavedReferenced(target);

            if (!Strings.isNullOrEmpty(elementDTO.getForceCoordinates())) {
                target.setForceCoordinates(elementDTO.getForceCoordinates());
            }

            if (!isMany) {
                pane.setTargetSelected(target);
            }

            pane.prepareToInsertElementDTO(instructionList, blockId, nextOrder, target, true);
            nextOrder++;
        }
    }

    public interface PanePort {
        ScannerTargetContext scannerTargetContext();

        void setTargetSelected(TargetElement target);

        void prepareToInsertElementDTO(
                List<InstructionLoad> instructionList,
                int currentBlockId,
                int nextOrder,
                TargetElement target,
                boolean fromElementDto);
    }

    public interface ActionsPort {
        WebElement findWebElement(TargetElement target);

        void defineSavedReferenced(TargetElement target);
    }

    public interface TargetExtractor {
        void initialize(ScannerTargetContext scannerTargetContext);

        TargetElement extractPickClone(ElementDTO elementDTO);
    }
}
