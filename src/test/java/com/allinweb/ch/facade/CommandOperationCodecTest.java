package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.InstructionLoad;
import com.google.gson.JsonObject;
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
