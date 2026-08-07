package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class GridItemTestActionContractsTest {
    @Test
    void parsesMinimalInputAndDefaultsToFirstExcelRow() {
        JsonObject body = body("INPUT");

        GridItemTestActionContracts.Request request =
                GridItemTestActionContracts.parse(body);

        assertEquals(1, request.contractVersion());
        assertEquals("req-1", request.requestId());
        assertEquals(2, request.homeBankingId());
        assertEquals(29, request.botJobId());
        assertEquals(1728, request.instructionId());
        assertEquals(Action.INPUT, request.action());
        assertEquals(0, request.excelRowIndex());
        assertNull(request.workspaceEpoch());
        assertNull(request.baseGraphVersion());
        assertNull(request.graphRevision());
    }

    @Test
    void parsesOptionalAuthorityFieldsAndNormalizesActionAndRevision() {
        JsonObject body = body("click");
        body.addProperty("excelRowIndex", 3);
        body.addProperty("workspaceEpoch", 7);
        body.addProperty("baseGraphVersion", 19);
        body.addProperty("graphRevision", "A".repeat(64));

        GridItemTestActionContracts.Request request =
                GridItemTestActionContracts.parse(body);

        assertEquals(Action.CLICK, request.action());
        assertEquals(3, request.excelRowIndex());
        assertEquals(7L, request.workspaceEpoch());
        assertEquals(19L, request.baseGraphVersion());
        assertEquals("a".repeat(64), request.graphRevision());
    }

    @Test
    void rejectsUnknownActionsAndInvalidRows() {
        JsonObject unknown = body("HOVER");
        assertThrows(
                IllegalArgumentException.class,
                () -> GridItemTestActionContracts.parse(unknown));

        JsonObject negativeRow = body("INPUT");
        negativeRow.addProperty("excelRowIndex", -1);
        assertThrows(
                IllegalArgumentException.class,
                () -> GridItemTestActionContracts.parse(negativeRow));

        JsonObject staleVersion = body("INPUT");
        staleVersion.addProperty("contractVersion", 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> GridItemTestActionContracts.parse(staleVersion));
    }

    private JsonObject body(String action) {
        JsonObject body = new JsonObject();
        body.addProperty("contractVersion", 1);
        body.addProperty("requestId", "req-1");
        body.addProperty("homeBankingId", 2);
        body.addProperty("botJobId", 29);
        body.addProperty("instructionId", 1728);
        body.addProperty("action", action);
        return body;
    }
}
