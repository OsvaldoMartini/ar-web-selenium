package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import org.junit.jupiter.api.Test;

class CommandEditorDeleteMetadataTest {
    @Test
    void acceptsLegacyZeroForASqlNullParent() {
        InstructionLoad stored = instruction("C", null, 247);
        SplitDTO request = request("C", 0, 247);

        assertTrue(CommandEditorService.deleteMetadataMatches(request, stored));
        assertNull(CommandEditorService.getInstance().validateDeleteMetadata(request, stored));
    }

    @Test
    void treatsAllNonPositiveOptionalParentValuesAsAbsent() {
        InstructionLoad stored = instruction("I:importo", null, 247);

        assertTrue(CommandEditorService.deleteMetadataMatches(request("I:importo", null, 247), stored));
        assertTrue(CommandEditorService.deleteMetadataMatches(request("I:importo", 0, 247), stored));
        assertTrue(CommandEditorService.deleteMetadataMatches(request("I:importo", -1, 247), stored));
    }

    @Test
    void keepsStrictChecksForRealMetadataChanges() {
        InstructionLoad stored = instruction("LOOP", 1771, 247);

        assertTrue(CommandEditorService.deleteMetadataMatches(request("loop", 1771, 247), stored));
        assertFalse(CommandEditorService.deleteMetadataMatches(request("REFRESH_LOOP", 1771, 247), stored));
        assertFalse(CommandEditorService.deleteMetadataMatches(request("LOOP", 1772, 247), stored));
        assertFalse(CommandEditorService.deleteMetadataMatches(request("LOOP", 1771, 248), stored));
    }

    private InstructionLoad instruction(String action, Integer parentId, int blockId) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(1771);
        instruction.setActions(action);
        instruction.setParentId(parentId);
        instruction.setBlockId(blockId);
        return instruction;
    }

    private SplitDTO request(String action, Integer parentId, int blockId) {
        SplitDTO request = new SplitDTO();
        request.setRequestId("delete-1771");
        request.setInstructionId(1771);
        request.setActions(action);
        request.setParentId(parentId);
        request.setBlockId(blockId);
        return request;
    }
}
