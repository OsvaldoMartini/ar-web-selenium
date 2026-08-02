package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.VariablesWorkspaceInstructionStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

/** Persists the Variables-page command status with the same conditional-family rule as GridItem. */
public final class VariablesInstructionStatusTransaction {

    public Result execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesWorkspaceInstructionStatus.Request request)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        validate(owner, request);
        if (!connection.getAutoCommit()) {
            throw new SQLException("Variables command status requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            InstructionState current = load(
                    connection, owner.owner().ownerId(), request.instructionId());
            boolean expected = request.expectedActive();
            if (current.active() != expected) {
                throw refused(
                        "COMMAND_STATUS_CHANGED",
                        "The command status changed before this request. Review it and retry.");
            }
            int updatedCount = update(
                    connection,
                    owner.owner().ownerId(),
                    request.instructionId(),
                    current,
                    request.active());
            if (updatedCount <= 0) {
                throw refused(
                        "COMMAND_STATUS_NOT_UPDATED",
                        "The selected command status was not updated.");
            }
            connection.commit();
            return new Result(
                    request.requestId().trim(),
                    request.instructionId(),
                    request.active(),
                    updatedCount);
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                restoreAutoCommit = false;
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        } finally {
            if (restoreAutoCommit) connection.setAutoCommit(true);
        }
    }

    private static void validate(
            AuthenticatedBotJob owner,
            VariablesWorkspaceInstructionStatus.Request request)
            throws MutationRefusedException {
        if (!Objects.equals(
                request.contractVersion(), VariablesWorkspaceInstructionStatus.CONTRACT_VERSION)) {
            throw refused(
                    "COMMAND_STATUS_CONTRACT_UNSUPPORTED",
                    "The command-status contract is not supported.");
        }
        if (request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw refused(
                    "COMMAND_STATUS_REQUEST_ID_REQUIRED",
                    "A command-status request ID is required.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused(
                    "COMMAND_STATUS_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before the command status update.");
        }
        if (request.instructionId() == null || request.instructionId() <= 0) {
            throw refused(
                    "COMMAND_STATUS_INSTRUCTION_REQUIRED",
                    "A valid command ID is required.");
        }
        if (request.expectedActive() == null || request.active() == null) {
            throw refused(
                    "COMMAND_STATUS_VALUE_REQUIRED",
                    "The current and requested command statuses are required.");
        }
    }

    private static InstructionState load(
            Connection connection, int botJobId, int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT block_id,parent_id,actions,active FROM instruction"
                        + " WHERE bot_job_id=? AND id=?")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, instructionId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused(
                            "COMMAND_STATUS_INSTRUCTION_MISSING",
                            "The selected command no longer exists.");
                }
                int blockId = rows.getInt("block_id");
                Integer parentId = nullableInteger(rows, "parent_id");
                String action = rows.getString("actions");
                boolean active = rows.getBoolean("active");
                if (rows.wasNull()) active = true;
                return new InstructionState(blockId, parentId, action, active);
            }
        }
    }

    private static int update(
            Connection connection,
            int botJobId,
            int instructionId,
            InstructionState current,
            boolean active)
            throws SQLException {
        boolean conditional = switch (normalized(current.action())) {
            case "IF", "ELSEIF", "ELSE", "ENDIF" -> true;
            default -> false;
        };
        String sql;
        if (conditional && current.parentId() != null) {
            sql = "UPDATE instruction SET active=?"
                    + " WHERE bot_job_id=? AND block_id=? AND parent_id=?";
        } else {
            sql = "UPDATE instruction SET active=? WHERE bot_job_id=? AND id=?";
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, botJobId);
            if (conditional && current.parentId() != null) {
                statement.setInt(3, current.blockId());
                statement.setInt(4, current.parentId());
            } else {
                statement.setInt(3, instructionId);
            }
            return statement.executeUpdate();
        }
    }

    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }

    private record InstructionState(
            int blockId, Integer parentId, String action, boolean active) {}

    public record Result(
            String requestId, int instructionId, boolean active, int updatedCount) {}
}
