package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Persists the exact IF-family boundary removal scope authored and confirmed by React. */
final class VariablesConditionalFamilyDissolvePersistence {

    void deleteSiblings(
            Connection connection,
            OwnerKey owner,
            List<Integer> boundaryInstructionIds)
            throws SQLException {
        if (boundaryInstructionIds.isEmpty()) return;

        for (Integer instructionId : boundaryInstructionIds) {
            clearSurvivingLinks(connection, owner, instructionId);
            releaseVariableProducers(connection, owner, instructionId);
        }

        boolean hasCommandConfiguration =
                tableExists(connection, "instruction_variable_command_config");
        boolean hasUseCaseFieldMapping =
                tableExists(connection, "use_case_field_mapping");
        for (int index = boundaryInstructionIds.size() - 1; index >= 0; index--) {
            int instructionId = boundaryInstructionIds.get(index);
            deleteOwnedRows(
                    connection,
                    owner,
                    instructionId,
                    hasCommandConfiguration,
                    hasUseCaseFieldMapping);
            deleteBoundary(connection, owner.ownerId(), instructionId);
        }
    }

    private static void clearSurvivingLinks(
            Connection connection,
            OwnerKey owner,
            int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET parent_id=NULL,parent_block_id=NULL"
                        + " WHERE bot_job_id=? AND parent_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, instructionId);
            statement.executeUpdate();
        }
    }

    private static void releaseVariableProducers(
            Connection connection,
            OwnerKey owner,
            int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bot_job_variable_definition SET producer_instruction_id=NULL,"
                        + "updated_at=CURRENT_TIMESTAMP"
                        + " WHERE home_banking_id=? AND bot_job_id=?"
                        + " AND producer_instruction_id=?")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            statement.setInt(3, instructionId);
            statement.executeUpdate();
        }
    }

    private static void deleteOwnedRows(
            Connection connection,
            OwnerKey owner,
            int instructionId,
            boolean hasCommandConfiguration,
            boolean hasUseCaseFieldMapping)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM reference WHERE bot_job_id=? AND instruction_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, instructionId);
            statement.executeUpdate();
        }
        if (hasCommandConfiguration) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM instruction_variable_command_config"
                            + " WHERE home_banking_id=? AND bot_job_id=?"
                            + " AND instruction_id=?")) {
                statement.setInt(1, owner.homeBankingId());
                statement.setInt(2, owner.ownerId());
                statement.setInt(3, instructionId);
                statement.executeUpdate();
            }
        }
        if (hasUseCaseFieldMapping) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM use_case_field_mapping"
                            + " WHERE bot_job_id=? AND bot_instruction_id=?")) {
                statement.setInt(1, owner.ownerId());
                statement.setInt(2, instructionId);
                statement.executeUpdate();
            }
        }
    }

    private static void deleteBoundary(
            Connection connection,
            int botJobId,
            int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM instruction WHERE bot_job_id=? AND id=?")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, instructionId);
            if (statement.executeUpdate() != 1) {
                throw new MutationRefusedException(
                        "COMMAND_UPDATE_CONDITIONAL_SOURCE_CHANGED",
                        "The IF-family boundaries changed before they could be removed.");
            }
        }
    }

    private static boolean tableExists(Connection connection, String table)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }
}
