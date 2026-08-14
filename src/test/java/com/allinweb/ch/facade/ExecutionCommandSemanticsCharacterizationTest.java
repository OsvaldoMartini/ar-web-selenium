package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Freezes the persisted command contract that P1 observed before relationship editing changes.
 *
 * <p>This test deliberately characterizes the current facade contract. It does not treat
 * successful metadata/operation encoding as proof of integrated Scanner or Engine execution.
 */
class ExecutionCommandSemanticsCharacterizationTest {

    @Test
    void characterizesVariableCommandOperationsAndRoles() {
        List<VariableCase> cases = List.of(
                new VariableCase("GET", "Account:#amount", true, false),
                new VariableCase("SET", "Account:10", false, false),
                new VariableCase("E", "#amount", false, true),
                new VariableCase("CK", "#amount:>=:10", false, true),
                new VariableCase("PDF CHECK", "#amount:>=:10", false, true),
                new VariableCase("CSV CHECK", "#amount:>=:10", false, true));

        for (VariableCase value : cases) {
            assertEquals(
                    value.operation(),
                    CommandOperationCodec.encodeResolved(
                            value.action(), "Account", "#amount", "10", ">=", "4", "3"),
                    value.action());
            assertEquals(
                    List.of("GET", "SET").contains(value.action()),
                    CommandRegistry.requires(value.action(), "webField"),
                    value.action());
            assertTrue(CommandRegistry.requires(value.action(), "variable"), value.action());
            assertTrue(VariableDefinitionPolicy.isVariableCommand(value.action()), value.action());
            assertEquals(value.producer(), VariableDefinitionPolicy.isProducer(value.action()), value.action());
            assertEquals(value.consumer(), VariableDefinitionPolicy.isConsumer(value.action()), value.action());
        }

        assertFalse(
                VariableDefinitionPolicy.isConsumer("SET"),
                "Current SET writes its configured literal value; it is not a GET consumer.");
    }

    @Test
    void characterizesSetAsALiteralWriterRatherThanAVariableConsumer() {
        assertEquals(
                "Account:10",
                CommandOperationCodec.encodeResolved(
                        "SET", "Account", "#a-different-variable", "10", ">=", "4", "3"));
        assertEquals(
                "Account:$EMPTY",
                CommandOperationCodec.encodeResolved(
                        "SET", "Account", "#amount", "", ">=", "4", "3"));
        assertEquals(
                "Account:$EMPTY",
                CommandOperationCodec.encodeResolved(
                        "SET", "Account", "#amount", null, ">=", "4", "3"));

        assertTrue(CommandRegistry.requires("SET", "webField"));
        assertTrue(CommandRegistry.requires("SET", "variable"));
        assertTrue(VariableDefinitionPolicy.isVariableCommand("SET"));
        assertFalse(VariableDefinitionPolicy.isProducer("SET"));
        assertFalse(VariableDefinitionPolicy.isConsumer("SET"));
    }

    @Test
    void characterizesCheckAuthoringContractSeparatelyFromRuntimeDataSources() {
        CommandOperationCodec codec = new CommandOperationCodec();

        for (String action : List.of("CK", "PDF CHECK", "CSV CHECK")) {
            String operation = CommandOperationCodec.encodeResolved(
                    action, "Account", "#amount", "10", ">=", "4", "3");
            assertEquals("#amount:>=:10", operation, action);
            assertFalse(
                    CommandRegistry.requires(action, "webField"),
                    action + " compares variable/runtime data and has no Web Element target.");
            assertTrue(CommandRegistry.requires(action, "variable"), action);
            assertTrue(CommandRegistry.requires(action, "operator"), action);
            assertFalse(
                    CommandRegistry.requires(action, "value"),
                    action + " obtains the encoded expected value from variable metadata.");
            assertTrue(VariableDefinitionPolicy.isConsumer(action), action);

            InstructionLoad instruction = instruction(action, operation, 101, 202, 303);
            JsonObject decoded = codec.decode(instruction);
            assertEquals(">=", decoded.get("operator").getAsString(), action);
            assertTrue(decoded.getAsJsonArray("warnings").isEmpty(), action);
        }
    }

