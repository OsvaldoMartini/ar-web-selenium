package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the structural envelope of a React-planned DELETE_INSTRUCTION v2 request.
 *
 * <p>This validator deliberately does not infer conditional, loop, parent, variable, or
 * positional delete groups. React supplies the exact IDs that the user confirmed; Java only
 * proves that the submitted IDs are a unique, owner-scoped set containing the selected row.
 */
public final class InstructionDeleteContractValidator {
    public static final int CONTRACT_VERSION = 2;

    public Validation validate(
            SplitDTO request,
            List<InstructionLoad> ownerRows,
            int ownerId,
            boolean componentWorkspace) {
        if (request == null || request.getDeleteContractVersion() == null
                || request.getDeleteContractVersion() != CONTRACT_VERSION) {
            return failure(
                    "Unsupported delete contract",
                    "Refresh the workspace and submit DELETE_INSTRUCTION contract version 2.");
        }
        if (request.getInstructionId() == null || request.getInstructionId() <= 0) {
            return failure(
                    "Selected instruction is required",
                    "The delete request does not identify the selected instruction.");
        }
        List<Integer> submitted = request.getDeleteInstructionIds();
        if (submitted == null || submitted.isEmpty()) {
            return failure(
                    "Delete instruction IDs are required",
                    "The confirmed delete list is empty.");
        }

        List<Integer> orderedIds = new ArrayList<>(submitted.size());
        Set<Integer> uniqueIds = new HashSet<>();
        for (Integer id : submitted) {
            if (id == null || id <= 0) {
                return failure(
                        "Invalid delete instruction ID",
                        "Every confirmed instruction ID must be a positive integer.");
            }
            if (!uniqueIds.add(id)) {
                return failure(
                        "Duplicate delete instruction ID",
                        "The confirmed delete list contains instruction #" + id + " more than once.");
            }
            orderedIds.add(id);
        }
        if (!uniqueIds.contains(request.getInstructionId())) {
            return failure(
                    "Selected instruction is missing",
                    "The confirmed delete list must contain instruction #"
                            + request.getInstructionId() + ".");
        }

        Map<Integer, InstructionLoad> rowsById = new HashMap<>();
        if (ownerRows != null) {
            for (InstructionLoad row : ownerRows) {
                if (row == null || row.getId() == null) continue;
                if (rowsById.putIfAbsent(row.getId(), row) != null) {
                    return failure(
                            "Duplicate stored instruction ID",
                            "The current owner graph contains instruction #"
                                    + row.getId() + " more than once.");
                }
            }
        }
        for (Integer id : orderedIds) {
            InstructionLoad stored = rowsById.get(id);
            if (stored == null || !belongsToOwner(stored, ownerId, componentWorkspace)) {
                return failure(
                        "Instruction is outside the requested owner",
                        "Instruction #" + id
                                + " does not belong to the current workspace. Refresh and retry.");
            }
        }

        List<UpdatedRow> submittedRepairs = request.getDeleteParentRepairs() == null
                ? List.of()
                : request.getDeleteParentRepairs();
        List<UpdatedRow> validatedRepairs = new ArrayList<>(submittedRepairs.size());
        Set<Integer> repairIds = new HashSet<>();
        for (UpdatedRow repair : submittedRepairs) {
            Integer repairId = repair == null ? null : repair.getInstructionId();
            if (repairId == null || repairId <= 0 || !repairIds.add(repairId)) {
                return failure(
                        "Invalid parent repair",
                        "Every parent repair must identify one unique, positive instruction ID.");
            }
            if (uniqueIds.contains(repairId)) {
                return failure(
                        "Invalid parent repair",
                        "Deleted instruction #" + repairId + " cannot also be repaired.");
            }
            InstructionLoad stored = rowsById.get(repairId);
            if (stored == null || !belongsToOwner(stored, ownerId, componentWorkspace)) {
                return failure(
                        "Parent repair is outside the requested owner",
                        "Instruction #" + repairId
                                + " does not belong to the current workspace.");
            }
            if (stored.getParentId() == null || !uniqueIds.contains(stored.getParentId())) {
                return failure(
                        "Parent repair does not match the stored graph",
                        "Instruction #" + repairId
                                + " is not currently attached to a deleted instruction.");
            }
            if (repair.getParentId() != null) {
                return failure(
                        "Unsupported parent repair",
                        "DELETE_INSTRUCTION v2 currently supports clearing parentId only.");
            }
            UpdatedRow validated = new UpdatedRow();
            validated.setInstructionId(repairId);
            validated.setParentId(null);
            validatedRepairs.add(validated);
        }
        return new Validation(
                List.copyOf(orderedIds),
                List.copyOf(validatedRepairs),
                null);
    }

    private boolean belongsToOwner(
            InstructionLoad row, int ownerId, boolean componentWorkspace) {
        Integer storedOwner = componentWorkspace
                ? row.getHomeBankingId()
                : row.getBotJobId();
        return storedOwner != null && storedOwner == ownerId;
    }

    private Validation failure(String message, String details) {
        return new Validation(
                List.of(),
                List.of(),
                new ErrorMessage("Delete Instruction Refused", message, details));
    }

    public record Validation(
            List<Integer> instructionIds,
            List<UpdatedRow> parentRepairs,
            ErrorMessage error) {
        public boolean successful() {
            return error == null;
        }
    }
}
