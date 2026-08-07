package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.GridItemWebElementTypeContracts.WebElementType;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class GridItemWebElementTypeContractsTest {

    @Test
    void parsesTheCompleteReducedContract() {
        JsonObject body = validBody();

        GridItemWebElementTypeContracts.Request request =
                GridItemWebElementTypeContracts.parse(body);

        assertEquals("type-1", request.requestId());
        assertEquals(2, request.homeBankingId());
        assertEquals(32, request.botJobId());
        assertEquals(1728, request.instructionId());
        assertEquals(9L, request.workspaceEpoch());
        assertEquals(44L, request.baseGraphVersion());
        assertEquals("a".repeat(64), request.graphRevision());
        assertEquals(WebElementType.OUTPUT, request.expectedType());
        assertEquals(WebElementType.INPUT, request.replacementType());
    }

    @Test
    void rejectsMissingOptimisticAuthority() {
        JsonObject body = validBody();
        body.remove("graphRevision");

        assertThrows(
                IllegalArgumentException.class,
                () -> GridItemWebElementTypeContracts.parse(body));
    }

    @Test
    void rejectsTypesOutsideTheThreeStateContract() {
        JsonObject body = validBody();
        body.addProperty("replacementType", "GET");

        assertThrows(
                IllegalArgumentException.class,
                () -> GridItemWebElementTypeContracts.parse(body));
    }

    private static JsonObject validBody() {
        JsonObject body = new JsonObject();
        body.addProperty("contractVersion", GridItemWebElementTypeContracts.CONTRACT_VERSION);
        body.addProperty("requestId", "type-1");
        body.addProperty("homeBankingId", 2);
        body.addProperty("botJobId", 32);
        body.addProperty("instructionId", 1728);
        body.addProperty("workspaceEpoch", 9);
        body.addProperty("baseGraphVersion", 44);
        body.addProperty("graphRevision", "A".repeat(64));
        body.addProperty("expectedType", "OUTPUT");
        body.addProperty("replacementType", "INPUT");
        return body;
    }
}
