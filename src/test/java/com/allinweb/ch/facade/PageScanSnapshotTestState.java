package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Restores the process-wide snapshot configuration and health generation after an isolated test. */
public final class PageScanSnapshotTestState implements AutoCloseable {

    private static final String[] MANAGED_PROPERTIES = {
        ARPropertyEnum.PATH_DB.getValue(),
        ARPropertyEnum.PAGE_SCAN_RETENTION_DAYS.getValue(),
        ARPropertyEnum.PAGE_SCAN_RETENTION_MAX_UNPINNED_PER_PAGE.getValue()
    };

    private final ARPropertyManager manager = ARPropertyManager.getInstance();
    private final Map<String, String> previousProperties = new LinkedHashMap<>();
    private final String previousConfigurationFileName;
    private final Field initializedPathDb;
    private final Field unavailableReason;
    private final Path previousInitializedPathDb;
    private final String previousUnavailableReason;

    private PageScanSnapshotTestState(Path pathDb) throws ReflectiveOperationException {
        Properties properties = manager.getProperties();
        for (String property : MANAGED_PROPERTIES) {
            previousProperties.put(property, properties.getProperty(property));
        }
        previousConfigurationFileName = manager.getConfigurationFileName();

        initializedPathDb = healthField("initializedPathDb");
        unavailableReason = healthField("unavailableReason");
        previousInitializedPathDb = (Path) initializedPathDb.get(null);
        previousUnavailableReason = (String) unavailableReason.get(null);

        initializedPathDb.set(null, null);
        unavailableReason.set(null, "");
        properties.setProperty(
                ARPropertyEnum.PATH_DB.getValue(), pathDb.toAbsolutePath().normalize().toString());
        setPolicy(0, 0);
    }

    public static PageScanSnapshotTestState isolate(Path pathDb)
            throws ReflectiveOperationException {
        return new PageScanSnapshotTestState(pathDb);
    }

    public void setPolicy(int retentionDays, int maxUnpinnedPerPage) {
        Properties properties = manager.getProperties();
        properties.setProperty(
                ARPropertyEnum.PAGE_SCAN_RETENTION_DAYS.getValue(),
                Integer.toString(retentionDays));
        properties.setProperty(
                ARPropertyEnum.PAGE_SCAN_RETENTION_MAX_UNPINNED_PER_PAGE.getValue(),
                Integer.toString(maxUnpinnedPerPage));
    }

    public void setPathDb(Path pathDb) {
        manager.getProperties().setProperty(
                ARPropertyEnum.PATH_DB.getValue(), pathDb.toAbsolutePath().normalize().toString());
    }

    public void clearPathDb() {
        manager.getProperties().remove(ARPropertyEnum.PATH_DB.getValue());
    }

    public void clearConfigurationFile() {
        manager.setConfigurationFileName(null);
    }

    @Override
    public void close() throws IllegalAccessException {
        Properties properties = manager.getProperties();
        for (Map.Entry<String, String> entry : previousProperties.entrySet()) {
            if (entry.getValue() == null) properties.remove(entry.getKey());
            else properties.setProperty(entry.getKey(), entry.getValue());
        }
        manager.setConfigurationFileName(previousConfigurationFileName);
        initializedPathDb.set(null, previousInitializedPathDb);
        unavailableReason.set(null, previousUnavailableReason);
    }

    private static Field healthField(String name) throws ReflectiveOperationException {
        Field field = PageScanSnapshotStorageHealth.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
