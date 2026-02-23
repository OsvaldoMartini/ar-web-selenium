//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;

public class AMIQueueReceiver extends AMIMessageConsumer implements QueueReceiver {
    protected AMIQueueReceiver(final AMIQueueSession queueSession, final Queue queue, final String messageSelector)
            throws JMSException {
        super(queueSession, (Destination) queue, messageSelector);
    }

    public Queue getQueue() throws JMSException {
        return (Queue) this.getDestination();
    }
}
