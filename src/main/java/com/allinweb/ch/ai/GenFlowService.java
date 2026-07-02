package com.allinweb.ch.ai;

import com.allinweb.ch.ai.GenFlowPlanParser.ValidatedBlock;
import com.allinweb.ch.ai.GenFlowPlanParser.ValidatedPlan;
import com.allinweb.ch.ai.GenFlowPlanParser.ValidatedStep;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockMoveDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * GEN FLOW orchestrator: reads the GEN_FLOW prompt from the database, sends the selected
 * block's element inventory to the configured AI, validates the returned plan against the
 * inventory, and persists the generated surface-navigation blocks right after the source
 * block. The composed prompt and the raw AI response are always written under
 * {@code <PATH_DB>/ai/} so the prompt can also be used manually in Claude Code.
 *
 * <p>Runs on a background thread — must never touch JavaFX or mutate the shared
 * {@code performLists.getListInstruction()} (uses the read-only instruction loader).
 */
@Slf4j
public final class GenFlowService {

    private static final int MAX_INVENTORY_ELEMENTS = 120;
    private static final String SYSTEM_PROMPT =
            "You are an expert web test automation assistant. Respond only with valid JSON.";

    private final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private final PerformLists performLists = PerformLists.getInstance();
    private final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private final ARPropertyManager propertyManager = ARPropertyManager.getInstance();
    private final Gson gson = new Gson();

    public record GenFlowResult(
            int blocksCreated, int instructionsCreated, int droppedSteps, Path promptFile, Path responseFile) {}

