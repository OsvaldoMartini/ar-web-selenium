package com.allinweb.ch.facade;

import com.allinweb.ch.model.ParentOperations;
import com.allinweb.ch.model.VariableUserDTO;
import java.util.List;

/** Rewrites dependent command operations after a variable name, type, or value change. */
public final class VariableOperationRewriteService {
    public void rewrite(List<ParentOperations> dependents, VariableUserDTO variable) {
        String typedName = ("#Numeric".equals(variable.getType()) ? "#" : "$") + variable.getName();
        String value = variable.getValue() == null || variable.getValue().isBlank() ? "$EMPTY" : variable.getValue();
        for (ParentOperations dependent : dependents) {
            String[] parts = dependent.getOperations() == null
                    ? new String[0]
                    : dependent.getOperations().split(":", -1);
            switch (dependent.getActions() == null ? "" : dependent.getActions()) {
                case "SET" -> dependent.setOperations(
                        (parts.length > 0 ? parts[0] : variable.getParentName()) + ":" + value);
                case "GET" -> dependent.setOperations(
                        (parts.length > 0 ? parts[0] : variable.getParentName()) + ":" + typedName);
                case "CK", "PDF CHECK", "CSV CHECK" -> dependent.setOperations(
                        typedName + ":" + (parts.length > 1 ? parts[1] : "=") + ":" + value);
                case "E" -> dependent.setOperations(typedName);
                default -> { }
            }
        }
    }
}
