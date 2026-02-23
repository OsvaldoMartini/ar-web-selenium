//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.JMSException;

public class AMINotImplementedException extends JMSException {
    private static final long serialVersionUID = 1L;

    protected AMINotImplementedException(final String reason, final String errorCode) {
        super(reason, errorCode);
    }

    protected AMINotImplementedException(final String reason) {
        super(reason);
    }
}
