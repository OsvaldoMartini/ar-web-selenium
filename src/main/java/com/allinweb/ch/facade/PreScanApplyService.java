package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.TargetElementHelper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists elements applied from the PRE SCAN React dashboard when the heavy AR Web Factory
 * pane is closed.
 *
 * <p>The legacy Apply path forwards {@code NEW_ELEMENT_DTO} / {@code SEND_ALL_ELEMENTS_DTO} to the
 * scanner element WebSocket session, consumed by {@code ScannerRuntime} ->
 * {@code performInsertManyDTO} — which silently no-ops when AR Web Factory isn't open. This
 * service is the pane-free equivalent used as the fallback: it mirrors
 * {@code ScannerRuntimeBackend.prepareToInsertElementDTO} (manyElements=true) field-for-field but
 * builds each {@link TargetElement} purely from the scanned {@link ElementDTO} metadata
 * ({@code TargetElementHelper.extractPickClone(dto, true)}) — no live-browser lookup, which is
 * correct here because the pre-scan page lives in its own isolated browser.
 *
 * <p>Requires {@code blockId > 0}: the dashboard's Apply always sends the selected target block,
 * so the pane's create-block-modal fallback is intentionally not replicated.
 */
@Slf4j
public final class PreScanApplyService {

    private static volatile PreScanApplyService instance;

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();

    private PreScanApplyService() {}

    public static PreScanApplyService getInstance() {
        if (instance == null) {
            synchronized (PreScanApplyService.class) {
                if (instance == null) {
                    instance = new PreScanApplyService();
                }
            }
        }
        return instance;
    }

    private static ErrorMessage failure(String message) {
        return new ErrorMessage("PRE SCAN Apply", "Operation Failed", message);
    }

    /**
     * Inserts the applied elements as instructions at the end of the chosen block, then pushes
     * the refreshed instruction list to the task grid — the same outcome as the
     * pane path. Returns null on success.
     */
    public ErrorMessage applyElements(SplitDTO splitDTO) {
        return applyElementsDetailed(splitDTO).error();
    }

    public synchronized ApplyResult applyElementsDetailed(SplitDTO splitDTO) {
        if (splitDTO == null
                || splitDTO.getBotJobId() == null
                || splitDTO.getBotJobId() <= 0
                || splitDTO.getHomeBankingId() == null) {
            return ApplyResult.failed(failure("Missing bot job / home banking id"));
        }
        if (splitDTO.getBlockId() == null || splitDTO.getBlockId() <= 0) {
            return ApplyResult.failed(failure("Select a target block before applying"));
        }
        ElementDTO[] elements = splitDTO.getElementDetails();
        if (elements == null || elements.length == 0) {
            return ApplyResult.failed(failure("No elements to apply"));
        }

        int botJobId = splitDTO.getBotJobId();
        int blockId = splitDTO.getBlockId();
        int homeBankingId = splitDTO.getHomeBankingId();

        ErrorMessage loadError = performDataBase.loadInstructions(botJobId, blockId, -1, "instruction");
        if (loadError != null) {
            return ApplyResult.failed(loadError);
        }
        int nextOrder = performLists.getListInstruction().size() + 1;

        List<InstructionLoad> instructionList = new ArrayList<>();
        for (int elementIndex = 0; elementIndex < elements.length; elementIndex++) {
            ElementDTO elementDTO = elements[elementIndex];
            if (elementDTO == null) {
                return ApplyResult.failed(
                        failure("Selected element " + (elementIndex + 1) + " is empty; nothing was inserted"));
            }
            InstructionLoad instruction = buildInstruction(elementDTO, botJobId, blockId, nextOrder);
            if (instruction == null) {
                return ApplyResult.failed(failure(
                        "Selected element " + (elementIndex + 1)
                                + " could not be converted; nothing was inserted"));
            }
            instructionList.add(instruction);
            nextOrder++;
        }
        if (instructionList.isEmpty()) {
            return ApplyResult.failed(failure("No element could be converted to an instruction"));
        }

        PerformDataBase.AtomicInstructionInsertResult persistence =
                performDataBase.insertInstructionsAndReferencesAtomic(instructionList, botJobId, blockId);
        if (persistence.error() != null) return ApplyResult.failed(persistence.error());
        if (persistence.instructionIds().size() != instructionList.size()) {
            return ApplyResult.failed(failure("Inserted instruction count mismatch"));
        }

        boolean synchronizedSnapshot = refreshBotJobTasks(botJobId, homeBankingId);
        log.info(
                "PRE SCAN Apply - inserted {} instruction(s) into block {} of bot job {}",
                instructionList.size(),
                blockId,
                botJobId);
        return new ApplyResult(null, instructionList.size(), synchronizedSnapshot);
    }

