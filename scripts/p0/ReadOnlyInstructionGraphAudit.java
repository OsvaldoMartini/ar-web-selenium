import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * P0-only structural audit for an AR Web database.
 *
 * <p>This program executes SELECT and JDBC metadata operations only. For PostgreSQL it also starts
 * an explicitly read-only transaction. It never prints connection URLs, credentials, row IDs,
 * names, selectors, values, or other customer data.
 *
 * <p>Run with Java source-file mode and the JDBC driver on the class path:
 *
 * <pre>
 * java -cp path/to/jdbc-driver.jar scripts/p0/ReadOnlyInstructionGraphAudit.java config/ARWeb.config
 * java -cp path/to/uber-jar scripts/p0/ReadOnlyInstructionGraphAudit.java \
 *     config/ARWeb.config --database-copy target/p0-audit/database.mdb
 * </pre>
 */
public final class ReadOnlyInstructionGraphAudit {
    private static final List<String> TABLES =
            List.of("instruction", "variable", "component_instruction", "component_variable");

    private static final Map<Integer, String> DELETE_RULES = Map.of(
            DatabaseMetaData.importedKeyCascade, "CASCADE",
            DatabaseMetaData.importedKeyRestrict, "RESTRICT",
            DatabaseMetaData.importedKeySetNull, "SET_NULL",
            DatabaseMetaData.importedKeyNoAction, "NO_ACTION",
            DatabaseMetaData.importedKeySetDefault, "SET_DEFAULT");

    private ReadOnlyInstructionGraphAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected ARWeb.config and optional '--database-copy <path>'. "
                            + "No database URL or credential arguments are accepted.");
        }

        Path configPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path databaseCopy = parseDatabaseCopy(args);
        Properties config = loadConfig(configPath);
        String configuredDialect = config.getProperty("data_base", "unknown").trim();
        ConnectionTarget target = connectionTarget(config, configuredDialect, databaseCopy);
        String jdbcUrl = target.jdbcUrl();

        loadDriver(jdbcUrl);
        DriverManager.setLoginTimeout(8);

        Properties connectionProperties = new Properties();
        if (target.kind().startsWith("POSTGRES")) {
            putIfPresent(connectionProperties, "user", config.getProperty("db_user"));
            putIfPresent(connectionProperties, "password", config.getProperty("db_pwd"));
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, connectionProperties)) {
            enableReadOnlyTransaction(connection);
            DatabaseMetaData metadata = connection.getMetaData();

            System.out.println("# P0 read-only instruction graph audit");
            line("config.file", configPath.getFileName().toString());
            line("config.dialect", configuredDialect);
            line("database.targetKind", target.kind());
            line("database.targetFingerprint", target.fingerprint());
            if (databaseCopy != null) {
                line("database.copyFile", databaseCopy.getFileName().toString());
                line("database.copyBytes", Long.toString(Files.size(databaseCopy)));
                line("database.copySha256", sha256(Files.readAllBytes(databaseCopy)));
            }
            line("database.product", metadata.getDatabaseProductName());
            line("database.version", metadata.getDatabaseProductVersion());
            line("driver.name", metadata.getDriverName());
            line("driver.version", metadata.getDriverVersion());
            line("transaction.readOnly", readOnlyState(connection, metadata.getDatabaseProductName()));

            printColumns(metadata);
            printForeignKeys(metadata);
            printMigrationState(connection);
            printMetrics(connection);

            connection.rollback();
            line("transaction.outcome", "ROLLED_BACK");
        }
    }

    private static Path parseDatabaseCopy(String[] args) {
        if (args.length == 1) {
            return null;
        }
        if (!"--database-copy".equals(args[1])) {
            throw new IllegalArgumentException("The only supported optional argument is --database-copy.");
        }
        Path copy = Path.of(args[2]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(copy)) {
            throw new IllegalArgumentException("Database copy does not exist: " + copy);
        }
        return copy;
    }

    private static Properties loadConfig(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Config file does not exist: " + path);
        }
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required config property: " + key);
        }
        return value.trim();
    }

    private static ConnectionTarget connectionTarget(
            Properties config, String configuredDialect, Path databaseCopy) throws Exception {
        String dialect = configuredDialect.toLowerCase(Locale.ROOT);
        if (dialect.equals("postgres") || dialect.equals("postgresql")) {
            if (databaseCopy != null) {
                throw new IllegalArgumentException("--database-copy is not valid for PostgreSQL.");
            }
            String url = required(config, "db_url");
            return new ConnectionTarget("POSTGRES_READ_ONLY_TRANSACTION", url, sha256(url));
        }

        String fileName = dialect.equals("text") ? "database.db" : "database.mdb";
        String configuredPath = Path.of(required(config, "path_db"), fileName)
                .toAbsolutePath()
                .normalize()
                .toString();
        if (databaseCopy == null) {
            throw new IllegalArgumentException(
                    "SQLite and Access audits require --database-copy. "
                            + "The live database is never opened directly.");
        }
        String url = dialect.equals("text")
                ? "jdbc:sqlite:" + databaseCopy
                : "jdbc:ucanaccess://" + databaseCopy;
        String kind = dialect.equals("text") ? "SQLITE_DISPOSABLE_COPY" : "ACCESS_DISPOSABLE_COPY";
        return new ConnectionTarget(kind, url, sha256(configuredPath));
    }

    private static void putIfPresent(Properties target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.setProperty(key, value);
        }
    }

    private static void loadDriver(String url) throws ClassNotFoundException {
        if (url.startsWith("jdbc:postgresql:")) {
            Class.forName("org.postgresql.Driver");
        } else if (url.startsWith("jdbc:sqlite:")) {
            Class.forName("org.sqlite.JDBC");
        } else if (url.startsWith("jdbc:ucanaccess:")) {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        }
    }

    private static void enableReadOnlyTransaction(Connection connection) throws SQLException {
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // Access is audited only through a disposable copy and receives SELECT statements only.
        }
        connection.setAutoCommit(false);
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        } catch (SQLException ignored) {
            // Some Access/SQLite drivers expose only their default isolation. The program still
            // contains no mutating statement.
        }
        if (connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres")) {
            try (Statement statement = statement(connection)) {
                statement.execute("SET TRANSACTION READ ONLY");
            }
        }
    }

    private static String readOnlyState(Connection connection, String productName) throws SQLException {
        if (productName.toLowerCase(Locale.ROOT).contains("postgres")) {
            try (Statement statement = statement(connection);
                    ResultSet result = statement.executeQuery("SHOW transaction_read_only")) {
                return result.next() ? result.getString(1) : "unknown";
            }
        }
        return Boolean.toString(connection.isReadOnly());
    }

    private static void printColumns(DatabaseMetaData metadata) throws SQLException {
        System.out.println();
        System.out.println("## Columns");
        System.out.println("table|column|type|nullable");

        List<ColumnFact> facts = new ArrayList<>();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                String table = lower(columns.getString("TABLE_NAME"));
                String column = lower(columns.getString("COLUMN_NAME"));
                if (!TABLES.contains(table) || !isRelationshipColumn(column)) {
                    continue;
                }
                facts.add(new ColumnFact(
                        table,
                        column,
                        columns.getString("TYPE_NAME"),
                        columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable));
            }
        }

        facts.stream()
                .sorted(Comparator.comparing(ColumnFact::table).thenComparing(ColumnFact::column))
                .forEach(fact -> System.out.printf(
                        "%s|%s|%s|%s%n",
                        fact.table(), fact.column(), safe(fact.type()), fact.nullable()));
    }

    private static boolean isRelationshipColumn(String column) {
        return column.equals("id")
                || column.equals("instruction_id")
                || column.equals("bot_job_id")
                || column.equals("home_banking_id")
                || column.equals("block_id")
                || column.equals("parent_id")
                || column.equals("parent_block_id")
                || column.equals("variable_id")
                || column.equals("instruction_order_number")
                || column.equals("active");
    }

    private static void printForeignKeys(DatabaseMetaData metadata) throws SQLException {
        System.out.println();
        System.out.println("## Imported foreign keys");
        System.out.println("table.column|target|delete");

        List<ForeignKeyFact> facts = new ArrayList<>();
        for (String table : TABLES) {
            try (ResultSet keys = metadata.getImportedKeys(null, null, table)) {
                while (keys.next()) {
                    facts.add(new ForeignKeyFact(
                            lower(keys.getString("FKTABLE_NAME")),
                            lower(keys.getString("FKCOLUMN_NAME")),
                            lower(keys.getString("PKTABLE_NAME")),
                            lower(keys.getString("PKCOLUMN_NAME")),
                            DELETE_RULES.getOrDefault(keys.getInt("DELETE_RULE"), "UNKNOWN")));
                }
            }
        }

        facts.stream()
                .sorted(Comparator.comparing(ForeignKeyFact::table).thenComparing(ForeignKeyFact::column))
                .forEach(fact -> System.out.printf(
                        "%s.%s|%s.%s|%s%n",
                        fact.table(), fact.column(), fact.targetTable(), fact.targetColumn(), fact.deleteRule()));
    }

    private static void printMigrationState(Connection connection) {
        System.out.println();
        System.out.println("## Migration state");
        metric(connection, "schema_migrations.rows", "SELECT COUNT(*) FROM schema_migrations");
        try (Statement statement = statement(connection);
                ResultSet columns = statement.executeQuery("SELECT * FROM schema_migrations WHERE 1 = 0")) {
            List<String> names = new ArrayList<>();
            for (int index = 1; index <= columns.getMetaData().getColumnCount(); index++) {
                names.add(lower(columns.getMetaData().getColumnName(index)));
            }
            line("schema_migrations.columns", String.join(",", names));
            String keyColumn = names.contains("name") ? "name" : (names.contains("version") ? "version" : null);
            if (keyColumn != null) {
                List<String> applied = new ArrayList<>();
                try (Statement namesStatement = statement(connection);
                        ResultSet rows =
                                namesStatement.executeQuery("SELECT " + keyColumn + " FROM schema_migrations")) {
                    while (rows.next()) {
                        String value = rows.getString(1);
                        if (value != null && !value.isBlank()) {
                            applied.add(value.trim());
                        }
                    }
                }
                applied.sort(String::compareTo);
                line("schema_migrations.applied", String.join(",", applied));
                line(
                        "schema_migrations.latest",
                        applied.isEmpty() ? "NONE" : applied.get(applied.size() - 1));
            }
        } catch (SQLException error) {
            line("schema_migrations.columns", "UNAVAILABLE:" + sqlState(error));
        }
    }

    private static void printMetrics(Connection connection) {
        System.out.println();
        System.out.println("## Sanitized structural metrics");

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("bot.instructions", "SELECT COUNT(*) FROM instruction");
        metrics.put("bot.variables", "SELECT COUNT(*) FROM variable");
        metrics.put(
                "bot.variableLinkedInstructions",
                "SELECT COUNT(*) FROM instruction WHERE variable_id IS NOT NULL");
        metrics.put(
                "bot.ownerlessVariables",
                "SELECT COUNT(*) FROM variable WHERE instruction_id IS NULL");
        metrics.put(
                "bot.variablesMissingOwner",
                "SELECT COUNT(*) FROM variable v LEFT JOIN instruction i ON i.id = v.instruction_id "
                        + "WHERE v.instruction_id IS NOT NULL AND i.id IS NULL");
        metrics.put(
                "bot.instructionsMissingVariable",
                "SELECT COUNT(*) FROM instruction i LEFT JOIN variable v ON v.id = i.variable_id "
                        + "WHERE i.variable_id IS NOT NULL AND v.id IS NULL");
        metrics.put(
                "bot.instructionsMissingParent",
                "SELECT COUNT(*) FROM instruction c LEFT JOIN instruction p ON p.id = c.parent_id "
                        + "WHERE c.parent_id IS NOT NULL AND p.id IS NULL");
        metrics.put(
                "bot.crossOwnerParents",
                "SELECT COUNT(*) FROM instruction c JOIN instruction p ON p.id = c.parent_id "
                        + "WHERE (c.bot_job_id <> p.bot_job_id) "
                        + "OR (c.bot_job_id IS NULL AND p.bot_job_id IS NOT NULL) "
                        + "OR (c.bot_job_id IS NOT NULL AND p.bot_job_id IS NULL)");
        metrics.put(
                "bot.parentBlockMissing",
                "SELECT COUNT(*) FROM instruction WHERE parent_id IS NOT NULL AND parent_block_id IS NULL");
        metrics.put(
                "bot.duplicateVariableOwners",
                "SELECT COALESCE(SUM(d.duplicate_count - 1), 0) FROM "
                        + "(SELECT instruction_id, COUNT(*) duplicate_count FROM variable "
                        + "WHERE instruction_id IS NOT NULL GROUP BY instruction_id HAVING COUNT(*) > 1) d");

        metrics.put("component.instructions", "SELECT COUNT(*) FROM component_instruction");
        metrics.put("component.variables", "SELECT COUNT(*) FROM component_variable");
        metrics.put(
                "component.variableLinkedInstructions",
                "SELECT COUNT(*) FROM component_instruction WHERE variable_id IS NOT NULL");
        metrics.put(
                "component.ownerlessVariables",
                "SELECT COUNT(*) FROM component_variable WHERE instruction_id IS NULL");
        metrics.put(
                "component.variablesMissingOwner",
                "SELECT COUNT(*) FROM component_variable v "
                        + "LEFT JOIN component_instruction i ON i.id = v.instruction_id "
                        + "WHERE v.instruction_id IS NOT NULL AND i.id IS NULL");
        metrics.put(
                "component.instructionsMissingVariable",
                "SELECT COUNT(*) FROM component_instruction i "
                        + "LEFT JOIN component_variable v ON v.id = i.variable_id "
                        + "WHERE i.variable_id IS NOT NULL AND v.id IS NULL");
        metrics.put(
                "component.instructionsMissingParent",
                "SELECT COUNT(*) FROM component_instruction c "
                        + "LEFT JOIN component_instruction p ON p.id = c.parent_id "
                        + "WHERE c.parent_id IS NOT NULL AND p.id IS NULL");
        metrics.put(
                "component.crossOwnerParents",
                "SELECT COUNT(*) FROM component_instruction c "
                        + "JOIN component_instruction p ON p.id = c.parent_id "
                        + "WHERE (c.home_banking_id <> p.home_banking_id) "
                        + "OR (c.home_banking_id IS NULL AND p.home_banking_id IS NOT NULL) "
                        + "OR (c.home_banking_id IS NOT NULL AND p.home_banking_id IS NULL)");
        metrics.put(
                "component.parentBlockMissing",
                "SELECT COUNT(*) FROM component_instruction "
                        + "WHERE parent_id IS NOT NULL AND parent_block_id IS NULL");
        metrics.put(
                "component.duplicateVariableOwners",
                "SELECT COALESCE(SUM(d.duplicate_count - 1), 0) FROM "
                        + "(SELECT instruction_id, COUNT(*) duplicate_count FROM component_variable "
                        + "WHERE instruction_id IS NOT NULL GROUP BY instruction_id HAVING COUNT(*) > 1) d");

        metrics.forEach((name, sql) -> metric(connection, name, sql));
    }

    private static void metric(Connection connection, String name, String sql) {
        try (Statement statement = statement(connection);
                ResultSet result = statement.executeQuery(sql)) {
            line(name, result.next() ? result.getString(1) : "NO_ROW");
        } catch (SQLException error) {
            line(name, "UNAVAILABLE:" + sqlState(error));
        }
    }

    private static Statement statement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(20);
        return statement;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('|', '_').replace('\n', '_').replace('\r', '_');
    }

    private static String sha256(String value) throws Exception {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private static String sqlState(SQLException error) {
        String state = error.getSQLState();
        return state == null || state.isBlank() ? error.getClass().getSimpleName() : state;
    }

    private static void line(String name, String value) {
        System.out.println(name + "=" + safe(value));
    }

    private record ColumnFact(String table, String column, String type, boolean nullable) {}

    private record ForeignKeyFact(
            String table, String column, String targetTable, String targetColumn, String deleteRule) {}

    private record ConnectionTarget(String kind, String jdbcUrl, String fingerprint) {}
}
