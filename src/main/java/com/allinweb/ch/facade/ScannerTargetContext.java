package com.allinweb.ch.facade;

import com.allinweb.ch.model.TargetElement;

/**
 * Minimal scanner selection bridge used while AR Web Factory is migrated away
 * from a concrete UI shell. Implementations may update a legacy runtime today or a React-backed
 * session later.
 */
public interface ScannerTargetContext {

    void rememberPreviousXPath(String xpath);

    void applyActionDefaults(TargetElement targetElement);
}
