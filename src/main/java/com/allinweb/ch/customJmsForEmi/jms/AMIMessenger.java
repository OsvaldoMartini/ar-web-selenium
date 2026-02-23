//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;

public class AMIMessenger {
    private final Destination destination;
    private final AMISession session;
    private boolean closed;

    protected AMIMessenger(final AMISession session, final Destination destination) {
        this.closed = false;
        this.session = session;
        this.destination = destination;
    }

    public void close() throws JMSException {
        this.closed = true;
    }

    public Destination getDestination() throws JMSException {
        return this.destination;
    }

    protected AMISession getSession() {
        return this.session;
    }

    protected void checkClosed(final String message) throws IllegalStateException {
        if (this.closed) {
            throw new IllegalStateException(message);
        }
    }
}
