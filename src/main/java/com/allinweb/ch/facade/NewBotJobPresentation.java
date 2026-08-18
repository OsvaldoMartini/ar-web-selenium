package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;

public interface NewBotJobPresentation {
    void openOrganizations();

    void openBotJobAndClose(BotJobLoadDTO botJob);

    void closeModal();
}