    @Test
    void characterizesLoopAndNavigationOperations() {
        assertEquals(
                "3:4",
                CommandOperationCodec.encodeResolved(
                        "LOOP", "Account", "#amount", "10", ">=", "4", "3"));
        assertEquals(
                "3:4",
                CommandOperationCodec.encodeResolved(
                        "REFRESH_LOOP", "Account", "#amount", "10", ">=", "4", "3"));
        assertEquals(
                "4",
                CommandOperationCodec.encodeResolved(
                        "GOTO", "Account", "#amount", "10", ">=", "4", "3"));
        assertEquals(
                "1",
                CommandOperationCodec.encodeResolved(
                        "EXCEL GOTO", "Account", "#amount", "10", ">=", "4", "3"));

        assertTrue(CommandRegistry.requires("LOOP", "webField"));
        assertTrue(CommandRegistry.requires("REFRESH_LOOP", "webField"));
        assertTrue(CommandRegistry.requires("GOTO", "block"));
        assertTrue(CommandRegistry.requires("EXCEL GOTO", "block"));
        assertFalse(VariableDefinitionPolicy.isVariableCommand("LOOP"));
        assertFalse(VariableDefinitionPolicy.isVariableCommand("GOTO"));
    }

    @Test
    void decodePreservesExplicitRelationshipIdentifiers() {
        CommandOperationCodec codec = new CommandOperationCodec();

        for (RelationshipCase value : List.of(
                new RelationshipCase("GET", "Account:#amount"),
                new RelationshipCase("SET", "Account:10"),
                new RelationshipCase("E", "#amount"),
                new RelationshipCase("CK", "#amount:>=:10"),
                new RelationshipCase("PDF CHECK", "#amount:>=:10"),
                new RelationshipCase("CSV CHECK", "#amount:>=:10"),
                new RelationshipCase("LOOP", "3:4"),
                new RelationshipCase("REFRESH_LOOP", "3:4"),
                new RelationshipCase("GOTO", "4"),
                new RelationshipCase("EXCEL GOTO", "1"))) {
            JsonObject decoded = codec.decode(instruction(value.action(), value.operation(), 101, 202, 303));

            assertEquals(101, decoded.get("parentId").getAsInt(), value.action());
            assertEquals(202, decoded.get("parentBlockId").getAsInt(), value.action());
            assertEquals(303, decoded.get("variableId").getAsInt(), value.action());
            assertTrue(decoded.getAsJsonArray("warnings").isEmpty(), value.action());
        }
    }

    @Test
    void decodeDoesNotInventMissingRelationshipIdentifiersFromOperationText() {
        InstructionLoad instruction = instruction("CK", "#amount:>=:10", null, null, null);

        JsonObject decoded = new CommandOperationCodec().decode(instruction);

        assertFalse(decoded.has("parentId"));
        assertFalse(decoded.has("parentBlockId"));
        assertFalse(decoded.has("variableId"));
        assertEquals(">=", decoded.get("operator").getAsString());
        assertTrue(decoded.getAsJsonArray("warnings").isEmpty());
    }

    private static InstructionLoad instruction(
            String action, String operation, Integer parentId, Integer parentBlockId, Integer variableId) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setActions(action);
        instruction.setName(action);
        instruction.setOperation(operation);
        instruction.setParentId(parentId);
        instruction.setParentBlockId(parentBlockId);
        instruction.setVariableId(variableId);
        instruction.setOnHoldSeconds(1);
        return instruction;
    }

    private record VariableCase(String action, String operation, boolean producer, boolean consumer) {}

    private record RelationshipCase(String action, String operation) {}
}
