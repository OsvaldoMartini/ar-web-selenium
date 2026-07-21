package com.allinweb.ch.facade.actions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class EngineDialogsTest {

    @Test
    void quitDelegatesBrowserCloseToRunOwnedPolicyInsteadOfClosingDriverDirectly() {
        ActionContext context = mock(ActionContext.class);

        new EngineDialogs(context).quit(1);

        verify(context).closeBrowserForExecutionAction();
        verify(context, never()).arWebDriver();
    }
}
