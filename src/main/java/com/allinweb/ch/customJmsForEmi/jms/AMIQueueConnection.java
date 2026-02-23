//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.sql.DataSource;

public class AMIQueueConnection extends AMIConnection implements QueueConnection {
    protected AMIQueueConnection(final String url, final String userName, final String password) throws JMSException {
        super(url, userName, password);
    }

    protected AMIQueueConnection(final DataSource dataSource, final String userName, final String password)
            throws JMSException {
        super(dataSource, userName, password);
    }

    public synchronized QueueSession createQueueSession(
            final boolean transacted, final int acknowledgeMode, final Integer bu_id) throws JMSException {
        this.checkClosed();
        return (QueueSession) new AMIQueueSession(this, transacted, acknowledgeMode, bu_id);
    }

    @Override
    public QueueSession createQueueSession(boolean b, int i) throws JMSException {
        return null;
    }

    public ConnectionConsumer createConnectionConsumer(
            final Queue queue, final String messageSelector, final ServerSessionPool sessionPool, final int maxMessages)
            throws JMSException {
        return super.createConnectionConsumer((Destination) queue, messageSelector, sessionPool, maxMessages);
    }
}
