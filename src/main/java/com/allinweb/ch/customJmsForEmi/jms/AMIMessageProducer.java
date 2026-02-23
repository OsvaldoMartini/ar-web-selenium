//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

public class AMIMessageProducer extends AMIMessenger implements MessageProducer {
    private boolean disableMessageID;
    private boolean disableMessageTimestamp;
    private long timeToLive;
    private int priority;

    protected AMIMessageProducer(final AMISession session, final Destination destination) throws JMSException {
        super(session, destination);
        this.disableMessageTimestamp = false;
        this.timeToLive = 0L;
        AMIDestination.checkDirection(destination, 1);
    }

    public void setDisableMessageID(final boolean value) throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        this.disableMessageID = value;
    }

    public boolean getDisableMessageID() throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        return this.disableMessageID;
    }

    public void setDisableMessageTimestamp(final boolean value) throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        this.disableMessageTimestamp = value;
    }

    public boolean getDisableMessageTimestamp() throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        return this.disableMessageTimestamp;
    }

    public void setDeliveryMode(final int deliveryMode) throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        if (deliveryMode != 1 && deliveryMode != 2) {
            throw new JMSException("Invalid delivery mode");
        }
        if (deliveryMode == 1) {
            throw new AMINotImplementedException("delivery mode only DeliveryMode.PERSISTENT");
        }
    }

    public int getDeliveryMode() throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        return 2;
    }

    public void setPriority(final int priority) throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        if (priority < 0 || priority > 9) {
            throw new JMSException("Invalid priority");
        }
        this.priority = priority;
    }

    public int getPriority() throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        return this.priority;
    }

    public void setTimeToLive(final long timeToLive) throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        this.timeToLive = timeToLive;
    }

    public long getTimeToLive() throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        return this.timeToLive;
    }

    public void send(final Message message) throws JMSException {
        this.send(
                this.getDestination(),
                message,
                message.getJMSDeliveryMode(),
                message.getJMSPriority(),
                this.getTimeToLive());
    }

    public void send(final Message message, final int deliveryMode, final int priority, final long timeToLive)
            throws JMSException {
        if (deliveryMode == 1) {
            throw new AMINotImplementedException("delivery mode only DeliveryMode.PERSISTENT");
        }
        this.send(this.getDestination(), message, deliveryMode, priority, timeToLive);
    }

    public void send(final Destination destination, final Message message) throws JMSException {
        this.send(destination, message, message.getJMSDeliveryMode(), message.getJMSPriority(), this.getTimeToLive());
    }

    public void send(
            final Destination destination,
            final Message message,
            final int deliveryMode,
            final int priority,
            final long timeToLive)
            throws JMSException {
        this.checkClosed("Forbidden call on a closed message producer.");
        if (deliveryMode != 2) {
            throw new AMINotImplementedException("delivery mode only DeliveryMode.PERSISTENT");
        }
        message.setJMSDestination(destination);
        message.setJMSPriority(priority);
        message.setJMSDeliveryMode(deliveryMode);
        if (timeToLive > 0L) {
            message.setJMSExpiration(System.currentTimeMillis() + timeToLive);
        } else {
            message.setJMSExpiration(0L);
        }
        this.getSession().send(message, this.getDisableMessageID(), this.getDisableMessageTimestamp());
    }

    public void send(
            final Destination destination,
            final Message message,
            final int deliveryMode,
            final int priority,
            final long timeToLive,
            final CompletionListener completionListener)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support this specification of the send method");
    }

    public void send(final Destination destination, final Message message, final CompletionListener completionListener)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support this specification of the send method");
    }

    public void send(
            final Message message,
            final int deliveryMode,
            final int priority,
            final long timeToLive,
            final CompletionListener completionListener)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support this specification of the send method");
    }

    public void send(final Message message, final CompletionListener completionListener) throws JMSException {
        throw new AMINotImplementedException("AMI does not support this specification of the send method");
    }

    public long getDeliveryDelay() throws JMSException {
        throw new AMINotImplementedException("AMI does not support the method getDeliveryDelay from JMS 2.0 yet");
    }

    public void setDeliveryDelay(final long deliveryDelay) throws JMSException {
        throw new AMINotImplementedException("AMI does not support the method setDeliveryDelay from JMS 2.0 yet");
    }
}
