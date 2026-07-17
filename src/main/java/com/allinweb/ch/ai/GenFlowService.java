package com.allinweb.ch.ai;

import com.allinweb.ch.ai.GenFlowPlanParser.ValidatedBlock;
import com.allinweb.ch.ai.GenFlowPlanParser.ValidatedPlan;
import com.allinweb.ch.ai.GenFlowPlanParser.ValidatedStep;
import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.ScannerElementPanePublisher;
import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockMoveDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
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
 * <p>Runs on a background thread — must never touch presentation state or mutate the shared
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
    private final ScannerElementPanePublisher scannerElementPanePublisher = new ScannerElementPanePublisher();
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
        composedPrompt = enforcePageElementNavigationPolicy(composedPrompt);
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

        // Consent-first: if the page has a cookie-consent control (Accept All / Allow / Deny),
        // prepend a block that clicks it so every test run dismisses the banner before navigating.
        // These are typically shadow-DOM elements (e.g. data-testid="uc-accept-all-button") which
        // the Playwright click pierces. Prepended as the FIRST generated block.
        validated = prependConsentBlock(validated, inventory, cfg.maxBlocks());

        int instructionsCreated = persist(botJob, sourceBlock, validated);
        broadcast(botJob);

        return new GenFlowResult(
                validated.blocks().size(), instructionsCreated, validated.droppedSteps(), promptFile, responseFile);
    }

    // ── consent-first ─────────────────────────────────────────────────────────

    /**
     * If a cookie-consent accept/deny control exists in the inventory, prepend a block that
     * clicks it as the FIRST generated block, so every test run dismisses the consent banner
     * (often shadow-DOM, e.g. Usercentrics {@code uc-accept-all-button}) before navigating.
     * Preference order: accept-all/allow-all → accept/allow → deny/reject.
     */
    private ValidatedPlan prependConsentBlock(ValidatedPlan validated, List<InstructionLoad> inventory, int maxBlocks) {
        InstructionLoad consent = findConsentControl(inventory);
        if (consent == null) {
            return validated;
        }
        // Skip if the first block already clicks it (avoid a duplicate consent step).
        if (!validated.blocks().isEmpty()) {
            List<GenFlowPlanParser.ValidatedStep> firstSteps =
                    validated.blocks().get(0).steps();
            boolean alreadyFirst = !firstSteps.isEmpty()
                    && firstSteps.get(0).source() != null
                    && consent.getId() != null
                    && consent.getId().equals(firstSteps.get(0).source().getId());
            if (alreadyFirst) {
                return validated;
            }
        }

        List<GenFlowPlanParser.ValidatedStep> steps =
                List.of(new GenFlowPlanParser.ValidatedStep("CLICK", consent, null));
        GenFlowPlanParser.ValidatedBlock consentBlock = new GenFlowPlanParser.ValidatedBlock("Accept Cookies", steps);

        List<GenFlowPlanParser.ValidatedBlock> blocks = new ArrayList<>();
        blocks.add(consentBlock);
        blocks.addAll(validated.blocks());
        // Respect the cap (consent block counts toward it).
        if (blocks.size() > maxBlocks) {
            blocks = new ArrayList<>(blocks.subList(0, maxBlocks));
        }
        log.info("GEN FLOW — prepended consent-accept block for element '{}'", consent.getName());
        return new GenFlowPlanParser.ValidatedPlan(blocks, validated.droppedSteps());
    }

    private InstructionLoad findConsentControl(List<InstructionLoad> inventory) {
        InstructionLoad acceptAll = null;
        InstructionLoad accept = null;
        InstructionLoad deny = null;
        for (InstructionLoad instruction : inventory) {
            String hay = consentHaystack(instruction);
            if (hay.isEmpty()) continue;
            if (acceptAll == null
                    && (hay.contains("accept-all")
                            || hay.contains("accept_all")
                            || hay.contains("acceptall")
                            || hay.contains("accept all")
                            || hay.contains("allow-all")
                            || hay.contains("allowall")
                            || hay.contains("accept cookies")
                            || hay.contains("uc-accept-all"))) {
                acceptAll = instruction;
            } else if (accept == null && (hay.contains("accept") || hay.contains("allow"))) {
                accept = instruction;
            } else if (deny == null && (hay.contains("deny") || hay.contains("reject"))) {
                deny = instruction;
            }
        }
        if (acceptAll != null) return acceptAll;
        if (accept != null) return accept;
        return deny;
    }

    /** Lower-cased searchable text from name/clientNamed/cssSelector/references of an element. */
    private String consentHaystack(InstructionLoad instruction) {
        StringBuilder sb = new StringBuilder();
        appendLower(sb, instruction.getName());
        appendLower(sb, instruction.getClientNamed());
        appendLower(sb, instruction.getCssSelector());
        if (instruction.getReferenceLoadDTOList() != null) {
            for (com.allinweb.ch.model.ReferenceLoadDTO ref : instruction.getReferenceLoadDTOList()) {
                if (ref != null) appendLower(sb, ref.getValue());
            }
        }
        return sb.toString();
    }

    private void appendLower(StringBuilder sb, String value) {
        if (value != null) sb.append(' ').append(value.toLowerCase(java.util.Locale.ROOT));
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
                    ScannerWorkspaceSessions.BOT_JOB_TASKS, rows, botJobId, newBlockId, botJob.getHomeBankingId());
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

    private String enforcePageElementNavigationPolicy(String prompt) {
        return prompt
                + """

                NON-NEGOTIABLE NAVIGATION POLICY
                - Do not use browser history, browser Back, window.history, driver.navigate().back, or any BACK action.
                - If a test needs to return or move to another section, use only a CLICK step on a real element from ELEMENTS
                  such as a visible Back, Previous, Home, menu, breadcrumb, or navigation link/button.
                - If no page element exists for that navigation, omit the return step instead of inventing browser navigation.
                - Every generated navigation step must be a page element action with a copied elementName/xpath from ELEMENTS.
                """;
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

            scannerElementPanePublisher.publishUpdateBlocks(botJob.getHomeBankingId(), new BlockMoveDTO());

            if (!performLists.getListBotJob().isEmpty()) {
                List<InstructionLoad> viewData = performLists.buildJsonViewData(performLists.getListBotJob());
                com.allinweb.ch.socket.InstructionRealtimePublisher.getInstance()
                        .publishSnapshot(botJob.getHomeBankingId(), ScannerWorkspaceSessions.BOT_JOB_TASKS, viewData);
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
