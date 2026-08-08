package com.allinweb.ch.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.MigrationRunner.Migration;
import com.allinweb.ch.db.migrations.M20260807_PageScanSnapshot;
import com.allinweb.ch.db.migrations.M20260808_PageScanSnapshotSqlServerKeyRepair;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationRunnerPageScanSnapshotRegistrationTest {

    @Test
    void registersUniqueFollowUpImmediatelyAfterOriginalSnapshotMigration() throws Exception {
        List<Migration> migrations = registeredMigrations();
        List<String> names = migrations.stream().map(Migration::name).toList();
        String originalName = new M20260807_PageScanSnapshot().name();
        String repairName = new M20260808_PageScanSnapshotSqlServerKeyRepair().name();

        assertEquals(names.size(), new HashSet<>(names).size(), "migration names must remain unique");
        assertTrue(names.contains(originalName));
        assertTrue(names.contains(repairName));
        assertEquals(names.indexOf(originalName) + 1, names.indexOf(repairName));
        assertEquals(repairName, names.get(names.size() - 1), "new migrations must only be appended");
    }

    @Test
    void originalMigrationRecordDoesNotSuppressTheFollowUpRepair() throws Exception {
        List<String> registeredNames = registeredMigrations().stream()
                .map(Migration::name)
                .toList();
        String originalName = new M20260807_PageScanSnapshot().name();
        String repairName = new M20260808_PageScanSnapshotSqlServerKeyRepair().name();
        Set<String> alreadyApplied = Set.of(originalName);
        List<String> pending = registeredNames.stream()
                .filter(name -> !alreadyApplied.contains(name))
                .toList();

        assertFalse(pending.contains(originalName));
        assertTrue(pending.contains(repairName));
    }

    @SuppressWarnings("unchecked")
    private static List<Migration> registeredMigrations() throws Exception {
        Field field = MigrationRunner.class.getDeclaredField("MIGRATIONS");
        field.setAccessible(true);
        return (List<Migration>) field.get(null);
    }
}
