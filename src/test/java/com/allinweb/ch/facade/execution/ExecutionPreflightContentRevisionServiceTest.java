package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.BlockFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.InstructionFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.Owner;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.VariableFact;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionPreflightContentRevisionServiceTest {
    private final ExecutionPreflightContentRevisionService service =
            new ExecutionPreflightContentRevisionService();

    @Test
    void revisionIsOrderIndependentButIncludesEveryExecutionFact() {
        ExecutionPreflightSnapshot first = snapshot(true, "$String");
        ExecutionPreflightSnapshot reordered = new ExecutionPreflightSnapshot(
                first.owner(),
                List.of(first.blocks().get(1), first.blocks().get(0)),
                List.of(first.instructions().get(1), first.instructions().get(0)),
                first.variables());

        assertEquals(service.revision(first), service.revision(reordered));
        assertNotEquals(service.revision(first), service.revision(snapshot(false, "$String")));
        assertNotEquals(service.revision(first), service.revision(snapshot(true, "#Numeric")));
    }

    private ExecutionPreflightSnapshot snapshot(boolean firstBlockActive, String variableType) {
        return new ExecutionPreflightSnapshot(
                new Owner(2, 5),
                List.of(
                        new BlockFact(10, 1, firstBlockActive),
                        new BlockFact(20, 2, true)),
                List.of(
                        new InstructionFact(
                                101, 10, 1, "FIELD", "input", true, null, null, null),
                        new InstructionFact(
                                102, 10, 2, "GET", null, true, 101, null, 501)),
                List.of(new VariableFact(501, variableType, 101)));
    }
}
