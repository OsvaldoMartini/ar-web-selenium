package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageMappingsOcrAliasServiceTest {

    private static final int HOME_BANKING_ID = 7;
    private static final int BOT_JOB_ID = 42;
    private static final long SCANNED_ELEMENT_ID = 11L;
    private static final String PAGE_KEY = "page-1";
    private static final String ELEMENT_HASH = "a".repeat(64);
    private static final String LAST_SCANNED_AT = "2026-08-07T12:00:00Z";
    private static final String REVIEWED_ALIAS = "Reviewed label";

    @TempDir Path temporaryDirectory;

    @Test
    void commitAcknowledgementFailureDoesNotMutateUnknownTransactionDuringCleanup()
            throws Exception {
        String databaseUrl = createDatabase("commit-outcome-unknown.db");
        CommitOutcomeUnknownConnection seam =
                commitOutcomeUnknownConnection(DriverManager.getConnection(databaseUrl));
        PageMappingsOcrAliasService service = new PageMappingsOcrAliasService(
                seam::connection);

        PageMappingsOcrAliasService.AliasApplyOutcomeUnknownException failure = assertThrows(
                PageMappingsOcrAliasService.AliasApplyOutcomeUnknownException.class,
                () -> service.apply(
                        HOME_BANKING_ID,
                        BOT_JOB_ID,
                        PAGE_KEY,
                        List.of(aliasChange())));

        assertEquals("commit acknowledgement lost", failure.getCause().getMessage());
        assertFalse(seam.rollbackInvoked().get());
        assertFalse(seam.isolationRestoreInvoked().get());
        assertFalse(seam.autoCommitRestoreInvoked().get());
        assertEquals(REVIEWED_ALIAS, storedAlias(databaseUrl));
    }

    @Test
    void closeFailureAfterSuccessfulCommitReturnsOutcomeUnknown() throws Exception {
        String databaseUrl = createDatabase("close-outcome-unknown.db");
        PageMappingsOcrAliasService service = new PageMappingsOcrAliasService(
                () -> closeOutcomeUnknownConnection(DriverManager.getConnection(databaseUrl)));

        PageMappingsOcrAliasService.AliasApplyOutcomeUnknownException failure = assertThrows(
                PageMappingsOcrAliasService.AliasApplyOutcomeUnknownException.class,
                () -> service.apply(
                        HOME_BANKING_ID,
                        BOT_JOB_ID,
                        PAGE_KEY,
                        List.of(aliasChange())));

        assertEquals("close acknowledgement lost", failure.getCause().getMessage());
        assertEquals(REVIEWED_ALIAS, storedAlias(databaseUrl));
    }

    private String createDatabase(String fileName) throws SQLException {
        String databaseUrl = "jdbc:sqlite:"
                + temporaryDirectory.resolve(fileName).toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bot_job ("
                    + "id INTEGER PRIMARY KEY, "
                    + "home_banking_id INTEGER NOT NULL)");
            statement.execute("CREATE TABLE scanned_element ("
                    + "id INTEGER PRIMARY KEY, "
                    + "home_banking_id INTEGER, "
                    + "bot_job_id INTEGER, "
                    + "home_url_id INTEGER, "
                    + "page_url TEXT, "
                    + "page_key TEXT, "
                    + "element_hash TEXT, "
                    + "tag_name TEXT, "
                    + "type_element TEXT, "
                    + "defined_name TEXT, "
                    + "client_named TEXT, "
                    + "some_text TEXT, "
                    + "x_path TEXT, "
                    + "custom_x_path TEXT, "
                    + "css_selector TEXT, "
                    + "attrib_id TEXT, "
                    + "attrib_name TEXT, "
                    + "coordinates TEXT, "
                    + "iframe_xpath TEXT, "
                    + "shadow_host TEXT, "
                    + "shadow_root TEXT, "
                    + "attribute_data TEXT, "
                    + "ocr_text TEXT, "
                    + "ocr_match_quality TEXT, "
                    + "ocr_confidence REAL, "
                    + "scan_count INTEGER, "
                    + "first_scanned_at TEXT, "
                    + "last_scanned_at TEXT)");
            statement.executeUpdate("INSERT INTO bot_job (id, home_banking_id) VALUES ("
                    + BOT_JOB_ID + ", " + HOME_BANKING_ID + ")");
        }
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO scanned_element ("
                                + "id, home_banking_id, bot_job_id, page_url, page_key, "
                                + "element_hash, tag_name, type_element, defined_name, "
                                + "some_text, x_path, scan_count, first_scanned_at, "
                                + "last_scanned_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, SCANNED_ELEMENT_ID);
            statement.setInt(2, HOME_BANKING_ID);
            statement.setInt(3, BOT_JOB_ID);
            statement.setString(4, "https://example.test/page");
            statement.setString(5, PAGE_KEY);
            statement.setString(6, ELEMENT_HASH);
            statement.setString(7, "button");
            statement.setString(8, "BUTTON");
            statement.setString(9, "Continue");
            statement.setString(10, "Continue");
            statement.setString(11, "//button");
            statement.setInt(12, 3);
            statement.setString(13, LAST_SCANNED_AT);
            statement.setString(14, LAST_SCANNED_AT);
            statement.executeUpdate();
        }
        return databaseUrl;
    }

    private static PageMappingsOcrAliasService.AliasChange aliasChange() {
        return new PageMappingsOcrAliasService.AliasChange(
                SCANNED_ELEMENT_ID,
                ELEMENT_HASH,
                LAST_SCANNED_AT,
                3,
                null,
                REVIEWED_ALIAS);
    }

    private static String storedAlias(String databaseUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT client_named FROM scanned_element WHERE id = ?")) {
            statement.setLong(1, SCANNED_ELEMENT_ID);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static CommitOutcomeUnknownConnection commitOutcomeUnknownConnection(
            Connection delegate) {
        AtomicBoolean commitApplied = new AtomicBoolean();
        AtomicBoolean rollbackInvoked = new AtomicBoolean();
        AtomicBoolean isolationRestoreInvoked = new AtomicBoolean();
        AtomicBoolean autoCommitRestoreInvoked = new AtomicBoolean();
        Connection connection = connectionProxy(delegate, (method, arguments) -> {
            String name = method.getName();
            if ("commit".equals(name)) {
                invoke(delegate, method, arguments);
                commitApplied.set(true);
                throw new SQLException("commit acknowledgement lost");
            }
            if (commitApplied.get() && "rollback".equals(name)) {
                rollbackInvoked.set(true);
            }
            if (commitApplied.get() && "setTransactionIsolation".equals(name)) {
                isolationRestoreInvoked.set(true);
            }
            if (commitApplied.get()
                    && "setAutoCommit".equals(name)
                    && Boolean.TRUE.equals(arguments[0])) {
                autoCommitRestoreInvoked.set(true);
            }
            return invoke(delegate, method, arguments);
        });
        return new CommitOutcomeUnknownConnection(
                connection,
                rollbackInvoked,
                isolationRestoreInvoked,
                autoCommitRestoreInvoked);
    }

    private static Connection closeOutcomeUnknownConnection(Connection delegate) {
        return connectionProxy(delegate, (method, arguments) -> {
            Object result = invoke(delegate, method, arguments);
            if ("close".equals(method.getName())) {
                throw new SQLException("close acknowledgement lost");
            }
            return result;
        });
    }

    private static Connection connectionProxy(Connection delegate, ConnectionInvocation invocation) {
        return (Connection) Proxy.newProxyInstance(
                PageMappingsOcrAliasServiceTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> invocation.invoke(method, arguments));
    }

    private static Object invoke(Connection delegate, Method method, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(delegate, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    @FunctionalInterface
    private interface ConnectionInvocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }

    private record CommitOutcomeUnknownConnection(
            Connection connection,
            AtomicBoolean rollbackInvoked,
            AtomicBoolean isolationRestoreInvoked,
            AtomicBoolean autoCommitRestoreInvoked) {}
}
