package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renames only variable-flow slot values; the instruction_variable_slot structure is unchanged. */
public final class M20260806_VariableSlotDirectionNames implements Migration {
    private static final String NAME = "2026-08-06__variable_slot_direction_names";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        List<ExistingSlot> existing = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                        "SELECT s.home_banking_id,s.bot_job_id,s.instruction_id,s.slot,i.actions"
                                + " FROM instruction_variable_slot s"
                                + " INNER JOIN instruction i ON i.id=s.instruction_id"
                                + " AND i.bot_job_id=s.bot_job_id"
                                + " WHERE s.slot IN ('OUTPUT','SOURCE')");
                ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                existing.add(new ExistingSlot(
                        rows.getInt("home_banking_id"),
                        rows.getInt("bot_job_id"),
                        rows.getInt("instruction_id"),
                        rows.getString("slot"),
                        targetSlot(rows.getString("actions"))));
            }
        }

        try (PreparedStatement targetExists = connection.prepareStatement(
                        "SELECT 1 FROM instruction_variable_slot"
                                + " WHERE home_banking_id=? AND bot_job_id=?"
                                + " AND instruction_id=? AND slot=?");
                PreparedStatement rename = connection.prepareStatement(
                        "UPDATE instruction_variable_slot SET slot=?,"
                                + "slot_revision=slot_revision+1,updated_at=CURRENT_TIMESTAMP"
                                + " WHERE home_banking_id=? AND bot_job_id=?"
                                + " AND instruction_id=? AND slot=?");
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM instruction_variable_slot"
                                + " WHERE home_banking_id=? AND bot_job_id=?"
                                + " AND instruction_id=? AND slot=?")) {
            for (ExistingSlot row : existing) {
                boolean duplicate = row.targetSlot() != null
                        && exists(targetExists, row, row.targetSlot());
                if (row.targetSlot() == null || duplicate) {
                    bindOwner(delete, row, 1);
                    delete.setString(4, row.oldSlot());
                    delete.executeUpdate();
                    continue;
                }
                rename.setString(1, row.targetSlot());
                bindOwner(rename, row, 2);
                rename.setString(5, row.oldSlot());
                rename.executeUpdate();
            }
        }
    }

    private static boolean exists(
            PreparedStatement statement, ExistingSlot row, String slot) throws SQLException {
        bindOwner(statement, row, 1);
        statement.setString(4, slot);
        try (ResultSet result = statement.executeQuery()) {
            return result.next();
        }
    }

    private static void bindOwner(
            PreparedStatement statement, ExistingSlot row, int offset) throws SQLException {
        statement.setInt(offset, row.homeBankingId());
        statement.setInt(offset + 1, row.botJobId());
        statement.setInt(offset + 2, row.instructionId());
    }

    private static String targetSlot(String actions) {
        String action = actions == null ? "" : actions.split(":", 2)[0]
                .trim().toUpperCase(Locale.ROOT);
        if ("GET".equals(action)) return "GET_WRITE";
        if ("SET".equals(action)) return "READ_SET";
        if ("E".equals(action)) return "READ";
        return null;
    }

    private record ExistingSlot(
            int homeBankingId,
            int botJobId,
            int instructionId,
            String oldSlot,
            String targetSlot) {}
}
