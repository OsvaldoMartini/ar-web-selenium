package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsPersistedState;
import com.allinweb.ch.util.ErrorMessage;
import java.sql.SQLException;

/** Persistence/configuration port used by the React Bot Job Details contract. */
public interface BotJobDetailsDataPort {
    BotJobDetailsPersistedState load(int botJobId) throws SQLException;

    ErrorMessage updateMetadata(int botJobId, int homeUrlId, String name, String description);

    int navigationTimeSeconds();

    boolean licenseActive();
}
