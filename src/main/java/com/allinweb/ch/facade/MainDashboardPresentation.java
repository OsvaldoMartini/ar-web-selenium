package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;

public interface MainDashboardPresentation {
    void openOrganizations();

    void openNewBotJob();

    void openCloneBotJob(BotJobLoadDTO botJob);

    void openCloneOrganizations();

    void closeCloneJob();

    void closeScanner();

    void closeScannerWebDrivers();

    void openBotJob(BotJobLoadDTO botJob);

    void openConfig();

    void openTemplate();

    void openInfo();

    void exitApplication();

    void launchBotJob(BotJobLoadDTO botJob);
}
