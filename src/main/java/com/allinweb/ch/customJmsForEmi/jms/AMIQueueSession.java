//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;

public class AMIQueueSession extends AMISession implements QueueSession {
    protected AMIQueueSession(
            final AMIQueueConnection queueConnection,
            final boolean transacted,
            final int acknowledgeMode,
            final Integer bu_id)
            throws JMSException {
        super(queueConnection, transacted, acknowledgeMode, bu_id);
    }

    public QueueReceiver createReceiver(final Queue queue) throws JMSException {
        return this.createReceiver(queue, null);
    }

    public QueueReceiver createReceiver(final Queue queue, final String messageSelector) throws JMSException {
        this.checkClosed();
        return (QueueReceiver) new AMIQueueReceiver(this, queue, messageSelector);
    }

    public QueueSender createSender(final Queue queue) throws JMSException {
        this.checkClosed();
        return (QueueSender) new AMIQueueSender(this, queue);
    }
}
