//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import java.io.Serializable;
import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

public class AMIDestination implements Destination, Referenceable, Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private static final char SEPERATOR = '/';
    public static final int IN = 1;
    public static final int OUT = 2;

    public AMIDestination(final String name) {
        this.name = name;
    }

    protected static String getDestinationName(final Destination destination) throws JMSException {
        if (destination instanceof Queue) {
            return ((Queue) destination).getQueueName();
        }
        if (destination instanceof Topic) {
            return ((Topic) destination).getTopicName();
        }
        return null;
    }

    protected static String getNetworkName(final Destination destination) throws JMSException {
        final String destinationName = getDestinationName(destination);
        int endIndex = destinationName.length();
        if (destinationName.endsWith(".IN")) {
            endIndex -= 3;
        } else if (destinationName.endsWith(".OUT")) {
            endIndex -= 4;
        }
        return destinationName.substring(destinationName.lastIndexOf(47) + 1, endIndex);
    }

    protected static String getDBInstanceName(final Destination destination) throws JMSException {
        final String destinationName = getDestinationName(destination);
        final int endIndex = destinationName.lastIndexOf(47);
        if (endIndex < 0) {
            return null;
        }
        return destinationName.substring(0, endIndex);
    }

    protected static void checkDirection(final Destination destination, final int direction)
            throws InvalidDestinationException, JMSException {
        if (direction != 1 && direction != 2) {
            throw new JMSException("invalid direction");
        }
        if (direction == 1 && !getDestinationName(destination).endsWith(".IN")) {
            throw new InvalidDestinationException("destination name must end with \".IN\"");
        }
        if (direction == 2 && !getDestinationName(destination).endsWith(".OUT")) {
            throw new InvalidDestinationException("destination name must end with \".OUT\"");
        }
    }

    protected String getDestinationName() {
        return this.name;
    }

    public Reference getReference() {
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr("name", this.getDestinationName()),
                "com.avaloq.jms.AMIObjectFactory",
                null);
    }
}
