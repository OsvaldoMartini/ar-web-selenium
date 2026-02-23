//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import java.sql.SQLException;
import javax.jms.JMSException;

public class AMIJMSException extends JMSException {
    private static final long serialVersionUID = 1L;

    public AMIJMSException(final SQLException sqlException, final String info) {
        super(
                "SQL exception occurred in " + info + ": " + sqlException.getMessage(),
                Integer.toString(sqlException.getErrorCode()));
        this.setLinkedException((Exception) sqlException);
    }
}
