//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import com.allinweb.ch.customJmsForEmi.abs.ami.intf.DeliveryStatus;
import com.allinweb.ch.customJmsForEmi.s.ClassB;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

public class AMIMessage implements Message {
    private final Hashtable<String, Object> properties;
    private Destination destination;
    private String id;
    private long timestamp;
    private String correlationID;
    private String type;
    private AMISession session;
    private Integer priority;
    private DeliveryStatus status;
    private long expirationTime;
    private boolean redelivered;
    private Destination replyTo;

    protected AMIMessage() {
        this.properties = new Hashtable<String, Object>();
    }

    protected AMISession getSession() {
        return this.session;
    }

    public String getJMSMessageID() throws JMSException {
        return this.id;
    }

    public void setJMSMessageID(final String id) throws JMSException {
        if (id.startsWith("ID:")) {
            this.id = id;
            return;
        }
        throw new JMSException("JMSMessageID " + id + " must start with \"ID:\"");
    }

    public long getJMSTimestamp() throws JMSException {
        return this.timestamp;
    }

    public void setJMSTimestamp(final long timestamp) throws JMSException {
        this.timestamp = timestamp;
    }

    public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
        return this.correlationID.getBytes();
    }

    public void setJMSCorrelationIDAsBytes(final byte[] correlationID) throws JMSException {
        this.correlationID = new String(correlationID);
    }

    public void setJMSCorrelationID(final String correlationID) throws JMSException {
        this.correlationID = correlationID;
    }

    public String getJMSCorrelationID() throws JMSException {
        return this.correlationID;
    }

    public Destination getJMSReplyTo() throws JMSException {
        return this.replyTo;
    }

    public void setJMSReplyTo(final Destination replyTo) throws JMSException {
        this.replyTo = replyTo;
    }

    public Destination getJMSDestination() throws JMSException {
        return this.destination;
    }

    public void setJMSDestination(final Destination destination) throws JMSException {
        this.destination = destination;
    }

    public int getJMSDeliveryMode() throws JMSException {
        return 2;
    }

    public void setJMSDeliveryMode(final int deliveryMode) throws JMSException {
        if (deliveryMode != 1 && deliveryMode != 2) {
            throw new JMSException("Invalid delivery mode: " + deliveryMode);
        }
        if (deliveryMode == 1) {
            throw new AMINotImplementedException("delivery mode only DeliveryMode.PERSISTENT");
        }
    }

    public boolean getJMSRedelivered() throws JMSException {
        return this.redelivered;
    }

    public void setJMSRedelivered(final boolean redelivered) throws JMSException {
        this.redelivered = redelivered;
    }

    public String getJMSType() throws JMSException {
        return this.type;
    }

    public void setJMSType(final String type) throws JMSException {
        this.type = type;
    }

    public long getJMSExpiration() throws JMSException {
        return this.expirationTime;
    }

    public void setJMSExpiration(final long expirationTime) throws JMSException {
        this.expirationTime = expirationTime;
    }

    public int getJMSPriority() throws JMSException {
        return (this.priority == null) ? 5 : this.priority;
    }

    public void setJMSPriority(final int priority) throws JMSException {
        this.priority = priority;
    }

    public void clearProperties() throws JMSException {
        this.properties.clear();
    }

    public boolean propertyExists(final String name) throws JMSException {
        return this.properties.containsKey(name);
    }

    public boolean getBooleanProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Boolean) {
                return (boolean) this.properties.get(name);
            }
        }
        return Boolean.valueOf(null);
    }

    public byte getByteProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Byte) {
                return ((Byte) this.properties.get(name)).byteValue();
            }
        }
        return Byte.valueOf(null);
    }

    public short getShortProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Short) {
                return Short.parseShort(this.properties.get(name).toString());
            }
        }
        return Short.valueOf(null);
    }

    public int getIntProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Integer) {
                return Integer.parseUnsignedInt((String) this.properties.get(name));
            }
        }
        return Integer.valueOf(null);
    }

    public long getLongProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Long) {
                return (long) this.properties.get(name);
            }
        }
        return Long.valueOf(null);
    }

    public float getFloatProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Float) {
                return (float) this.properties.get(name);
            }
        }
        return Float.valueOf(null);
    }

    public double getDoubleProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof Double) {
                return (double) this.properties.get(name);
            }
        }
        return Double.valueOf(null);
    }

    public String getStringProperty(final String name) throws JMSException {
        if (this.propertyExists(name)) {
            if (this.properties.get(name) instanceof String) {
                return String.valueOf(this.properties.get(name));
            }
        }
        return String.valueOf(null);
    }

    public Object getObjectProperty(final String name) throws JMSException {
        return this.properties.get(name);
    }

    public Enumeration<String> getPropertyNames() throws JMSException {
        return this.properties.keys();
    }

    public long getMessageID() throws JMSException {
        final String messageID = this.getJMSMessageID();
        final int idx = messageID.indexOf("#");
        if (idx != -1) {
            return Long.parseLong(messageID.substring(idx + 1));
        }
        throw new JMSException("wrong format");
    }

    public void setSession(final AMISession session) {
        this.session = session;
    }

    public void setBooleanProperty(final String name, final boolean value) throws JMSException {
        this.properties.put(name, Boolean.toString(value));
    }

    public void setByteProperty(final String name, final byte value) throws JMSException {
        this.properties.put(name, Byte.toString(value));
    }

    public void setShortProperty(final String name, final short value) throws JMSException {
        this.properties.put(name, Short.toString(value));
    }

    public void setIntProperty(final String name, final int value) throws JMSException {
        this.properties.put(name, Integer.toString(value));
    }

    public void setLongProperty(final String name, final long value) throws JMSException {
        this.properties.put(name, Long.toString(value));
    }

    public void setFloatProperty(final String name, final float value) throws JMSException {
        this.properties.put(name, Float.toString(value));
    }

    public void setDoubleProperty(final String name, final double value) throws JMSException {
        this.properties.put(name, Double.toString(value));
    }

    public void setStringProperty(final String name, final String value) throws JMSException {
        this.properties.put(name, value);
    }

    public void setObjectProperty(final String name, final Object value) throws JMSException {
        if (value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof String) {
            this.properties.put(name, value);
        }
    }

    public void acknowledge() throws JMSException {
        if (this.session == null) {
            throw new JMSException("Internal Exception; Message.acknowledge() called on a null session.");
        }
        this.session.acknowledge(this, this.status);
    }

    public void setDeliveryStatus(final DeliveryStatus status) {
        this.status = status;
    }

    public void clearBody() throws JMSException {}

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("AMIMessage(");
        final ClassB bb = AMIConnectionFactory.getB();
        try {
            stringBuilder.append("\n---- CORE PROPERTIES ----");
            stringBuilder.append(String.format("\n  %-24s = %s", "MessageID", this.getJMSMessageID()));
            stringBuilder.append(String.format("\n  %-24s = %s", "Timestamp", this.getJMSTimestamp()));
            stringBuilder.append(String.format("\n  %-24s = %s", "CorrelationID", this.getJMSCorrelationID()));
            stringBuilder.append(String.format("\n  %-24s = %s", "DeliveryMode", this.getJMSDeliveryMode()));
            stringBuilder.append(String.format("\n  %-24s = %s", "MessageID", this.getJMSMessageID()));
            stringBuilder.append(String.format("\n  %-24s = %s", "Expiration", this.getJMSExpiration()));
            stringBuilder.append(String.format("\n  %-24s = %s", "Priority", this.getJMSPriority()));
            stringBuilder.append(String.format("\n  %-24s = %s", "Redelivered", this.getJMSRedelivered()));
            stringBuilder.append(String.format("\n  %-24s = %s", "Type", this.getJMSType()));
            stringBuilder.append(String.format(
                    "\n  %-24s = %s", "Destination", AMIDestination.getDestinationName(this.getJMSDestination())));
            stringBuilder.append(String.format(
                    "\n  %-24s = %s", "ReplyTo", AMIDestination.getDestinationName(this.getJMSReplyTo())));
            final boolean logProperties = false;
            if (logProperties) {
                stringBuilder.append("\n---- MESSAGE PROPERTIES ----");
                final Collection<String> propertyNames = Collections.list(this.getPropertyNames());
                for (final String name : propertyNames) {
                    stringBuilder.append(String.format("\n  %-24s = %s", name, this.getStringProperty(name)));
                }
            }
            String messageText = ((TextMessage) this).getText();
            final boolean logMessageText = false;
            if (!logMessageText) {
                messageText = bb.alwaysEncrypted(messageText);
            }
            stringBuilder
                    .append("\n---- MESSAGE TEXT ")
                    .append(logMessageText ? "" : "(encrypted)")
                    .append("----\n")
                    .append(messageText);
            return stringBuilder.toString();
        } catch (JMSException e) {
            return e.getMessage();
        }
    }

    public boolean isBodyAssignableTo(final Class c) throws JMSException {
        throw new AMINotImplementedException("AMI does not support the method isBodyAssignableTo from JMS 2.0 yet");
    }

    public <T> T getBody(final Class<T> c) throws JMSException {
        throw new AMINotImplementedException("AMI does not support the method getBody from JMS 2.0 yet");
    }

    public long getJMSDeliveryTime() throws JMSException {
        throw new AMINotImplementedException("AMI does not support the method getJMSDeliveryTime from JMS 2.0 yet");
    }

    public void setJMSDeliveryTime(final long deliveryTime) throws JMSException {
        throw new AMINotImplementedException("AMI does not support the method setJMSDeliveryTime from JMS 2.0 yet");
    }
}
