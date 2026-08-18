package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.UpdatedRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionDeleteContractValidatorTest {
    private final InstructionDeleteContractValidator validator =
            new InstructionDeleteContractValidator();

    @Test
    void acceptsAndPreservesTheExactOrderedBotJobIds() {
        SplitDTO request = request(11, List.of(13, 11));

        InstructionDeleteContractValidator.Validation validation = validator.validate(
                request,
                List.of(botRow(11, 5), botRow(12, 5), botRow(13, 5)),
                5,
                false);

        assertTrue(validation.successful());
        assertEquals(List.of(13, 11), validation.instructionIds());
    }

    @Test
    void acceptsIdsOwnedByTheRequestedComponentOrganization() {
        SplitDTO request = request(21, List.of(21, 22));
        request.setHomeBankingId(2);

        InstructionDeleteContractValidator.Validation validation = validator.validate(
                request,
                List.of(componentRow(21, 2), componentRow(22, 2)),
                2,
                true);

        assertTrue(validation.successful());
        assertEquals(List.of(21, 22), validation.instructionIds());
    }

    @Test
    void rejectsEmptyDuplicateAndMissingSelectedIds() {
        List<InstructionLoad> rows = List.of(botRow(11, 5), botRow(12, 5));

        assertFalse(validator.validate(request(11, List.of()), rows, 5, false).successful());
        assertEquals(
                "Duplicate delete instruction ID",
                validator.validate(request(11, List.of(11, 11)), rows, 5, false)
                        .error()
                        .getErrorHeader());
        assertEquals(
                "Selected instruction is missing",
                validator.validate(request(11, List.of(12)), rows, 5, false)
                        .error()
                        .getErrorHeader());
    }

    @Test
    void rejectsIdsOutsideTheCurrentOwnerEvenWhenTheIdExists() {
        SplitDTO request = request(11, List.of(11, 12));

        InstructionDeleteContractValidator.Validation validation = validator.validate(
                request,
                List.of(botRow(11, 5), botRow(12, 6)),
                5,
                false);

        assertFalse(validation.successful());
        assertEquals(
                "Instruction is outside the requested owner",
                validation.error().getErrorHeader());
    }

    @Test
    void rejectsUnsupportedContractVersions() {
        SplitDTO request = request(11, List.of(11));
        request.setDeleteContractVersion(1);

        InstructionDeleteContractValidator.Validation validation =
                validator.validate(request, List.of(botRow(11, 5)), 5, false);

        assertFalse(validation.successful());
        assertEquals("Unsupported delete contract", validation.error().getErrorHeader());
    }

    @Test
    void acceptsOnlyTheExplicitParentRepairsWithoutInferringMissingOnes() {
        InstructionLoad selected = botRow(11, 5);
        InstructionLoad explicitRepair = botRow(12, 5);
        explicitRepair.setParentId(11);
        InstructionLoad omittedRepair = botRow(13, 5);
        omittedRepair.setParentId(11);
        SplitDTO request = request(11, List.of(11));
        request.setDeleteParentRepairs(List.of(clearParent(12)));

        InstructionDeleteContractValidator.Validation validation = validator.validate(
                request,
                List.of(selected, explicitRepair, omittedRepair),
                5,
                false);

        assertTrue(validation.successful());
        assertEquals(
                List.of(12),
                validation.parentRepairs().stream()
                        .map(UpdatedRow::getInstructionId)
                        .toList());
    }

    @Test
    void rejectsARepairThatDoesNotPointToAnExactlyDeletedParent() {
        InstructionLoad selected = botRow(11, 5);
        InstructionLoad survivor = botRow(12, 5);
        survivor.setParentId(99);
        SplitDTO request = request(11, List.of(11));
        request.setDeleteParentRepairs(List.of(clearParent(12)));

        InstructionDeleteContractValidator.Validation validation =
                validator.validate(request, List.of(selected, survivor), 5, false);

        assertFalse(validation.successful());
        assertEquals(
                "Parent repair does not match the stored graph",
                validation.error().getErrorHeader());
    }

    private SplitDTO request(int selectedId, List<Integer> ids) {
        SplitDTO request = new SplitDTO();
        request.setDeleteContractVersion(InstructionDeleteContractValidator.CONTRACT_VERSION);
        request.setInstructionId(selectedId);
        request.setDeleteInstructionIds(ids);
        request.setBotJobId(5);
        return request;
    }

    private InstructionLoad botRow(int id, int botJobId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setBotJobId(botJobId);
        return row;
    }

    private InstructionLoad componentRow(int id, int homeBankingId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setHomeBankingId(homeBankingId);
        return row;
    }

    private UpdatedRow clearParent(int instructionId) {
        UpdatedRow repair = new UpdatedRow();
        repair.setInstructionId(instructionId);
        repair.setParentId(null);
        return repair;
    }
}
