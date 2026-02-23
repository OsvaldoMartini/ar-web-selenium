//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.JMSException;
import javax.jms.TextMessage;

public class AMITextMessage extends AMIMessage implements TextMessage {
    private String text;

    protected AMITextMessage() {}

    public AMITextMessage(final String text) throws JMSException {
        this();
        this.setText(text);
    }

    public void setText(final String text) throws JMSException {
        if (this.getText() != null) {
            throw new IllegalStateException("message text is already set");
        }
        this.text = text;
    }

    public String getText() throws JMSException {
        return this.text;
    }

    @Override
    public void clearBody() throws JMSException {
        super.clearBody();
        this.text = null;
    }
}
