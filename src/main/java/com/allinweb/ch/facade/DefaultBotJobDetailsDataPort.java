package com.allinweb.ch.facade;

import com.allinweb.ch.db.BotJobDetailsRepository;
import com.allinweb.ch.model.BotJobDetailsPersistedState;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import java.sql.SQLException;

final class DefaultBotJobDetailsDataPort implements BotJobDetailsDataPort {

    private final PerformDataBase database = PerformDataBase.getInstance();
    private final BotJobDetailsRepository repository = new BotJobDetailsRepository(database);
    private final ARPropertyManager properties = ARPropertyManager.getInstance();

    @Override
    public BotJobDetailsPersistedState load(int botJobId) throws SQLException {
        return repository.load(botJobId);
    }

    @Override
    public ErrorMessage updateMetadata(int botJobId, int homeUrlId, String name, String description) {
        return database.updateBotJobDetails(botJobId, homeUrlId, name, description);
    }

    @Override
    public int navigationTimeSeconds() {
        try {
            String value = properties.getProperty(ARPropertyEnum.NAVIGATION_TIME);
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Override
    public boolean licenseActive() {
        return LicenseService.getInstance().isActive();
    }
}
