package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.TargetElementHelper;
import java.util.List;
import java.util.Objects;

public final class ScannerRuntimeDataPorts {
    private ScannerRuntimeDataPorts() {}

    public static final class ElementTestListsPort implements ScannerElementTestLookupService.ListsPort {
        private final PerformLists performLists;

        public ElementTestListsPort(PerformLists performLists) {
            this.performLists = Objects.requireNonNull(performLists, "performLists");
        }

        @Override
        public boolean isInstructionListEmpty(String tableName) {
            return ScannerElementTestLookupService.BOT_JOB_INSTRUCTION_TABLE.equals(tableName)
                    ? performLists.getListInstruction().isEmpty()
                    : performLists.getListInstructionComp().isEmpty();
        }

        @Override
        public InstructionLoad getInstructionById(String tableName, int whereId, int instructionId) {
            return performLists.getInstructionById(tableName, whereId, instructionId);
        }
    }

    public static final class ElementTestDataPort implements ScannerElementTestLookupService.DataPort {
        private final PerformDataBase performDataBase;

        public ElementTestDataPort(PerformDataBase performDataBase) {
            this.performDataBase = Objects.requireNonNull(performDataBase, "performDataBase");
        }

        @Override
        public ErrorMessage loadInstructions(int whereId, String tableName) {
            return performDataBase.loadInstructions(whereId, -1, -1, tableName);
        }
    }

    public static final class InsertBlockListsPort implements ScannerInsertBlockSelectionService.ListsPort {
        private final PerformLists performLists;

        public InsertBlockListsPort(PerformLists performLists) {
            this.performLists = Objects.requireNonNull(performLists, "performLists");
        }

        @Override
        public boolean hasBlocks() {
            return !performLists.getListBlock().isEmpty();
        }
    }

    public static final class InstructionOrderDataPort implements ScannerInstructionOrderService.DataPort {
        private final PerformDataBase performDataBase;
        private final PerformLists performLists;

        public InstructionOrderDataPort(PerformDataBase performDataBase, PerformLists performLists) {
            this.performDataBase = Objects.requireNonNull(performDataBase, "performDataBase");
            this.performLists = Objects.requireNonNull(performLists, "performLists");
        }

        @Override
        public void loadInstructions(int botJobId, int blockId, int instructionId, String tableName) {
            performDataBase.loadInstructions(botJobId, blockId, instructionId, tableName);
        }

        @Override
        public List<InstructionLoad> instructions() {
            return performLists.getListInstruction();
        }
    }

    public static final class InsertActionsPort implements ScannerInsertPreparationService.ActionsPort {
        private final PerformActions performActions;

        public InsertActionsPort(PerformActions performActions) {
            this.performActions = Objects.requireNonNull(performActions, "performActions");
        }

        @Override
        public void defineSavedReferenced(TargetElement target) {
            performActions.defineSavedReferenced(target);
        }
    }

    public static final class InsertTargetExtractor implements ScannerInsertPreparationService.TargetExtractor {
        private final TargetElementHelper targetElementHelper;
        private final PerformActions performActions;

        public InsertTargetExtractor(TargetElementHelper targetElementHelper, PerformActions performActions) {
            this.targetElementHelper = Objects.requireNonNull(targetElementHelper, "targetElementHelper");
            this.performActions = Objects.requireNonNull(performActions, "performActions");
        }

        @Override
        public void initialize(ScannerTargetContext scannerTargetContext) {
            targetElementHelper.initialize(performActions, scannerTargetContext);
        }

        @Override
        public TargetElement extractPickClone(ElementDTO elementDTO) {
            return targetElementHelper.extractPickClone(elementDTO);
        }
    }

    public static final class ElementDetailsTargetExtractor
            implements ScannerElementDetailsSelectionService.TargetExtractor {
        private final TargetElementHelper targetElementHelper;
        private final PerformActions performActions;

        public ElementDetailsTargetExtractor(TargetElementHelper targetElementHelper, PerformActions performActions) {
            this.targetElementHelper = Objects.requireNonNull(targetElementHelper, "targetElementHelper");
            this.performActions = Objects.requireNonNull(performActions, "performActions");
        }

        @Override
        public void initialize(ScannerTargetContext scannerTargetContext) {
            targetElementHelper.initialize(performActions, scannerTargetContext);
        }

        @Override
        public TargetElement extractPickClone(ElementDTO elementDTO) {
            return targetElementHelper.extractPickClone(elementDTO);
        }
    }
}