    public GenFlowResult generate(BotJobLoadDTO botJob, BlockLoadDTO sourceBlock) throws GenFlowException {
        AiChatClient.AiConfig cfg = AiChatClient.fromProperties(propertyManager);

        String template = performDataBase.loadAiPrompt("GEN_FLOW");
        if (Strings.isNullOrEmpty(template)) {
            throw new GenFlowException(
                    "GEN FLOW - Prompt Missing",
                    "No active 'GEN_FLOW' prompt found in the ai_prompt table. "
                            + "Restart the application so migration 2026-07-02__ai_prompt can seed it.");
        }

        List<InstructionLoad> inventory =
                performDataBase.loadBlockInstructionsReadOnly(botJob.getId(), sourceBlock.getId());
        if (inventory.isEmpty()) {
            throw new GenFlowException(
                    "GEN FLOW - Empty Block",
                    "Block \"" + sourceBlock.getName() + "\" has no instructions to build a navigation test from.");
        }
        boolean truncated = inventory.size() > MAX_INVENTORY_ELEMENTS;
        List<InstructionLoad> promptInventory = truncated ? inventory.subList(0, MAX_INVENTORY_ELEMENTS) : inventory;

        String composedPrompt = template.replace("{{BLOCK_NAME}}", nullSafe(sourceBlock.getName()))
                .replace("{{ELEMENTS_JSON}}", buildElementsJson(promptInventory))
                .replace("{{MAX_BLOCKS}}", String.valueOf(cfg.maxBlocks()))
                .replace("{{JSON_SCHEMA}}", GenFlowPlan.SCHEMA_JSON);
        if (truncated) {
            composedPrompt = composedPrompt
                    + "\n\nNOTE: the element list was truncated to " + MAX_INVENTORY_ELEMENTS + " of "
                    + inventory.size() + " elements.";
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path promptFile = writeAiFile(
                "genflow-prompt-" + botJob.getId() + "-" + sourceBlock.getId() + "-" + stamp + ".txt", composedPrompt);

        String rawResponse = new AiChatClient().chat(cfg, SYSTEM_PROMPT, composedPrompt);
        Path responseFile = writeAiFile(
                "genflow-response-" + botJob.getId() + "-" + sourceBlock.getId() + "-" + stamp + ".txt", rawResponse);

        GenFlowPlan plan = GenFlowPlanParser.parse(rawResponse);
        ValidatedPlan validated = GenFlowPlanParser.validate(plan, inventory, cfg.maxBlocks());
        if (validated.blocks().isEmpty()) {
            throw new GenFlowException(
                    "GEN FLOW - Nothing Usable",
                    "The AI plan contained no steps matching the block's elements ("
                            + validated.droppedSteps() + " hallucinated steps dropped). "
                            + "Raw response: " + responseFile);
        }

        int instructionsCreated = persist(botJob, sourceBlock, validated);
        broadcast(botJob);

        return new GenFlowResult(
                validated.blocks().size(), instructionsCreated, validated.droppedSteps(), promptFile, responseFile);
    }

    // ── persistence ─────────────────────────────────────────────────────────

    private int persist(BotJobLoadDTO botJob, BlockLoadDTO sourceBlock, ValidatedPlan validated)
            throws GenFlowException {
        int botJobId = botJob.getId();
        int newBlockCount = validated.blocks().size();

        // Fresh orders right before the shift to minimize stale-order races.
        performDataBase.loadBlocks(botJobId, "", "block");
        Integer sourceOrder = performLists.getListBlock().stream()
                .filter(b -> sourceBlock.getId().equals(b.getId()))
                .map(BlockLoadDTO::getBlockOrderNumber)
                .findFirst()
                .orElse(sourceBlock.getBlockOrderNumber());
        int targetStart = (sourceOrder == null ? 0 : sourceOrder) + 1;

        // One bulk shift +N for everything at or after the insertion point.
        List<BlockLoadDTO> toRenumber = new ArrayList<>();
        for (BlockLoadDTO b : performLists.getListBlock()) {
            if (b.getBotJobId() == null || !b.getBotJobId().equals(botJobId)) continue;
            if (b.getBlockOrderNumber() == null || b.getBlockOrderNumber() < targetStart) continue;
            BlockLoadDTO shifted = new BlockLoadDTO();
            shifted.setId(b.getId());
            shifted.setBlockOrderNumber(b.getBlockOrderNumber() + newBlockCount);
            // updateSwiftBlockOrderNumber reads botJobId as the WHERE predicate.
            shifted.setBotJobId(botJobId);
            shifted.setHomeBankingId(b.getHomeBankingId());
            toRenumber.add(shifted);
        }
        if (!toRenumber.isEmpty()) {
            ErrorMessage err = performDataBase.updateSwiftBlockOrderNumber("block", botJobId, toRenumber);
            if (err != null) {
                throw new GenFlowException("GEN FLOW - Block Reorder Failed", err.getErrorMessage());
            }
            performLists.updateMemorySwiftBlockOrder("block", botJobId, toRenumber);
        }

        int instructionsCreated = 0;
        for (int i = 0; i < newBlockCount; i++) {
            ValidatedBlock planBlock = validated.blocks().get(i);

            BlockDetailsDTO newBlock = new BlockDetailsDTO();
            newBlock.setBlockName(String.format("GF-%02d %s", i + 1, planBlock.name()));
            newBlock.setBlockDescription("Generated by GEN FLOW from block \"" + sourceBlock.getName() + "\"");
            newBlock.setBlockOrderNumber(targetStart + i);
            newBlock.setBotJobId(botJobId);
            newBlock.setActive(true);
            newBlock.setForceOrder(true);
            ErrorMessage err = performDataBase.insertNewBlock("block", botJobId, newBlock);
            if (err != null) {
                throw new GenFlowException(
                        "GEN FLOW - Block Insert Failed",
                        "Block \"" + planBlock.name() + "\": " + err.getErrorMessage());
            }
            int newBlockId = performDataBase.getIdsBlockAfter().get(0);

            List<InstructionLoad> rows = buildInstructions(planBlock, newBlockId, botJob);
            ErrorMessage insErr = performDataBase.insertInstructionsBatch(
                    "botJobTasks", rows, botJobId, newBlockId, botJob.getHomeBankingId());
            if (insErr != null) {
                throw new GenFlowException(
                        "GEN FLOW - Instruction Insert Failed",
                        "Block \"" + planBlock.name() + "\": " + insErr.getErrorMessage());
            }
            List<Integer> newIds = performDataBase.getIdsInstrucAfter();
            for (int r = 0; r < rows.size() && r < newIds.size(); r++) {
                rows.get(r).setId(newIds.get(r));
            }
            performDataBase.insertReferencesBatch(rows);
            instructionsCreated += rows.size();
        }
        return instructionsCreated;
    }

    private List<InstructionLoad> buildInstructions(ValidatedBlock planBlock, int blockId, BotJobLoadDTO botJob) {
        List<InstructionLoad> rows = new ArrayList<>();
        int order = 1;
        for (ValidatedStep step : planBlock.steps()) {
            InstructionLoad row = new InstructionLoad();
            row.setBlockId(blockId);
            row.setBotJobId(botJob.getId());
            row.setInstructionOrderNumber(order++);
            row.setInstructionActive(true);
            row.setOptional(false);
            row.setCodified(false);
            row.setExportToABR(true);
            row.setActionCustomMaxWaitSec(30);
            row.setForceCoordinates("");
            row.setDescription("GEN FLOW generated");

            switch (step.action()) {
                case "BACK" -> {
                    row.setActions(ARConstantsEngine.BACK);
                    row.setName("Go Back");
                    row.setOnHoldSeconds(1);
                    row.setReferenceLoadDTOList(new ArrayList<>());
                }
                case "INSERT" -> {
                    InstructionLoad src = step.source();
                    copyLocators(src, row, botJob.getId());
                    String actions =
                            src.getActions() != null && src.getActions().startsWith("I:")
                                    ? src.getActions()
                                    : ARConstantsEngine.INSERT
                                            + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                                            + nullSafe(src.getName());
                    row.setActions(actions);
                    row.setDefaultValue(step.syntheticValue());
                }
                default -> { // CLICK
                    InstructionLoad src = step.source();
                    copyLocators(src, row, botJob.getId());
                    row.setActions(ARConstantsEngine.CLICK);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private void copyLocators(InstructionLoad src, InstructionLoad row, int botJobId) {
        row.setName(src.getName());
        row.setClientNamed(src.getClientNamed());
        row.setXpath(src.getXpath());
        row.setCssSelector(src.getCssSelector());
        row.setIFrameXPath(src.getIFrameXPath());
        row.setTagName(src.getTagName());
        row.setShadowHost(src.getShadowHost());
        row.setShadowRoot(src.getShadowRoot());
        row.setCoordinates(src.getCoordinates());

        List<ReferenceLoadDTO> references = new ArrayList<>();
        if (src.getReferenceLoadDTOList() != null) {
            for (ReferenceLoadDTO ref : src.getReferenceLoadDTOList()) {
                if (ref == null) continue;
                ReferenceLoadDTO copy = new ReferenceLoadDTO();
                copy.setReferenceType(ref.getReferenceType());
                copy.setValue(ref.getValue());
                copy.setBotJobId(botJobId);
                references.add(copy);
            }
        }
        row.setReferenceLoadDTOList(references);
    }

    // ── refresh + broadcast ─────────────────────────────────────────────────

    private void broadcast(BotJobLoadDTO botJob) {
        try {
            performDataBase.loadBlocks(botJob.getId(), "", "block");
            performDBEngine.loadCompleteJobs(botJob.getId());

            String blockSignal = gson.toJson(new BlockMoveDTO());
            webSocketSessionManager.sendMessageJson(
                    botJob.getHomeBankingId(), "bot-job-scene", blockSignal, "UPDATE_BLOCKS");
            webSocketSessionManager.sendMessageJson(
                    botJob.getHomeBankingId(), "scanner-element-pane", blockSignal, "UPDATE_BLOCKS");

            if (!performLists.getListBotJob().isEmpty()) {
                List<InstructionLoad> viewData = performLists.buildJsonViewData(performLists.getListBotJob());
                webSocketSessionManager.sendMessageJson(
                        botJob.getHomeBankingId(), "botJobTasks", gson.toJson(viewData), "updateInstructions");
            }
        } catch (Exception e) {
            log.warn("GEN FLOW — post-insert broadcast failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String buildElementsJson(List<InstructionLoad> inventory) {
        JsonArray elements = new JsonArray();
        for (InstructionLoad instruction : inventory) {
            JsonObject el = new JsonObject();
            el.addProperty("name", nullSafe(instruction.getName()));
            el.addProperty("clientNamed", nullSafe(instruction.getClientNamed()));
            el.addProperty("tagName", nullSafe(instruction.getTagName()));
            el.addProperty("actions", nullSafe(instruction.getActions()));
            el.addProperty("xpath", nullSafe(instruction.getXpath()));
            el.addProperty("cssSelector", nullSafe(instruction.getCssSelector()));
            elements.add(el);
        }
        return gson.toJson(elements);
    }

    private Path writeAiFile(String fileName, String content) throws GenFlowException {
        String pathDb = propertyManager.getProperty(ARPropertyEnum.PATH_DB);
        Path dir = Paths.get(Strings.isNullOrEmpty(pathDb) ? "." : pathDb, "ai");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            log.info("GEN FLOW — wrote {}", file);
            return file;
        } catch (IOException e) {
            throw new GenFlowException(
                    "GEN FLOW - File Write Failed",
                    "Could not write " + dir.resolve(fileName) + ": " + e.getMessage(),
                    e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