    /**
     * Maps a scanned element to a transient {@link InstructionLoad} for pane-free row tests
     * (Test Click / Test Input on the pre-scan browser). Nothing is persisted — the ids are
     * placeholders; only the locator/coordinates/flags mapping matters to the executor.
     */
    public InstructionLoad buildTestInstruction(ElementDTO elementDTO) {
        return buildInstruction(elementDTO, 0, 0, 1);
    }

    /** Pane-free mirror of {@code prepareToInsertElementDTO} with {@code manyElements=true}. */
    private InstructionLoad buildInstruction(ElementDTO elementDTO, int botJobId, int blockId, int orderNumber) {
        TargetElement target = TargetElementHelper.getInstance().extractPickClone(elementDTO, true);
        if (target == null) {
            log.warn("PRE SCAN Apply - could not map element '{}', skipped", elementDTO.getXPath());
            return null;
        }

        // Per-element S/N/T/E/F badge flags from the dashboard win over the composed defaults.
        if (!Strings.isNullOrEmpty(elementDTO.getForceCoordinates())) {
            target.setForceCoordinates(elementDTO.getForceCoordinates());
        }
        // The pane derives this from its click checkbox; here the scanner's decided
        // category is the source of truth.
        target.setClickElement("button"
                .equalsIgnoreCase(
                        Objects.toString(elementDTO.getTypeElement(), "").toLowerCase(Locale.ROOT)));

        if (target.getXPath() == null) {
            target.setXPath(target.getSavedReferences().get("currentXPath"));
        }
        if (target.getCoordinates() == null) {
            target.setCoordinates(target.getSavedReferences().get("coordinates"));
        }

        InstructionLoad instruction = com.allinweb.ch.facade.actions.ElementDtoMapper.buildNewInstruction(
                target.getTagType(), target.getTagName(), false, orderNumber, target);

        instruction.setForceCoordinates(Strings.nullToEmpty(target.getForceCoordinates()));
        instruction.setCoordinates(target.getCoordinates());
        instruction.setIFrameXPath(target.getIFrameXPath());
        instruction.setShadowHost(target.getShadowHost());
        instruction.setShadowRoot(target.getShadowRoot());
        instruction.setCssSelector(target.getCssSelector());
        instruction.setBlockId(blockId);
        instruction.setBotJobId(botJobId);
        instruction.setName(target.getDefinedName());
        instruction.setClientNamed(target.getClientNamed());

        if (instruction.getName() == null && target.getNameLabel() == null) {
            instruction.setName(target.getSomeText() != null ? target.getSomeText() : target.getTagName());
        } else if (instruction.getName() == null) {
            instruction.setName(target.getNameLabel());
        }

        String actions = instruction.getActions();
        if (actions != null && actions.startsWith("I:")) {
            instruction.setActions("I:" + target.getDefinedName());
        }

        List<ReferenceLoadDTO> referenceList = new ArrayList<>();
        for (Map.Entry<String, String> entry : target.getSavedReferences().entrySet()) {
            ReferenceLoadDTO reference = new ReferenceLoadDTO();
            reference.setReferenceType(entry.getKey());
            reference.setValue(entry.getValue());
            reference.setBotJobId(botJobId);
            referenceList.add(reference);
        }
        instruction.setReferenceLoadDTOList(referenceList);
        return instruction;
    }

    /** Same refresh {@code ScannerRuntime.updateBotJobTasks} performs after a pane insert. */
    private boolean refreshBotJobTasks(int botJobId, int homeBankingId) {
        ErrorMessage errorMessage = performDBEngine.loadCompleteJobs(botJobId);
        if (errorMessage != null) {
            log.warn("PRE SCAN Apply - refresh failed: {}", errorMessage.getErrorMessage());
            return false;
        }
        String jsonData = "[]";
        if (!performLists.getListBotJob().isEmpty()) {
            List<InstructionLoad> view = performLists.buildJsonViewData(performLists.getListBotJob());
            jsonData = gson.toJson(view);
        }
        com.allinweb.ch.socket.InstructionRealtimePublisher.getInstance()
                .publishSerializedSnapshot(homeBankingId, ScannerWorkspaceSessions.BOT_JOB_TASKS, jsonData);
        return true;
    }

    public record ApplyResult(ErrorMessage error, int insertedCount, boolean synchronizedSnapshot) {
        private static ApplyResult failed(ErrorMessage error) {
            return new ApplyResult(error, 0, false);
        }
    }
}
