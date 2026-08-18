package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ParentOperations;
import com.allinweb.ch.model.VariableUserDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableOperationRewriteServiceTest {
    private final VariableOperationRewriteService service = new VariableOperationRewriteService();

    @Test
    void rewritesEveryVariableCommandFamily() {
        List<ParentOperations> rows = List.of(
                row("SET", "Field:old"), row("GET", "Field:$old"), row("CK", "$old:>=:old"),
                row("PDF CHECK", "$old:=:old"), row("CSV CHECK", "$old:!=:old"), row("E", "$old"));
        VariableUserDTO variable = variable("#Numeric", "amount", "10");

        service.rewrite(rows, variable);

        assertEquals("Field:10", rows.get(0).getOperations());
        assertEquals("Field:#amount", rows.get(1).getOperations());
        assertEquals("#amount:>=:10", rows.get(2).getOperations());
        assertEquals("#amount:=:10", rows.get(3).getOperations());
        assertEquals("#amount:!=:10", rows.get(4).getOperations());
        assertEquals("#amount", rows.get(5).getOperations());
    }

    @Test
    void usesEmptyValueAndLeavesUnsupportedActionsUntouched() {
        ParentOperations check = row("CK", null);
        ParentOperations pause = row("PAUSE", "keep");
        service.rewrite(List.of(check, pause), variable("$String", "status", ""));
        assertEquals("$status:=:$EMPTY", check.getOperations());
        assertEquals("keep", pause.getOperations());
    }

    private ParentOperations row(String action, String operation) {
        ParentOperations row = new ParentOperations();
        row.setActions(action);
        row.setOperations(operation);
        return row;
    }

    private VariableUserDTO variable(String type, String name, String value) {
        return new VariableUserDTO(1, type, name, value, 19, 100, "Field", "", "", "");
    }
}
