package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.FieldData;
import org.junit.jupiter.api.Test;

class GotoExecutionRoutingTest {

    @Test
    void resolvesDestinationBlockOrderToZeroBasedExecutionIndex() {
        assertEquals(1, InstructionGraph.gotoTargetIndex(
                new FieldData("1646:218:2:Destination", "1")));
    }

    @Test
    void rejectsMalformedGotoDetails() {
        assertEquals(-1, InstructionGraph.gotoTargetIndex(new FieldData("Unknown", "1")));
    }
}
