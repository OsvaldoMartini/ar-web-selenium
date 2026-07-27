package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandEditorSelectionTest {

    @Test
    void keepsEveryBlockInDeterministicOrder() {
        BlockLoadDTO third = block(13, 3, 42, 7);
        BlockLoadDTO first = block(11, 1, 42, 7);
        BlockLoadDTO second = block(12, 2, 42, 7);

        List<BlockLoadDTO> ordered =
                CommandEditorService.orderedBlocks(List.of(third, first, second));

        assertEquals(
                List.of(11, 12, 13),
                ordered.stream().map(BlockLoadDTO::getId).toList());
    }

    @Test
    void keepsEveryInstructionInDeterministicBlockAndInstructionOrder() {
        InstructionLoad blockTwoSecond = instruction(202, 12, 2, 2, 42, 7);
        InstructionLoad blockOneSecond = instruction(102, 11, 1, 2, 42, 7);
        InstructionLoad blockTwoFirst = instruction(201, 12, 2, 1, 42, 7);
        InstructionLoad blockOneFirst = instruction(101, 11, 1, 1, 42, 7);

        List<InstructionLoad> ordered = CommandEditorService.orderedInstructions(List.of(
                blockTwoSecond,
                blockOneSecond,
                blockTwoFirst,
                blockOneFirst));

        assertEquals(
                List.of(101, 102, 201, 202),
                ordered.stream().map(InstructionLoad::getId).toList());
    }

    @Test
    void enrichesBlockMetadataAndOrdersByBlockOrderInsteadOfBlockId() {
        BlockLoadDTO firstBlock = block(900, 1, 42, 7);
        firstBlock.setName("First workflow block");
        BlockLoadDTO secondBlock = block(100, 2, 42, 7);
        secondBlock.setName("Second workflow block");
        InstructionLoad first = instruction(901, 900, 99, 1, 42, 7);
        InstructionLoad second = instruction(101, 100, 1, 1, 42, 7);

        List<InstructionLoad> ordered = CommandEditorService.enrichAndOrderInstructions(
                List.of(second, first), List.of(secondBlock, firstBlock));

        assertEquals(List.of(901, 101), ordered.stream().map(InstructionLoad::getId).toList());
        assertEquals("First workflow block", ordered.get(0).getBlockName());
        assertEquals(1, ordered.get(0).getBlockOrderNumber());
        assertEquals("Second workflow block", ordered.get(1).getBlockName());
        assertEquals(2, ordered.get(1).getBlockOrderNumber());
    }

    @Test
    void resolvesOnlyAnInstructionOwnedByTheSelectedBlockAndWorkspace() {
        BlockLoadDTO selectedBlock = block(11, 1, 42, 7);
        InstructionLoad selectedInstruction = instruction(101, 11, 1, 1, 42, 7);

        InstructionLoad resolved = CommandEditorService.resolveSelectionFromRows(
                List.of(selectedBlock),
                List.of(selectedInstruction),
                42,
                7,
                11,
                101);

        assertSame(selectedInstruction, resolved);
    }

    @Test
    void refusesAnInstructionFromAnotherBlock() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        List.of(block(11, 1, 42, 7), block(12, 2, 42, 7)),
                        List.of(instruction(201, 12, 2, 1, 42, 7)),
                        42,
                        7,
                        11,
                        201));

        assertEquals(
                "The selected instruction does not belong to the selected Block.",
                error.getMessage());
    }

    @Test
    void refusesForeignBotJobAndOrganizationOwnership() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        List.of(block(11, 1, 99, 7)),
                        List.of(instruction(101, 11, 1, 1, 42, 7)),
                        42,
                        7,
                        11,
                        101));

        assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        List.of(block(11, 1, 42, 8)),
                        List.of(instruction(101, 11, 1, 1, 42, 7)),
                        42,
                        7,
                        11,
                        101));

        assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        List.of(block(11, 1, 42, 7)),
                        List.of(instruction(101, 11, 1, 1, 99, 7)),
                        42,
                        7,
                        11,
                        101));

        assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        List.of(block(11, 1, 42, 7)),
                        List.of(instruction(101, 11, 1, 1, 42, 8)),
                        42,
                        7,
                        11,
                        101));
    }

    @Test
    void routesComponentTargetsThroughOrganizationOwnedTables() {
        CommandEditorService.InstructionTarget target =
                CommandEditorService.instructionTarget(
                        ScannerWorkspaceSessions.COMPONENT_TASKS,
                        42,
                        7);

        assertEquals(7, target.ownerId());
        assertEquals("component_instruction", target.instructionTable());
        assertEquals("component_block", target.blockTable());
        assertEquals("Components", target.workspaceLabel());
    }

    @Test
    void retainsBotJobTargetTablesAndOwnerScope() {
        CommandEditorService.InstructionTarget target =
                CommandEditorService.instructionTarget(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        42,
                        7);

        assertEquals(42, target.ownerId());
        assertEquals("instruction", target.instructionTable());
        assertEquals("block", target.blockTable());
        assertEquals("Bot Job Details", target.workspaceLabel());
    }

    @Test
    void resolvesComponentSelectionByOrganizationInsteadOfBotJobMetadata() {
        BlockLoadDTO selectedBlock = block(11, 1, 7, 7);
        InstructionLoad selectedInstruction = instruction(101, 11, 1, 1, 42, 7);
        selectedInstruction.setBotJobId(null);

        InstructionLoad resolved = CommandEditorService.resolveSelectionFromRows(
                ScannerWorkspaceSessions.COMPONENT_TASKS,
                List.of(selectedBlock),
                List.of(selectedInstruction),
                42,
                7,
                11,
                101);

        assertSame(selectedInstruction, resolved);
    }

    @Test
    void refusesComponentRowsOwnedByAnotherOrganization() {
        BlockLoadDTO selectedBlock = block(11, 1, 99, 8);
        InstructionLoad selectedInstruction = instruction(101, 11, 1, 1, 99, 7);
        selectedInstruction.setBotJobId(null);

        IllegalArgumentException foreignBlock = assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        ScannerWorkspaceSessions.COMPONENT_TASKS,
                        List.of(selectedBlock),
                        List.of(selectedInstruction),
                        42,
                        7,
                        11,
                        101));

        assertEquals(
                "The selected Block does not belong to this organization.",
                foreignBlock.getMessage());

        selectedBlock.setHomeBankingId(7);
        selectedInstruction.setHomeBankingId(8);
        IllegalArgumentException foreignInstruction = assertThrows(
                IllegalArgumentException.class,
                () -> CommandEditorService.resolveSelectionFromRows(
                        ScannerWorkspaceSessions.COMPONENT_TASKS,
                        List.of(selectedBlock),
                        List.of(selectedInstruction),
                        42,
                        7,
                        11,
                        101));

        assertEquals(
                "The selected instruction does not belong to this organization.",
                foreignInstruction.getMessage());
    }

    private static BlockLoadDTO block(
            int id, int order, int botJobId, int homeBankingId) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setName("Block " + id);
        block.setBlockOrderNumber(order);
        block.setBotJobId(botJobId);
        block.setHomeBankingId(homeBankingId);
        return block;
    }

    private static InstructionLoad instruction(
            int id,
            int blockId,
            int blockOrder,
            int instructionOrder,
            int botJobId,
            int homeBankingId) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(id);
        instruction.setName("Instruction " + id);
        instruction.setActions("H");
        instruction.setBlockId(blockId);
        instruction.setBlockOrderNumber(blockOrder);
        instruction.setInstructionOrderNumber(instructionOrder);
        instruction.setBotJobId(botJobId);
        instruction.setHomeBankingId(homeBankingId);
        return instruction;
    }
}
