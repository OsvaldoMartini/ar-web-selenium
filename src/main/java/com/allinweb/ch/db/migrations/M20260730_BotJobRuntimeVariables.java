package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Cuts Bot Job variables over to durable definition and runtime-memory tables.
 *
 * <p>The legacy {@code variable} table is retained as a rollback/migration source. Runtime code
 * must use the new tables after cutover; in particular {@code variable.value} is not a runtime
 * value authority.
 *
 * <p>Legacy databases may contain multiple variable rows for the same producer instruction. The
 * lowest legacy variable ID is retained, every {@code instruction.variable_id} reference to a
 * duplicate is remapped first, and the complete old-to-retained mapping is recorded in
 * {@value #MIGRATION_NOTE_TABLE}. Variables without a producer ({@code instruction_id IS NULL})
 * remain independent definitions and are never deduplicated.
 */
@Slf4j
public final class M20260730_BotJobRuntimeVariables implements Migration {

    public static final String DEFINITION_TABLE = "bot_job_variable_definition";
    public static final String MEMORY_TABLE = "bot_job_runtime_memory";
    public static final String VALUE_TABLE = "bot_job_runtime_variable_value";
    public static final String MIGRATION_NOTE_TABLE = "bot_job_variable_migration_note";

    private static final String NAME = "2026-07-30__bot_job_runtime_variables";
    private static final Set<String> DEFINITION_COLUMNS = Set.of(
            "home_banking_id",
            "bot_job_id",
            "id",
            "variable_type",
            "name",
            "configured_value",
            "local_format",
            "delimiter",
            "producer_instruction_id",
            "created_at",
            "updated_at");
    private static final Set<String> MEMORY_COLUMNS = Set.of(
            "home_banking_id",
            "bot_job_id",
            "runtime_revision",
            "reset_generation",
            "next_variable_id",
            "created_at",
            "updated_at");
    private static final Set<String> VALUE_COLUMNS = Set.of(
            "home_banking_id",
            "bot_job_id",
            "variable_id",
            "value_state",
            "raw_value",
            "void_reason",
            "value_source",
            "entry_revision",
            "last_execution_id",
            "updated_at");
    private static final Set<String> NOTE_COLUMNS = Set.of(
            "home_banking_id",
            "bot_job_id",
            "legacy_variable_id",
            "retained_legacy_variable_id",
            "definition_id",
            "producer_instruction_id",
            "note_type",
            "created_at");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        requireSourceTables(connection);
        createTables(connection, dialect);
        verifyShape(connection);
        migrateLegacyRows(connection);
        createIndexes(connection, dialect);
        verifyMigratedOwners(connection);
        log.info("{} - durable Bot Job variable definitions and runtime memory are ready", NAME);
    }

    private static void createTables(Connection connection, String dialect) throws SQLException {
        if (!tableExists(connection, MEMORY_TABLE)) {
            execute(connection, createMemoryTableSql(dialect));
        }
        if (!tableExists(connection, DEFINITION_TABLE)) {
            execute(connection, createDefinitionTableSql(dialect));
        }
        if (!tableExists(connection, VALUE_TABLE)) {
            execute(connection, createValueTableSql(dialect));
        }
        if (!tableExists(connection, MIGRATION_NOTE_TABLE)) {
            execute(connection, createMigrationNoteTableSql(dialect));
        }
    }

    static String createMemoryTableSql(String dialect) {
        return switch (dialect) {
            case "Postgres" -> "CREATE TABLE " + MEMORY_TABLE + " ("
                    + "home_banking_id BIGINT NOT NULL,"
                    + "bot_job_id BIGINT NOT NULL,"
                    + "runtime_revision BIGINT NOT NULL,"
                    + "reset_generation BIGINT NOT NULL,"
                    + "next_variable_id BIGINT NOT NULL,"
                    + "created_at TIMESTAMP NOT NULL,"
                    + "updated_at TIMESTAMP NOT NULL,"
                    + "CONSTRAINT pk_bot_job_runtime_memory PRIMARY KEY"
                    + " (home_banking_id, bot_job_id),"
                    + "CONSTRAINT fk_runtime_memory_bot_job FOREIGN KEY (bot_job_id)"
                    + " REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
            case "SQLServer" -> "CREATE TABLE " + MEMORY_TABLE + " ("
                    + "home_banking_id BIGINT NOT NULL,"
                    + "bot_job_id BIGINT NOT NULL,"
                    + "runtime_revision BIGINT NOT NULL,"
                    + "reset_generation BIGINT NOT NULL,"
                    + "next_variable_id BIGINT NOT NULL,"
                    + "created_at DATETIME2 NOT NULL,"
                    + "updated_at DATETIME2 NOT NULL,"
                    + "CONSTRAINT pk_bot_job_runtime_memory PRIMARY KEY"
                    + " (home_banking_id, bot_job_id),"
                    + "CONSTRAINT fk_runtime_memory_bot_job FOREIGN KEY (bot_job_id)"
                    + " REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
            case "TEXT" -> "CREATE TABLE " + MEMORY_TABLE + " ("
                    + "home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,"
                    + "runtime_revision INTEGER NOT NULL,"
                    + "reset_generation INTEGER NOT NULL,"
                    + "next_variable_id INTEGER NOT NULL,"
                    + "created_at TEXT NOT NULL,"
                    + "updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY (home_banking_id, bot_job_id),"
                    + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
            default -> "CREATE TABLE " + MEMORY_TABLE + " ("
                    + "home_banking_id LONG NOT NULL,"
                    + "bot_job_id LONG NOT NULL,"
                    + "runtime_revision LONG NOT NULL,"
                    + "reset_generation LONG NOT NULL,"
                    + "next_variable_id LONG NOT NULL,"
                    + "created_at DATETIME NOT NULL,"
                    + "updated_at DATETIME NOT NULL,"
                    + "CONSTRAINT pk_bot_job_runtime_memory PRIMARY KEY"
                    + " (home_banking_id, bot_job_id),"
                    + "CONSTRAINT fk_runtime_memory_bot_job FOREIGN KEY (bot_job_id)"
                    + " REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
        };
    }

    static String createDefinitionTableSql(String dialect) {
        return switch (dialect) {
            case "Postgres" -> "CREATE TABLE " + DEFINITION_TABLE + " ("
                    + "home_banking_id BIGINT NOT NULL,"
                    + "bot_job_id BIGINT NOT NULL,"
                    + "id BIGINT NOT NULL,"
                    + "variable_type TEXT,"
                    + "name TEXT NOT NULL,"
                    + "configured_value TEXT,"
                    + "local_format TEXT,"
                    + "delimiter TEXT,"
                    + "producer_instruction_id BIGINT,"
                    + "created_at TIMESTAMP NOT NULL,"
                    + "updated_at TIMESTAMP NOT NULL,"
                    + "CONSTRAINT pk_bot_job_variable_definition PRIMARY KEY"
                    + " (home_banking_id, bot_job_id, id),"
                    + "CONSTRAINT fk_variable_definition_bot_job FOREIGN KEY (bot_job_id)"
                    + " REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
            case "SQLServer" -> "CREATE TABLE " + DEFINITION_TABLE + " ("
                    + "home_banking_id BIGINT NOT NULL,"
                    + "bot_job_id BIGINT NOT NULL,"
                    + "id BIGINT NOT NULL,"
                    + "variable_type NVARCHAR(255),"
                    + "name NVARCHAR(1024) NOT NULL,"
                    + "configured_value NVARCHAR(MAX),"
                    + "local_format NVARCHAR(1024),"
                    + "delimiter NVARCHAR(255),"
                    + "producer_instruction_id BIGINT,"
                    + "created_at DATETIME2 NOT NULL,"
                    + "updated_at DATETIME2 NOT NULL,"
                    + "CONSTRAINT pk_bot_job_variable_definition PRIMARY KEY"
                    + " (home_banking_id, bot_job_id, id),"
                    + "CONSTRAINT fk_variable_definition_bot_job FOREIGN KEY (bot_job_id)"
                    + " REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
            case "TEXT" -> "CREATE TABLE " + DEFINITION_TABLE + " ("
                    + "home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,"
                    + "id INTEGER NOT NULL,"
                    + "variable_type TEXT,"
                    + "name TEXT NOT NULL,"
                    + "configured_value TEXT,"
                    + "local_format TEXT,"
                    + "delimiter TEXT,"
                    + "producer_instruction_id INTEGER,"
                    + "created_at TEXT NOT NULL,"
                    + "updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY (home_banking_id, bot_job_id, id),"
                    + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
            default -> "CREATE TABLE " + DEFINITION_TABLE + " ("
                    + "home_banking_id LONG NOT NULL,"
                    + "bot_job_id LONG NOT NULL,"
                    + "id LONG NOT NULL,"
                    + "variable_type VARCHAR(255),"
                    + "name VARCHAR(255) NOT NULL,"
                    + "configured_value MEMO,"
                    + "local_format VARCHAR(255),"
                    + "delimiter VARCHAR(255),"
                    + "producer_instruction_id LONG,"
                    + "created_at DATETIME NOT NULL,"
                    + "updated_at DATETIME NOT NULL,"
                    + "CONSTRAINT pk_bot_job_variable_definition PRIMARY KEY"
                    + " (home_banking_id, bot_job_id, id),"
                    + "CONSTRAINT fk_variable_definition_bot_job FOREIGN KEY (bot_job_id)"
                    + " REFERENCES bot_job(id) ON DELETE CASCADE"
                    + ")";
        };
    }

    static String createValueTableSql(String dialect) {
        return switch (dialect) {
            case "Postgres" -> "CREATE TABLE " + VALUE_TABLE + " ("
                    + "home_banking_id BIGINT NOT NULL,"
                    + "bot_job_id BIGINT NOT NULL,"
                    + "variable_id BIGINT NOT NULL,"
                    + "value_state VARCHAR(8) NOT NULL,"
                    + "raw_value TEXT,"
                    + "void_reason VARCHAR(64),"
                    + "value_source VARCHAR(32) NOT NULL,"
                    + "entry_revision BIGINT NOT NULL,"
                    + "last_execution_id BIGINT,"
                    + "updated_at TIMESTAMP NOT NULL,"
                    + "CONSTRAINT pk_bot_job_runtime_variable_value PRIMARY KEY"
                    + " (home_banking_id, bot_job_id, variable_id),"
                    + "CONSTRAINT fk_runtime_value_definition FOREIGN KEY"
                    + " (home_banking_id, bot_job_id, variable_id) REFERENCES "
                    + DEFINITION_TABLE
                    + " (home_banking_id, bot_job_id, id) ON DELETE CASCADE"
                    + ")";
            case "SQLServer" -> "CREATE TABLE " + VALUE_TABLE + " ("
                    + "home_banking_id BIGINT NOT NULL,"
                    + "bot_job_id BIGINT NOT NULL,"
                    + "variable_id BIGINT NOT NULL,"
                    + "value_state NVARCHAR(8) NOT NULL,"
                    + "raw_value NVARCHAR(MAX),"
                    + "void_reason NVARCHAR(64),"
                    + "value_source NVARCHAR(32) NOT NULL,"
                    + "entry_revision BIGINT NOT NULL,"
                    + "last_execution_id BIGINT,"
                    + "updated_at DATETIME2 NOT NULL,"
                    + "CONSTRAINT pk_bot_job_runtime_variable_value PRIMARY KEY"
                    + " (home_banking_id, bot_job_id, variable_id),"
                    + "CONSTRAINT fk_runtime_value_definition FOREIGN KEY"
                    + " (home_banking_id, bot_job_id, variable_id) REFERENCES "
                    + DEFINITION_TABLE
                    + " (home_banking_id, bot_job_id, id) ON DELETE CASCADE"
                    + ")";
            case "TEXT" -> "CREATE TABLE " + VALUE_TABLE + " ("
                    + "home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,"
                    + "variable_id INTEGER NOT NULL,"
                    + "value_state TEXT NOT NULL,"
                    + "raw_value TEXT,"
                    + "void_reason TEXT,"
                    + "value_source TEXT NOT NULL,"
                    + "entry_revision INTEGER NOT NULL,"
                    + "last_execution_id INTEGER,"
                    + "updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY (home_banking_id, bot_job_id, variable_id),"
                    + "FOREIGN KEY (home_banking_id, bot_job_id, variable_id) REFERENCES "
                    + DEFINITION_TABLE
                    + " (home_banking_id, bot_job_id, id) ON DELETE CASCADE"
                    + ")";
            default -> "CREATE TABLE " + VALUE_TABLE + " ("
                    + "home_banking_id LONG NOT NULL,"
                    + "bot_job_id LONG NOT NULL,"
                    + "variable_id LONG NOT NULL,"
                    + "value_state VARCHAR(8) NOT NULL,"
                    + "raw_value MEMO,"
                    + "void_reason VARCHAR(64),"
                    + "value_source VARCHAR(32) NOT NULL,"
                    + "entry_revision LONG NOT NULL,"
                    + "last_execution_id LONG,"
                    + "updated_at DATETIME NOT NULL,"
                    + "CONSTRAINT pk_bot_job_runtime_variable_value PRIMARY KEY"
                    + " (home_banking_id, bot_job_id, variable_id),"
                    + "CONSTRAINT fk_runtime_value_definition FOREIGN KEY"
                    + " (home_banking_id, bot_job_id, variable_id) REFERENCES "
                    + DEFINITION_TABLE
                    + " (home_banking_id, bot_job_id, id) ON DELETE CASCADE"
                    + ")";
        };
    }

    static String createMigrationNoteTableSql(String dialect) {
        return switch (dialect) {
            case "Postgres" -> noteTableSql("BIGINT", "VARCHAR(32)", "TIMESTAMP");
            case "SQLServer" -> noteTableSql("BIGINT", "NVARCHAR(32)", "DATETIME2");
            case "TEXT" -> noteTableSql("INTEGER", "TEXT", "TEXT");
            default -> noteTableSql("LONG", "VARCHAR(32)", "DATETIME");
        };
    }

    private static String noteTableSql(String integerType, String textType, String timestampType) {
        return "CREATE TABLE " + MIGRATION_NOTE_TABLE + " ("
                + "home_banking_id " + integerType + " NOT NULL,"
                + "bot_job_id " + integerType + " NOT NULL,"
                + "legacy_variable_id " + integerType + " NOT NULL,"
                + "retained_legacy_variable_id " + integerType + " NOT NULL,"
                + "definition_id " + integerType + " NOT NULL,"
                + "producer_instruction_id " + integerType + ","
                + "note_type " + textType + " NOT NULL,"
                + "created_at " + timestampType + " NOT NULL,"
                + "CONSTRAINT pk_bot_job_variable_migration_note PRIMARY KEY"
                + " (home_banking_id, bot_job_id, legacy_variable_id)"
                + ")";
    }

    private static void migrateLegacyRows(Connection connection) throws SQLException {
        List<LegacyVariable> legacyRows = loadLegacyRows(connection);
        if (legacyRows.isEmpty()) {
            return;
        }

        Map<ProducerKey, LegacyVariable> retainedByProducer = new HashMap<>();
        Map<Long, LegacyVariable> retainedByLegacyId = new LinkedHashMap<>();
        Map<Long, Long> retainedIdByLegacyId = new LinkedHashMap<>();
        for (LegacyVariable legacy : legacyRows) {
            LegacyVariable retained = legacy;
            if (legacy.instructionId() != null) {
                ProducerKey key = new ProducerKey(legacy.botJobId(), legacy.instructionId());
                retained = retainedByProducer.computeIfAbsent(key, ignored -> legacy);
            }
            retainedByLegacyId.putIfAbsent(retained.id(), retained);
            retainedIdByLegacyId.put(legacy.id(), retained.id());
        }

        remapDuplicateInstructionLinks(connection, legacyRows, retainedIdByLegacyId);

        Timestamp now = Timestamp.from(Instant.now());
        Map<OwnerKey, Long> nextIds = new LinkedHashMap<>();
        // Never reuse an archived duplicate's legacy ID in the new definition table. Keeping the
        // allocator above every migrated legacy ID makes migration notes and rollback diagnostics
        // unambiguous.
        for (LegacyVariable legacy : legacyRows) {
            nextIds.merge(legacy.owner(), legacy.id() + 1L, Math::max);
        }
        for (LegacyVariable retained : retainedByLegacyId.values()) {
            OwnerKey owner = retained.owner();
            insertMemoryIfMissing(connection, owner, nextIds.get(owner), now);
            insertDefinitionIfMissing(connection, retained, now);
            insertRuntimeValueIfMissing(connection, retained, now);
        }
        for (Map.Entry<OwnerKey, Long> entry : nextIds.entrySet()) {
            advanceNextVariableId(connection, entry.getKey(), entry.getValue(), now);
        }
        for (LegacyVariable legacy : legacyRows) {
            long retainedId = retainedIdByLegacyId.get(legacy.id());
            insertMigrationNoteIfMissing(
                    connection,
                    legacy,
                    retainedId,
                    legacy.id() == retainedId ? "RETAINED" : "DUPLICATE_MERGED",
                    now);
        }
    }

    private static List<LegacyVariable> loadLegacyRows(Connection connection) throws SQLException {
        String sql = "SELECT v.id, v.type, v.name, v.value, v.local_format, v.delimiter,"
                + " v.instruction_id, v.bot_job_id, b.home_banking_id"
                + " FROM variable v INNER JOIN bot_job b ON b.id = v.bot_job_id"
                + " WHERE v.bot_job_id IS NOT NULL AND b.home_banking_id IS NOT NULL"
                + " ORDER BY v.bot_job_id, v.instruction_id, v.id";
        List<LegacyVariable> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                rows.add(new LegacyVariable(
                        result.getLong("id"),
                        result.getInt("home_banking_id"),
                        result.getInt("bot_job_id"),
                        nullableLong(result, "instruction_id"),
                        result.getString("type"),
                        defaultName(result.getString("name"), result.getLong("id")),
                        result.getString("value"),
                        result.getString("local_format"),
                        result.getString("delimiter")));
            }
        }
        return rows;
    }

    private static void remapDuplicateInstructionLinks(
            Connection connection,
            List<LegacyVariable> legacyRows,
            Map<Long, Long> retainedIdByLegacyId)
            throws SQLException {
        String sql = "UPDATE instruction SET variable_id = ?"
                + " WHERE bot_job_id = ? AND variable_id = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            for (LegacyVariable legacy : legacyRows) {
                long retainedId = retainedIdByLegacyId.get(legacy.id());
                if (retainedId == legacy.id()) {
                    continue;
                }
                update.setLong(1, retainedId);
                update.setInt(2, legacy.botJobId());
                update.setLong(3, legacy.id());
                int remapped = update.executeUpdate();
                log.info(
                        "{} - duplicate legacy variable {} mapped to {} for Bot Job {}; remapped {} instruction row(s)",
                        NAME,
                        legacy.id(),
                        retainedId,
                        legacy.botJobId(),
                        remapped);
            }
        }
    }

    private static void insertMemoryIfMissing(
            Connection connection,
            OwnerKey owner,
            long nextVariableId,
            Timestamp now)
            throws SQLException {
        if (memoryExists(connection, owner)) {
            return;
        }
        String sql = "INSERT INTO " + MEMORY_TABLE
                + " (home_banking_id, bot_job_id, runtime_revision, reset_generation,"
                + " next_variable_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setInt(1, owner.homeBankingId());
            insert.setInt(2, owner.botJobId());
            insert.setLong(3, 0L);
            insert.setLong(4, 0L);
            insert.setLong(5, Math.max(1L, nextVariableId));
            insert.setTimestamp(6, now);
            insert.setTimestamp(7, now);
            insert.executeUpdate();
        }
    }

    private static void insertDefinitionIfMissing(
            Connection connection,
            LegacyVariable legacy,
            Timestamp now)
            throws SQLException {
        if (definitionExists(connection, legacy.owner(), legacy.id())) {
            return;
        }
        String sql = "INSERT INTO " + DEFINITION_TABLE
                + " (home_banking_id, bot_job_id, id, variable_type, name, configured_value,"
                + " local_format, delimiter, producer_instruction_id, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setInt(1, legacy.homeBankingId());
            insert.setInt(2, legacy.botJobId());
            insert.setLong(3, legacy.id());
            insert.setString(4, legacy.type());
            insert.setString(5, legacy.name());
            insert.setString(6, legacy.value());
            insert.setString(7, legacy.localFormat());
            insert.setString(8, legacy.delimiter());
            setNullableLong(insert, 9, legacy.instructionId());
            insert.setTimestamp(10, now);
            insert.setTimestamp(11, now);
            insert.executeUpdate();
        }
    }

    private static void insertRuntimeValueIfMissing(
            Connection connection,
            LegacyVariable legacy,
            Timestamp now)
            throws SQLException {
        if (runtimeValueExists(connection, legacy.owner(), legacy.id())) {
            return;
        }
        String sql = "INSERT INTO " + VALUE_TABLE
                + " (home_banking_id, bot_job_id, variable_id, value_state, raw_value,"
                + " void_reason, value_source, entry_revision, last_execution_id, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setInt(1, legacy.homeBankingId());
            insert.setInt(2, legacy.botJobId());
            insert.setLong(3, legacy.id());
            insert.setString(4, "VOID");
            insert.setString(5, null);
            insert.setString(6, "NO_PRODUCER_YET");
            insert.setString(7, "MIGRATION");
            insert.setLong(8, 0L);
            insert.setObject(9, null);
            insert.setTimestamp(10, now);
            insert.executeUpdate();
        }
    }

    private static void insertMigrationNoteIfMissing(
            Connection connection,
            LegacyVariable legacy,
            long retainedId,
            String noteType,
            Timestamp now)
            throws SQLException {
        if (migrationNoteExists(connection, legacy.owner(), legacy.id())) {
            return;
        }
        String sql = "INSERT INTO " + MIGRATION_NOTE_TABLE
                + " (home_banking_id, bot_job_id, legacy_variable_id,"
                + " retained_legacy_variable_id, definition_id, producer_instruction_id,"
                + " note_type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setInt(1, legacy.homeBankingId());
            insert.setInt(2, legacy.botJobId());
            insert.setLong(3, legacy.id());
            insert.setLong(4, retainedId);
            insert.setLong(5, retainedId);
            setNullableLong(insert, 6, legacy.instructionId());
            insert.setString(7, noteType);
            insert.setTimestamp(8, now);
            insert.executeUpdate();
        }
    }

    private static void advanceNextVariableId(
            Connection connection,
            OwnerKey owner,
            long minimumNextId,
            Timestamp now)
            throws SQLException {
        String sql = "UPDATE " + MEMORY_TABLE
                + " SET next_variable_id = ?, updated_at = ?"
                + " WHERE home_banking_id = ? AND bot_job_id = ?"
                + " AND next_variable_id < ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setLong(1, minimumNextId);
            update.setTimestamp(2, now);
            update.setInt(3, owner.homeBankingId());
            update.setInt(4, owner.botJobId());
            update.setLong(5, minimumNextId);
            update.executeUpdate();
        }
    }

    private static boolean memoryExists(Connection connection, OwnerKey owner)
            throws SQLException {
        return rowExists(
                connection,
                "SELECT 1 FROM " + MEMORY_TABLE
                        + " WHERE home_banking_id = ? AND bot_job_id = ?",
                owner,
                null);
    }

    private static boolean definitionExists(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        return rowExists(
                connection,
                "SELECT 1 FROM " + DEFINITION_TABLE
                        + " WHERE home_banking_id = ? AND bot_job_id = ? AND id = ?",
                owner,
                variableId);
    }

    private static boolean runtimeValueExists(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        return rowExists(
                connection,
                "SELECT 1 FROM " + VALUE_TABLE
                        + " WHERE home_banking_id = ? AND bot_job_id = ? AND variable_id = ?",
                owner,
                variableId);
    }

    private static boolean migrationNoteExists(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        return rowExists(
                connection,
                "SELECT 1 FROM " + MIGRATION_NOTE_TABLE
                        + " WHERE home_banking_id = ? AND bot_job_id = ?"
                        + " AND legacy_variable_id = ?",
                owner,
                variableId);
    }

    private static boolean rowExists(
            Connection connection,
            String sql,
            OwnerKey owner,
            Long variableId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, owner.homeBankingId());
            select.setInt(2, owner.botJobId());
            if (variableId != null) {
                select.setLong(3, variableId);
            }
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void createIndexes(Connection connection, String dialect) throws SQLException {
        createIndexIfMissing(
                connection,
                DEFINITION_TABLE,
                "idx_bot_job_variable_producer",
                "CREATE INDEX idx_bot_job_variable_producer ON "
                        + DEFINITION_TABLE
                        + " (home_banking_id, bot_job_id, producer_instruction_id)");
        createIndexIfMissing(
                connection,
                VALUE_TABLE,
                "idx_bot_job_runtime_value_state",
                "CREATE INDEX idx_bot_job_runtime_value_state ON "
                        + VALUE_TABLE
                        + " (home_banking_id, bot_job_id, value_state)");
        if (!"Access".equalsIgnoreCase(dialect)) {
            createIndexIfMissing(
                    connection,
                    DEFINITION_TABLE,
                    "uq_bot_job_variable_producer",
                    "CREATE UNIQUE INDEX uq_bot_job_variable_producer ON "
                            + DEFINITION_TABLE
                            + " (home_banking_id, bot_job_id, producer_instruction_id)"
                            + " WHERE producer_instruction_id IS NOT NULL");
        }
    }

    private static void createIndexIfMissing(
            Connection connection,
            String table,
            String index,
            String sql)
            throws SQLException {
        if (!indexExists(connection, table, index)) {
            execute(connection, sql);
        }
    }

    private static void verifyMigratedOwners(Connection connection) throws SQLException {
        String missingDefinition = "SELECT COUNT(*) FROM variable v"
                + " INNER JOIN bot_job b ON b.id = v.bot_job_id"
                + " LEFT JOIN " + MIGRATION_NOTE_TABLE + " n"
                + " ON n.home_banking_id = b.home_banking_id"
                + " AND n.bot_job_id = v.bot_job_id AND n.legacy_variable_id = v.id"
                + " WHERE v.bot_job_id IS NOT NULL AND b.home_banking_id IS NOT NULL"
                + " AND n.legacy_variable_id IS NULL";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(missingDefinition)) {
            if (!rows.next() || rows.getLong(1) != 0L) {
                throw new SQLException(NAME + " did not record every migrated legacy variable");
            }
        }

        String invalidValue = "SELECT COUNT(*) FROM " + VALUE_TABLE
                + " WHERE (value_state = 'VALUE' AND (raw_value IS NULL OR void_reason IS NOT NULL))"
                + " OR (value_state = 'VOID' AND (raw_value IS NOT NULL OR void_reason IS NULL))"
                + " OR (value_state <> 'VALUE' AND value_state <> 'VOID')";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(invalidValue)) {
            if (!rows.next() || rows.getLong(1) != 0L) {
                throw new SQLException(NAME + " produced an invalid VALUE/VOID runtime row");
            }
        }
    }

    private static void requireSourceTables(Connection connection) throws SQLException {
        for (String table : List.of("home_banking", "bot_job", "instruction", "variable")) {
            if (!tableExists(connection, table)) {
                throw new SQLException(table + " must exist before applying " + NAME);
            }
        }
    }

    private static void verifyShape(Connection connection) throws SQLException {
        verifyColumns(connection, MEMORY_TABLE, MEMORY_COLUMNS);
        verifyColumns(connection, DEFINITION_TABLE, DEFINITION_COLUMNS);
        verifyColumns(connection, VALUE_TABLE, VALUE_COLUMNS);
        verifyColumns(connection, MIGRATION_NOTE_TABLE, NOTE_COLUMNS);
    }

    private static void verifyColumns(
            Connection connection,
            String table,
            Set<String> required)
            throws SQLException {
        Set<String> actual = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                    actual.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!actual.containsAll(required)) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(actual);
            throw new SQLException(NAME + " found an incompatible " + table
                    + " table; missing columns " + missing);
        }
    }

    private static boolean tableExists(Connection connection, String tableName)
            throws SQLException {
        try (ResultSet tables = connection.getMetaData()
                .getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean indexExists(
            Connection connection,
            String tableName,
            String indexName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(tableName, tableName.toUpperCase(Locale.ROOT))) {
            try (ResultSet indexes =
                    metadata.getIndexInfo(null, null, candidate, false, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static void setNullableLong(
            PreparedStatement statement,
            int parameter,
            Long value)
            throws SQLException {
        if (value == null) {
            statement.setObject(parameter, null);
        } else {
            statement.setLong(parameter, value);
        }
    }

    private static String defaultName(String name, long id) {
        return name == null || name.isBlank() ? "Variable " + id : name;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record OwnerKey(int homeBankingId, int botJobId) {}

    private record ProducerKey(int botJobId, long instructionId) {}

    private record LegacyVariable(
            long id,
            int homeBankingId,
            int botJobId,
            Long instructionId,
            String type,
            String name,
            String value,
            String localFormat,
            String delimiter) {
        private LegacyVariable {
            if (id <= 0L || homeBankingId <= 0 || botJobId <= 0) {
                throw new IllegalArgumentException("Migrated variable owner IDs must be positive");
            }
            Objects.requireNonNull(name, "name");
        }

        OwnerKey owner() {
            return new OwnerKey(homeBankingId, botJobId);
        }
    }
}
