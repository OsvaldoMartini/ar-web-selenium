package com.allinweb.ch.ai;

import com.allinweb.ch.model.InstructionLoad;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure parsing/validation of the AI's GEN FLOW response — no I/O, unit-testable.
 * The model is instructed to answer with bare JSON, but real answers arrive wrapped in
 * markdown fences or prose; {@link #parse} tolerates both. {@link #validate} then defends
 * against hallucinations by only accepting steps whose element exists in the source block's
 * inventory (matched by exact xpath, else by name).
 */
@Slf4j
public final class GenFlowPlanParser {

    public static final String DEFAULT_SYNTHETIC_VALUE = "test";

    private static final Gson GSON = new Gson();

    private GenFlowPlanParser() {}

    public record ValidatedPlan(List<ValidatedBlock> blocks, int droppedSteps) {}

    public record ValidatedBlock(String name, List<ValidatedStep> steps) {}

    public record ValidatedStep(String action, InstructionLoad source, String syntheticValue) {}

    /** Extracts and parses the JSON object from raw model output (strips fences/prose). */
    public static GenFlowPlan parse(String rawModelOutput) throws GenFlowException {
        if (Strings.isNullOrEmpty(rawModelOutput)) {
            throw new GenFlowException("GEN FLOW - Empty AI Response", "The AI returned no content.");
        }
        int first = rawModelOutput.indexOf('{');
        int last = rawModelOutput.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new GenFlowException("GEN FLOW - Invalid AI Response", "No JSON object found in the AI response.");
        }
        String json = rawModelOutput.substring(first, last + 1);
        GenFlowPlan plan;
        try {
            plan = GSON.fromJson(json, GenFlowPlan.class);
        } catch (JsonSyntaxException e) {
            // Long plans get truncated at the model's max_tokens; salvage every complete block.
            plan = repairTruncated(rawModelOutput.substring(first));
            if (plan == null) {
                throw new GenFlowException(
                        "GEN FLOW - Invalid AI Response", "The AI response is not valid JSON: " + e.getMessage(), e);
            }
            log.warn("GEN FLOW — AI response was truncated; salvaged {} complete block(s)", plan.blocks.size());
        }
        if (plan == null || plan.blocks == null || plan.blocks.isEmpty()) {
            throw new GenFlowException("GEN FLOW - Empty Plan", "The AI response contains no blocks to generate.");
        }
        return plan;
    }

    /**
     * Matches CLICK/INSERT steps to the source inventory (exact xpath first, then
     * case-insensitive name); drops unmatched steps and blocks that end up empty or
     * browser-history steps; caps the result at {@code maxBlocks}.
     */
    public static ValidatedPlan validate(GenFlowPlan plan, List<InstructionLoad> inventory, int maxBlocks) {
        List<ValidatedBlock> blocks = new ArrayList<>();
        int dropped = 0;

        for (GenFlowPlan.GenFlowBlock planBlock : plan.blocks) {
            if (blocks.size() >= maxBlocks) {
                break;
            }
            if (planBlock == null || planBlock.steps == null || planBlock.steps.isEmpty()) {
                continue;
            }
            List<ValidatedStep> steps = new ArrayList<>();
            boolean hasElementStep = false;

            for (GenFlowPlan.GenFlowStep step : planBlock.steps) {
                if (step == null || Strings.isNullOrEmpty(step.action)) {
                    dropped++;
                    continue;
                }
                String action = step.action.trim().toUpperCase();
                switch (action) {
                    case "CLICK", "INSERT" -> {
                        InstructionLoad source = matchElement(step, inventory);
                        if (source == null) {
                            dropped++;
                            log.warn(
                                    "GEN FLOW — dropping hallucinated step {} '{}' xpath='{}'",
                                    action,
                                    step.elementName,
                                    step.xpath);
                        } else {
                            String value = "INSERT".equals(action)
                                    ? (Strings.isNullOrEmpty(step.value) ? DEFAULT_SYNTHETIC_VALUE : step.value)
                                    : null;
                            steps.add(new ValidatedStep(action, source, value));
                            hasElementStep = true;
                        }
                    }
                    default -> {
                        dropped++;
                        if (isBrowserBackAction(action, step)) {
                            log.warn("GEN FLOW - dropping browser-history navigation step '{}'", step.action);
                        }
                    }
                }
            }

            if (!hasElementStep) {
                dropped += steps.size();
                continue;
            }
            String name =
                    Strings.isNullOrEmpty(planBlock.name) ? "Navigation " + (blocks.size() + 1) : planBlock.name.trim();
            if (name.length() > 40) {
                name = name.substring(0, 40);
            }
            blocks.add(new ValidatedBlock(name, steps));
        }

        return new ValidatedPlan(blocks, dropped);
    }

    private static boolean isBrowserBackAction(String action, GenFlowPlan.GenFlowStep step) {
        if ("BACK".equals(action) || "GO_BACK".equals(action) || "BROWSER_BACK".equals(action)) {
            return true;
        }
        String name = step == null ? null : step.elementName;
        return name != null && name.toLowerCase().contains("browser back");
    }

    /**
     * Salvages a truncated response: string-aware scan tracking brace/bracket depth, cutting
     * at the last position where a complete top-level block object closed (depth back to
     * "inside the blocks array"), then appending {@code ]}}. Returns null if nothing usable.
     */
    private static GenFlowPlan repairTruncated(String jsonFromFirstBrace) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int lastCompleteBlockEnd = -1;

        for (int i = 0; i < jsonFromFirstBrace.length(); i++) {
            char c = jsonFromFirstBrace.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = inString;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                // depth 2 = back inside the "blocks" array after a block object closed.
                if (c == '}' && depth == 2) {
                    lastCompleteBlockEnd = i;
                }
            }
        }

        if (lastCompleteBlockEnd < 0) {
            return null;
        }
        String repaired = jsonFromFirstBrace.substring(0, lastCompleteBlockEnd + 1) + "]}";
        try {
            GenFlowPlan plan = GSON.fromJson(repaired, GenFlowPlan.class);
            return (plan == null || plan.blocks == null || plan.blocks.isEmpty()) ? null : plan;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static InstructionLoad matchElement(GenFlowPlan.GenFlowStep step, List<InstructionLoad> inventory) {
        if (!Strings.isNullOrEmpty(step.xpath)) {
            for (InstructionLoad candidate : inventory) {
                if (step.xpath.equals(candidate.getXpath())) {
                    return candidate;
                }
            }
        }
        if (!Strings.isNullOrEmpty(step.elementName)) {
            String wanted = step.elementName.trim();
            for (InstructionLoad candidate : inventory) {
                if (candidate.getName() != null
                        && wanted.equalsIgnoreCase(candidate.getName().trim())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
