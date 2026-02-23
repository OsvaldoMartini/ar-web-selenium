//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

public class AMIMessageConsumer extends AMIMessenger implements MessageConsumer {
    private MessageListener messageListener;

    protected AMIMessageConsumer(final AMISession session, final Destination destination, final String messageSelector)
            throws JMSException {
        super(session, destination);
        AMIDestination.checkDirection(destination, 2);
        if (messageSelector != null && !messageSelector.equals("")) {
            throw new AMINotImplementedException("message selectors are not implemented");
        }
    }

    public String getMessageSelector() throws JMSException {
        this.checkClosed("Forbidden call on a closed message consumer.");
        throw new AMINotImplementedException("message selectors are not implemented");
    }

    public MessageListener getMessageListener() throws JMSException {
        this.checkClosed("Forbidden call on a closed message consumer.");
        return this.messageListener;
    }

    public void setMessageListener(final MessageListener listener) throws JMSException {
        this.checkClosed("Forbidden call on a closed message consumer.");
        this.getSession().removeMessageListener(this.messageListener, this.getDestination());
        this.messageListener = listener;
        this.getSession().registerMessageListener(this.messageListener, this.getDestination());
    }

    public Message receive() throws JMSException {
        this.checkClosed("Forbidden call on a closed message consumer.");
        return this.getSession().receive(this.getDestination(), -1L);
    }

    public Message receive(final long timeout) throws JMSException {
        this.checkClosed("Forbidden call on a closed message consumer.");
        return this.getSession().receive(this.getDestination(), (timeout > 0L) ? timeout : -1L);
    }

    public Message receiveNoWait() throws JMSException {
        this.checkClosed("Forbidden call on a closed message consumer.");
        return this.getSession().receive(this.getDestination(), 0L);
    }
}
