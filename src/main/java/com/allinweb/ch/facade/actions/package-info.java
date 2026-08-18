/**
 * Internal decomposition of {@link com.allinweb.ch.facade.PerformActions}.
 *
 * <p>External code must go through the {@code PerformActions} facade — the classes in this
 * package are implementation details extracted from the former god class and their API may
 * change without notice. The facade keeps the stable public surface (methods, public fields
 * {@code windowHandlesList}/{@code currentTabIndex}, statics {@code waitForPage}/{@code
 * waitForAction}) and delegates here.
 */
package com.allinweb.ch.facade.actions;
