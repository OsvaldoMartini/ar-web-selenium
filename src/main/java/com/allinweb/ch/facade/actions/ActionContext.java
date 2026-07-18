package com.allinweb.ch.facade.actions;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARPriorities;
import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Wait;

/**
 * Live view over the mutable execution state owned by the PerformActions facade.
 *
 * <p>The facade implements this interface with one-line reads/writes of its own fields, so the
 * facade's public fields ({@code windowHandlesList}, {@code currentTabIndex}) and statics
 * ({@code waitForPage}, {@code waitForAction}) remain the single source of truth — external
 * mutations stay visible here.
 *
 * <p>Rule for implementors of extracted classes: never cache {@link #driver()} in a field.
 * The facade swaps the current driver during iframe switching, so it must be re-read at
 * every call.
 */
public interface ActionContext {

    WebDriver driver();

    void driver(WebDriver driver);

    ARWebDriver arWebDriver();

    List<String> windowHandles();

    /** Replaces the window-handles list (the facade's public {@code windowHandlesList} field). */
    void windowHandles(List<String> handles);

    int tabIndex();

    void tabIndex(int tabIndex);

    Wait<WebDriver> pageWait();

    Wait<WebDriver> actionWait();

    ARPriorities priorities();

    boolean justCalledRefreshPage();

    void justCalledRefreshPage(boolean value);

    /** Runs the page-refresh callback (plugin re-injection) if one is registered. */
    void notifyPageRefresh();

    /** The actionExecutor plugin re-injection callback; may be null when not registered. */
    /** True when the user requested the running bot job to stop; locate loops must break. */
    boolean isInterceptBotJob();

    /**
     * Pauses execution via the facade's synchronized HOLD ({@code Object.wait} on the singleton
     * monitor). Kept on the facade so the monitor object never changes; pass {@code null} for
     * the default short hold.
     */
    String holdForSeconds(com.allinweb.ch.model.InstructionLoad instruction) throws Exception;
}
