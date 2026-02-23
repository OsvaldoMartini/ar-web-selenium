//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMIQueue extends AMIDestination implements Queue {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER;

    public AMIQueue(final String name) {
        super(name);
    }

    public String getQueueName() throws JMSException {
        return this.getDestinationName();
    }

    public String toString() {
        try {
            return "Queue: " + this.getQueueName();
        } catch (JMSException e) {
            AMIQueue.LOGGER.warn("Cannot calculate queue name.", (Throwable) e);
            return null;
        }
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) AMIQueue.class);
    }
}
