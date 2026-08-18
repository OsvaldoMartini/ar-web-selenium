package com.allinweb.ch.facade;

import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionPatch;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.allinweb.ch.model.ParentOperations;
import com.allinweb.ch.model.VariableUserDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Connection-scoped variable update and dependent-operation rewrite transaction. */
final class VariableUpdateTransaction {
    private final BotJobRuntimeVariableService botJobRuntimeVariables =
            new BotJobRuntimeVariableService();

    void execute(
            Connection connection,
            String variableTable,
            String instructionTable,
            int whereId,
            VariableUserDTO variable,
            List<ParentOperations> dependents)
            throws SQLException {
        boolean botJob = ("variable".equals(variableTable)
                        || "bot_job_variable_definition".equals(variableTable))
                && "instruction".equals(instructionTable);
        boolean component = "component_variable".equals(variableTable)
                && "component_instruction".equals(instructionTable);
        if (!botJob && !component) throw new SQLException("Invalid variable table pair.");

        String owner = botJob ? "bot_job_id" : "home_banking_id";
        String physicalVariableTable =
                botJob ? "bot_job_variable_definition" : variableTable;
        String variableSql = botJob
                ? "UPDATE bot_job_variable_definition"
                        + " SET name=?,variable_type=?,configured_value=?,local_format=?,"
                        + "delimiter=?,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=? AND producer_instruction_id=? AND bot_job_id=?"
                : "UPDATE " + physicalVariableTable
                        + " SET name=?,type=?,value=?,local_format=?,delimiter=?"
                        + " WHERE id=? AND instruction_id=? AND " + owner + "=?";
        String operationSql = "UPDATE " + instructionTable
                + " SET operation=? WHERE id=? AND parent_id=? AND " + owner
                + "=? AND variable_id=?";
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (botJob) {
                MutationResult updated = botJobRuntimeVariables.updateDefinition(
                        connection,
                        owner(connection, whereId),
                        variable.getId(),
                        new DefinitionPatch(
                                variable.getType(),
                                variable.getName(),
                                variable.getValue(),
                                variable.getLocalFormat(),
                                variable.getDelimiter(),
                                variable.getParentId() == null
                                        ? null
                                        : variable.getParentId().longValue()),
                        null);
                if (!updated.applied()) {
                    throw new SQLException(updated.message());
                }
            } else {
                try (PreparedStatement update = connection.prepareStatement(variableSql)) {
                    update.setString(1, variable.getName());
                    update.setString(2, variable.getType());
                    update.setString(3, variable.getValue());
                    update.setString(4, variable.getLocalFormat());
                    update.setString(5, variable.getDelimiter());
                    update.setInt(6, variable.getId());
                    update.setInt(7, variable.getParentId());
                    update.setInt(8, whereId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Variable could not be updated exactly once.");
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement(operationSql)) {
                for (ParentOperations dependent : dependents == null ? List.<ParentOperations>of() : dependents) {
                    String action = dependent.getActions() == null ? "" : dependent.getActions();
                    if (!VariableDefinitionPolicy.isVariableCommand(action)) continue;
                    update.setString(1, dependent.getOperations());
                    update.setInt(2, dependent.getId());
                    update.setInt(3, dependent.getInstructionId());
                    update.setInt(4, whereId);
                    update.setInt(5, variable.getId());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException(
                                "Dependent instruction " + dependent.getId() + " could not be rewritten exactly once.");
                    }
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static OwnerKey owner(Connection connection, int botJobId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT home_banking_id FROM bot_job WHERE id=?")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("The Bot Job variable owner was not found.");
                }
                return new OwnerKey(rows.getInt(1), botJobId);
            }
        }
    }
}
