package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandOperationCodecTest {
    private final CommandOperationCodec codec = new CommandOperationCodec();

    @Test
    void decodesAliasesChecksLoopsAndCounts() {
        assertEquals("H", codec.decode(row("HOLD", "", 7)).get("action").getAsString());
        JsonObject check = codec.decode(row("CK", "$amount:>=:10", 1));
        assertEquals(">=", check.get("operator").getAsString());
        assertTrue(check.getAsJsonArray("warnings").isEmpty());
        JsonObject loop = codec.decode(row("LOOP", "3:8", 1));
        assertEquals(3, loop.get("interval").getAsInt());
        assertEquals(8, loop.get("count").getAsInt());
        assertEquals(4, codec.decode(row("SWIPE_DOWN", "4", 1)).get("count").getAsInt());
    }

    @Test
    void warnsWhenHistoricalOperationsAreMalformed() {
        assertEquals(1, codec.decode(row("CK", "$amount", 1)).getAsJsonArray("warnings").size());
        assertEquals(1, codec.decode(row("REFRESH_LOOP", "0:oops", 1)).getAsJsonArray("warnings").size());
        assertEquals(1, codec.decode(row("IF", "BROKEN", 1)).getAsJsonArray("warnings").size());
        assertEquals(1, codec.decode(row("PAUSE", "unexpected", 1)).getAsJsonArray("warnings").size());
        assertEquals(1, codec.decode(row("EXCEL GOTO", "2", 1)).getAsJsonArray("warnings").size());
    }

    @Test
    void decodesEveryRegisteredCommandFamilyWithoutWarnings() {
        Map<String, String> canonicalOperations = new LinkedHashMap<>();
        canonicalOperations.put("SET", "Field:$EMPTY");
        canonicalOperations.put("GET", "Field:$value");
        canonicalOperations.put("CK", "$value:=:$EMPTY");
        canonicalOperations.put("PDF CHECK", "$value:=:$EMPTY");
        canonicalOperations.put("CSV CHECK", "$value:=:$EMPTY");
        canonicalOperations.put("E", "$value");
        canonicalOperations.put("IF", "IF");
        canonicalOperations.put("GOTO", "2");
        canonicalOperations.put("EXCEL GOTO", "1");
        canonicalOperations.put("LOOP", "2:3");
        canonicalOperations.put("REFRESH_LOOP", "2:3");
        canonicalOperations.put("REFRESH", "");
        canonicalOperations.put("NEXT_ENTER", "");
        canonicalOperations.put("SWIPE_UP", "2");
        canonicalOperations.put("SWIPE_DOWN", "2");
        canonicalOperations.put("H", "");
        canonicalOperations.put("PAUSE", "");
        canonicalOperations.put("Q", "");
        canonicalOperations.put("P", "");

        assertEquals(CommandRegistry.catalog().size(), canonicalOperations.size());
        canonicalOperations.forEach((action, operation) -> assertTrue(
                codec.decode(row(action, operation, 1)).getAsJsonArray("warnings").isEmpty(), action));
    }

    @Test
    void encodesEveryRegisteredCommandFamilyCanonically() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("SET", "Field:10");
        expected.put("GET", "Field:#value");
        expected.put("CK", "#value:>=:10");
        expected.put("PDF CHECK", "#value:>=:10");
        expected.put("CSV CHECK", "#value:>=:10");
        expected.put("E", "#value");
        expected.put("IF", "IF");
        expected.put("GOTO", "4");
        expected.put("EXCEL GOTO", "1");
        expected.put("LOOP", "3:4");
        expected.put("REFRESH_LOOP", "3:4");
        expected.put("REFRESH", "");
        expected.put("NEXT_ENTER", "");
        expected.put("SWIPE_UP", "4");
        expected.put("SWIPE_DOWN", "4");
        expected.put("H", "");
        expected.put("PAUSE", "");
        expected.put("Q", "");
        expected.put("P", "");

        assertEquals(CommandRegistry.catalog().size(), expected.size());
        expected.forEach((action, operation) -> assertEquals(operation,
                CommandOperationCodec.encodeResolved(action, "Field", "#value", "10", ">=", "4", "3"), action));
    }

    private InstructionLoad row(String action, String operation, int hold) {
        InstructionLoad row = new InstructionLoad();
        row.setActions(action);
        row.setOperation(operation);
        row.setName(action);
        row.setOnHoldSeconds(hold);
        return row;
    }
}
