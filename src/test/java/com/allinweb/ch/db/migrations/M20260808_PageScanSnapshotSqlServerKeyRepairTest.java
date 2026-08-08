package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class M20260808_PageScanSnapshotSqlServerKeyRepairTest {

    @Test
    void repairsOversizedOriginalSqlServerSchemaAndRestoresItsIndexes() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet tableResult = singleTableResult();
        ResultSet primaryKeyResult = primaryKeyResult("pk_page_scan_snapshot");
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getAutoCommit()).thenReturn(true);
        when(statement.executeUpdate(anyString())).thenReturn(1);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> maximumLengthResult(0));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tableResult);
        when(metadata.getColumns(any(), any(), any(), anyString()))
                .thenAnswer(invocation -> oversizedColumnResult());
        when(metadata.getPrimaryKeys(any(), any(), eq("page_scan_snapshot")))
                .thenReturn(primaryKeyResult);

        AtomicInteger indexProbe = new AtomicInteger();
        when(metadata.getIndexInfo(any(), any(), eq("page_scan_snapshot"), eq(false), eq(false)))
                .thenAnswer(invocation -> indexProbe.getAndIncrement() < 2
                        ? existingIndexResult(indexProbe.get() == 1
                                ? "idx_page_scan_snapshot_owner"
                                : "idx_page_scan_snapshot_page")
                        : emptyResult());

        new M20260808_PageScanSnapshotSqlServerKeyRepair().apply(connection, "SQLServer");

        verify(connection).setAutoCommit(false);
        verify(statement).executeUpdate(
                "DROP INDEX [idx_page_scan_snapshot_owner] ON page_scan_snapshot");
        verify(statement).executeUpdate(
                "DROP INDEX [idx_page_scan_snapshot_page] ON page_scan_snapshot");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot DROP CONSTRAINT [pk_page_scan_snapshot]");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot ALTER COLUMN scan_id NVARCHAR(36) NOT NULL");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot ALTER COLUMN page_key NVARCHAR(128) NOT NULL");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot ALTER COLUMN captured_at NVARCHAR(40) NOT NULL");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot ALTER COLUMN manifest_sha256 NVARCHAR(64) NULL");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot ALTER COLUMN status NVARCHAR(16) NOT NULL");
        verify(statement).executeUpdate(
                "ALTER TABLE page_scan_snapshot ADD CONSTRAINT pk_page_scan_snapshot PRIMARY KEY (scan_id)");
        verify(statement).executeUpdate(
                "CREATE INDEX idx_page_scan_snapshot_owner"
                        + " ON page_scan_snapshot (home_banking_id, bot_job_id, captured_at)");
        verify(statement).executeUpdate(
                "CREATE INDEX idx_page_scan_snapshot_page"
                        + " ON page_scan_snapshot (home_banking_id, bot_job_id, page_key)");
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
        verify(connection, never()).rollback();
        verify(metadata, atLeastOnce()).getColumns(any(), any(), any(), anyString());
    }

    @Test
    void skipsNonSqlServerDialectsWithoutTouchingTheConnection() throws Exception {
        Connection connection = mock(Connection.class);

        assertDoesNotThrow(() -> new M20260808_PageScanSnapshotSqlServerKeyRepair()
                .apply(connection, "TEXT"));

        verify(connection, never()).getMetaData();
    }

    private static ResultSet singleTableResult() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getString("TABLE_NAME")).thenReturn("page_scan_snapshot");
        return result;
    }

    private static ResultSet oversizedColumnResult() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getString("TABLE_NAME")).thenReturn("page_scan_snapshot");
        when(result.getInt("COLUMN_SIZE")).thenReturn(4000);
        return result;
    }

    private static ResultSet maximumLengthResult(int maximumLength) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getInt(1)).thenReturn(maximumLength);
        return result;
    }

    private static ResultSet primaryKeyResult(String name) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getString("PK_NAME")).thenReturn(name);
        return result;
    }

    private static ResultSet existingIndexResult(String name) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        when(result.getString("INDEX_NAME")).thenReturn(name);
        return result;
    }

    private static ResultSet emptyResult() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(false);
        return result;
    }
}
