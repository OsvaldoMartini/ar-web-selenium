package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;

public interface MainDashboardPresentation {
    void openOrganizations();

    void openNewBotJob();

    void openCloneBotJob(BotJobLoadDTO botJob);

    void openBotJob(BotJobLoadDTO botJob);

    void openConfig();

    void openInfo();

    void exitApplication();

    void launchBotJob(BotJobLoadDTO botJob);
}
