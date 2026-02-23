//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.ServerSession;
import javax.jms.ServerSessionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMIConnectionConsumer implements ConnectionConsumer, Runnable {
    private static final Logger LOGGER;
    private final Destination destination;
    private final ServerSessionPool sessionPool;
    private int maxMessages;

    protected AMIConnectionConsumer(
            final AMIConnection connection,
            final Destination destination,
            final String messageSelector,
            final ServerSessionPool sessionPool,
            final int maxMessages)
            throws JMSException {
        if (sessionPool == null) {
            throw new JMSException("Invalid ServerSessionPool parameter: " + sessionPool);
        }
        if (maxMessages <= 0) {
            throw new JMSException("Invalid maxMessages parameter: " + maxMessages);
        }
        if (messageSelector != null && !messageSelector.equals("")) {
            throw new AMINotImplementedException("message selectors are not implemented");
        }
        AMIDestination.checkDirection(destination, 2);
        this.destination = destination;
        this.sessionPool = sessionPool;
        this.maxMessages = maxMessages;
        new Thread(this).start();
    }

    public ServerSessionPool getServerSessionPool() throws JMSException {
        return this.sessionPool;
    }

    public synchronized void close() throws JMSException {
        if (AMIConnectionConsumer.LOGGER.isDebugEnabled()) {
            AMIConnectionConsumer.LOGGER.debug("close");
        }
        this.maxMessages = 0;
    }

    public void run() {
        while (this.maxMessages != 0) {
            try {
                final ServerSession serverSession = this.sessionPool.getServerSession();
                final AMISession session = (AMISession) serverSession.getSession();
                for (int nofMessages = 0; nofMessages < this.maxMessages; ++nofMessages) {
                    session.pushPendingMessage(session.receive(this.destination, -1L));
                }
                serverSession.start();
            } catch (JMSException e) {
                AMIConnectionConsumer.LOGGER.error("Cannot start session.", (Throwable) e);
            }
        }
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) AMIConnectionConsumer.class);
    }
}
