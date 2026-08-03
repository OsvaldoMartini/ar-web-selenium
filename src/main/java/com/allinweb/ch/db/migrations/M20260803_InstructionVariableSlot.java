package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Locale;

/**
 * Correct variable-connection design (user decision 2026-08-03): ONE uniform slot table.
 *
 * <p>Every variable connection of every Bot Job command is one row keyed by
 * {@code (home_banking_id, bot_job_id, instruction_id, slot)}. Slot meanings:
 * CK / CSV CHECK / PDF CHECK use {@code LEFT} + {@code RIGHT}; SET uses {@code SOURCE};
 * GET / E and every other variable-carrying command use {@code OUTPUT}. A free spot is
 * simply an absent row; the primary key makes duplicates impossible.
 *
 * <p>This migration only creates and BACKFILLS the table from the legacy columns
 * ({@code instruction.variable_id} and
 * {@code instruction_variable_command_config.operand_variable_id}). Nothing is deleted:
 * the legacy columns stay authoritative for the Engine until the write-through phase
 * mirrors them, so replay is unaffected. Bot Job scope only — component instructions are
 * out of scope for the Variables workspace rules.
 */
public final class M20260803_InstructionVariableSlot implements Migration {
    public static final String TABLE = "instruction_variable_slot";
    public static final String SLOT_LEFT = "LEFT";
    public static final String SLOT_RIGHT = "RIGHT";
    public static final String SLOT_OUTPUT = "OUTPUT";
    public static final String SLOT_SOURCE = "SOURCE";
    private static final String NAME = "2026-08-03__instruction_variable_slot";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        boolean created = false;
        if (!tableExists(connection, TABLE)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableSql(dialect));
            }
            created = true;
        }
        if (!indexExists(connection, "ix_instruction_variable_slot_variable")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE INDEX ix_instruction_variable_slot_variable ON "
                        + TABLE + " (home_banking_id, bot_job_id, variable_id)");
            }
        }
        if (created || isEmpty(connection)) {
            backfill(connection);
        }
    }

    static String createTableSql(String dialect) {
        String integerType = switch (dialect) {
            case "Postgres", "SQLServer" -> "BIGINT";
            case "TEXT" -> "INTEGER";
            default -> "LONG";
        };
        String timestampType = switch (dialect) {
            case "Postgres" -> "TIMESTAMP";
            case "SQLServer" -> "DATETIME2";
            case "TEXT" -> "TEXT";
            default -> "DATETIME";
        };
        return "CREATE TABLE " + TABLE + " ("
                + "home_banking_id " + integerType + " NOT NULL,"
                + "bot_job_id " + integerType + " NOT NULL,"
                + "instruction_id " + integerType + " NOT NULL,"
                + "slot VARCHAR(16) NOT NULL,"
                + "variable_id " + integerType + " NOT NULL,"
                + "slot_revision " + integerType + " NOT NULL,"
                + "created_at " + timestampType + " NOT NULL,"
                + "updated_at " + timestampType + " NOT NULL,"
                + "CONSTRAINT pk_instruction_variable_slot PRIMARY KEY"
                + " (home_banking_id, bot_job_id, instruction_id, slot)"
                + ")";
    }

    /** Slot carried by the legacy {@code instruction.variable_id} column per command. */
    static String legacySlotFor(String actions) {
        String canonical = actions == null ? "" : actions.trim().toUpperCase(Locale.ROOT);
        if ("CK".equals(canonical) || "CSV CHECK".equals(canonical) || "PDF CHECK".equals(canonical)) {
            return SLOT_LEFT;
        }
        if ("SET".equals(canonical)) return SLOT_SOURCE;
        return SLOT_OUTPUT;
    }

    private static void backfill(Connection connection) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + TABLE
                                + " (home_banking_id,bot_job_id,instruction_id,slot,variable_id,"
                                + "slot_revision,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)");
                PreparedStatement legacy = connection.prepareStatement(
                        "SELECT bj.home_banking_id, i.bot_job_id, i.id, i.actions, i.variable_id"
                                + " FROM instruction i INNER JOIN bot_job bj ON bj.id = i.bot_job_id"
                                + " WHERE i.variable_id IS NOT NULL")) {
            try (ResultSet rows = legacy.executeQuery()) {
                while (rows.next()) {
                    insertSlot(insert, rows.getInt(1), rows.getInt(2), rows.getInt(3),
                            legacySlotFor(rows.getString(4)), rows.getInt(5), now);
                }
            }
            if (tableExists(connection, "instruction_variable_command_config")) {
                try (PreparedStatement shadow = connection.prepareStatement(
                                "SELECT home_banking_id, bot_job_id, instruction_id, operand_variable_id"
                                        + " FROM instruction_variable_command_config"
                                        + " WHERE operand_kind = 'VARIABLE'"
                                        + " AND operand_variable_id IS NOT NULL");
                        ResultSet rows = shadow.executeQuery()) {
                    while (rows.next()) {
                        insertSlot(insert, rows.getInt(1), rows.getInt(2), rows.getInt(3),
                                SLOT_RIGHT, rows.getInt(4), now);
                    }
                }
            }
        }
    }

    private static void insertSlot(
            PreparedStatement insert,
            int homeBankingId,
            int botJobId,
            int instructionId,
            String slot,
            int variableId,
            Timestamp now)
            throws SQLException {
        insert.setInt(1, homeBankingId);
        insert.setInt(2, botJobId);
        insert.setInt(3, instructionId);
        insert.setString(4, slot);
        insert.setInt(5, variableId);
        insert.setTimestamp(6, now);
        insert.setTimestamp(7, now);
        insert.executeUpdate();
    }

    private static boolean isEmpty(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + TABLE);
                ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getInt(1) == 0;
        }
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static boolean indexExists(Connection connection, String name) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(null, null, TABLE, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && name.toLowerCase(Locale.ROOT)
                        .equals(indexName.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
