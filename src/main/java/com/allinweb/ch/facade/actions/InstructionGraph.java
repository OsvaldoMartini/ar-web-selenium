package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARExecution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lookup and traversal helpers over the instruction/block graph (cluster G):
 * parent/variable resolution, conditional IF/ELSEIF/ELSE/ENDIF jump maps, loop maps.
 * Pure functions over the DTOs; bodies moved verbatim from PerformActions.
 */
public final class InstructionGraph {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private InstructionGraph() {}

    /** Root instructions legitimately have no parent; execution uses zero as the no-parent sentinel. */
    public static int executionParentId(InstructionLoad instruction) {
        if (instruction == null || instruction.getParentId() == null) return 0;
        return instruction.getParentId();
    }

    public static String getXPathInstruction(InstructionLoad currentInstruction, BlockLoadDTO blockLoad) {
        try {
            return blockLoad.getInstructionLoad().stream()
                    .filter(f -> f.getId().equals(currentInstruction.getParentId()))
                    .findFirst()
                    .get()
                    .getXpath();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getInstructionParentField(InstructionLoad currentInstruction, BlockLoadDTO blockLoad) {
        try {
            return blockLoad.getInstructionLoad().stream()
                    .filter(f -> f.getId().equals(currentInstruction.getParentId()))
                    .findFirst()
                    .get()
                    .getName()
                    .trim();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getInstructionParentActions(InstructionLoad currentInstruction, BlockLoadDTO blockLoad) {
        try {
            return blockLoad.getInstructionLoad().stream()
                    .filter(f -> f.getId().equals(currentInstruction.getParentId()))
                    .findFirst()
                    .get()
                    .getActions();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getInstructionVariableField(
            InstructionLoad currentInstruction, List<VariableLoadDTO> variableLoad) {
        try {
            return variableLoad.stream()
                    .filter(f -> f.getId().equals(currentInstruction.getVariableId()))
                    .findFirst()
                    .map(v -> {
                        return v.getId() + "-" + String.valueOf(v.getType().charAt(0))
                                + v.getName().trim();
                    })
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getInstructionVariableFormat(
            InstructionLoad currentInstruction, List<VariableLoadDTO> variableLoad) {
        try {
            return variableLoad.stream()
                    .filter(f -> f.getId().equals(currentInstruction.getVariableId()))
                    .findFirst()
                    .map(v -> {
                        return v.getLocalFormat().trim();
                    })
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getInstructionVariableDelimiter(
            InstructionLoad currentInstruction, List<VariableLoadDTO> variableLoad) {
        try {
            return variableLoad.stream()
                    .filter(f -> f.getId().equals(currentInstruction.getVariableId()))
                    .findFirst()
                    .map(v -> {
                        return v.getDelimiter().trim();
                    })
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    // It Must be Greater than CurrentIndex
    // Ir Predicts if is going to have multiple ENSEIFs
    public static int searchMapConditional(
            Map<String, List<Integer>> mapConditional,
            int parentBlockCondition,
            ARExecution.ConditionStatus condition,
            int currentIndex,
            boolean showMessage) {

        // Construct the key pattern
        String keyPattern = parentBlockCondition + "-" + condition;

        // Iterate through the map entries
        for (Map.Entry<String, List<Integer>> entry : mapConditional.entrySet()) {
            String key = entry.getKey();
            List<Integer> indices = entry.getValue();

            // Check if the key matches the pattern
            if (key.startsWith(keyPattern)) {
                // Find the first index in the list that is greater than or equal to currentIndex
                for (int index : indices) {
                    if (index >= currentIndex) {
                        return index; // Return the matching index
                    }
                }
            }
        }

        if (showMessage) {
            // If no matching condition is found, show an error dialog
            PerformMessage.getInstance()
                    .showCustomModalDialog(
                            "ERROR ON CONDITIONAL BLOCK",
                            String.format(
                                    "Cannot find a matching condition for \"%s\" greater than the current index %d",
                                    condition, currentIndex),
                            " Please click OK to continue!",
                            null,
                            null,
                            true,
                            "OK",
                            null,
                            0);
        }

        return -1; // Return -1 if no valid index is found
    }

    public static Map<String, List<Integer>> getConditionIndexMapByParentId(BlockLoadDTO blockLoad) {
        try {
            // Create a map where key is "parentId-actions" and value is a list of indices
            return IntStream.range(0, blockLoad.getInstructionLoad().size())
                    .filter(index -> {
                        InstructionLoad instruction =
                                blockLoad.getInstructionLoad().get(index);
                        String actions = instruction.getActions();
                        return actions != null
                                && (actions.equals("IF")
                                        || actions.equals("ELSEIF")
                                        || actions.equals("ELSE")
                                        || actions.equals("ENDIF"));
                    })
                    .boxed() // Convert IntStream to Stream<Integer>
                    .collect(Collectors.toMap(
                            index -> {
                                InstructionLoad instruction =
                                        blockLoad.getInstructionLoad().get(index);
                                return instruction.getParentId() + "-"
                                        + instruction.getActions(); // Key: parentId-actions
                            },
                            index -> {
                                List<Integer> indices = new ArrayList<>();
                                indices.add(index);
                                return indices;
                            }, // Value: list of indices
                            (existing, replacement) -> {
                                existing.addAll(replacement);
                                return existing;
                            } // Handle duplicates by merging lists
                            ));
        } catch (Exception ex) {
            // Return an empty map in case of an exception
            return Collections.emptyMap();
        }
    }

    public static FieldData getBlockDetailsById(List<BlockLoadDTO> blocksLoaded, InstructionLoad currentInstruction) {
        for (BlockLoadDTO block : blocksLoaded) {
            if (block.getId() != null && block.getId().equals(currentInstruction.getParentBlockId())) {
                FieldData blockDetails = new FieldData(
                        currentInstruction.getId() + ":" + block.getId() + ":" + block.getBlockOrderNumber() + ":"
                                + block.getName().trim(),
                        currentInstruction.getOperation());
                return blockDetails;
            }
        }
        return null; // or throw an exception if the block is not found
    }

    public static int getBlockOrderNumber(List<BlockLoadDTO> blocksLoaded, Integer parentBlockId) {
        for (BlockLoadDTO block : blocksLoaded) {
            if (block.getId() != null && block.getId().equals(parentBlockId)) {
                return block.getBlockOrderNumber();
            }
        }
        return -1;
    }

    public static int gotoTargetIndex(FieldData gotoDetails) {
        if (gotoDetails == null || gotoDetails.getKey() == null) return -1;
        try {
            String[] parts = gotoDetails.getKey().split(":", 4);
            if (parts.length < 3) return -1;
            return Integer.parseInt(parts[2]) - 1;
        } catch (NumberFormatException invalidOrder) {
            return -1;
        }
    }

    public static FieldData getInstructionDetailsById(
            List<InstructionLoad> InstructionLoadS, InstructionLoad currentInstruction) {
        for (InstructionLoad instParent : InstructionLoadS) {
            if (instParent.getId() != null && instParent.getId().equals(currentInstruction.getParentId())) {
                FieldData blockDetails = new FieldData(
                        currentInstruction.getId() + ":" + instParent.getId() + ":"
                                + instParent.getName().trim(),
                        currentInstruction.getOperation());
                return blockDetails;
            }
        }
        return null; // or throw an exception if the block is not found
    }

    public static Map<String, Integer[]> getLoopAndRefreshLoops(List<InstructionLoad> InstructionLoadS) {
        // Step 2: Filter rows where actions = "REFRESH_LOOP" or "LOOP" and collect into the map
        Map<String, Integer[]> mapRefreshLoops = new HashMap<>();

        for (InstructionLoad instruction : InstructionLoadS) {
            // Filter by actions
            String actions = instruction.getActions();
            if ("REFRESH_LOOP".equalsIgnoreCase(actions) || "LOOP".equalsIgnoreCase(actions)) {
                // Convert id to String for the key
                String key = String.valueOf(instruction.getId());

                // Parse the operation into Integer[]
                String operation = instruction.getOperation();
                Integer[] operationValues;
                if (operation == null || operation.isEmpty()) {
                    operationValues = new Integer[] {}; // Handle null/empty operation
                } else {
                    String[] parts = operation.split(":"); // Split by ':'
                    operationValues = new Integer[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        operationValues[i] = Integer.parseInt(parts[i]); // Convert each part to Integer
                    }
                }

                // Add to the map
                mapRefreshLoops.put(key, operationValues);
            }
        }

        // Traverse and print keys and values
        for (Map.Entry<String, Integer[]> entry : mapRefreshLoops.entrySet()) {
            String key = entry.getKey(); // The key
            Integer[] values = entry.getValue(); // The value as an array

            // Convert the Integer[] to a readable string
            String valuesAsString = Arrays.stream(values)
                    .map(String::valueOf) // Convert each Integer to String
                    .collect(Collectors.joining(":")); // Join with ':'

            // Print the key and value
            logOperations.info("Key: " + key + ", Value: " + valuesAsString);
        }

        return mapRefreshLoops;
    }

    public static Set<Integer> getParentIdsForLoop(List<InstructionLoad> InstructionLoadS) {
        return InstructionLoadS.stream()
                .filter(instruction -> "REFRESH_LOOP".equalsIgnoreCase(instruction.getActions())
                        || "LOOP".equalsIgnoreCase(instruction.getActions()))
                .map(InstructionLoad::getParentId)
                .collect(Collectors.toSet());
    }

    public static Set<Integer> getAllOutputsPerBlock(List<InstructionLoad> InstructionLoadS) {
        return InstructionLoadS.stream()
                .filter(instruction -> instruction.getActions() != null
                        && instruction.getActions().trim().toUpperCase().startsWith("O:"))
                .map(InstructionLoad::getId)
                .collect(Collectors.toSet());
    }

    public static int checkActionToJump(
            String action,
            ARExecution.ConditionStatus progressCondition,
            Map<String, List<Integer>> mapConditional,
            int parentBlockCondition,
            int currentIndex) {
        if (action.equalsIgnoreCase(ARConstantsEngine.ELSEIF)) {
            // Goes to the ENDIF (ENDIF index + 1);
            return searchMapConditional(
                    mapConditional, parentBlockCondition, ARExecution.ConditionStatus.ENDIF, currentIndex, true);

        } else if (action.equalsIgnoreCase(ARConstantsEngine.ELSE)) {
            // Goes to the ENDIF (ENDIF index + 1);
            return searchMapConditional(
                    mapConditional, parentBlockCondition, ARExecution.ConditionStatus.ENDIF, currentIndex, true);
        }
        return 0;
    }
}
