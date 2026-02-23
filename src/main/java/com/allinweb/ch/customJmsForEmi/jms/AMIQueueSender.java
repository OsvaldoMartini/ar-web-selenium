//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueSender;

public class AMIQueueSender extends AMIMessageProducer implements QueueSender {
    protected AMIQueueSender(final AMIQueueSession queueSession, final Queue queue) throws JMSException {
        super(queueSession, (Destination) queue);
    }

    public Queue getQueue() throws JMSException {
        return (Queue) this.getDestination();
    }

    public void send(final Queue queue, final Message message) throws JMSException {
        super.send((Destination) queue, message);
    }

    public void send(
            final Queue queue, final Message message, final int deliveryMode, final int priority, final long timeToLive)
            throws JMSException {
        super.send((Destination) queue, message, deliveryMode, priority, timeToLive);
    }
}
