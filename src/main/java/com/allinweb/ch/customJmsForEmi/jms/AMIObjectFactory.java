//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

public class AMIObjectFactory implements ObjectFactory {
    private final String connectionFactoryClassName = "com.avaloq.jms.AMIConnectionFactory";
    private final String queueConnectionFactoryClassName = "com.avaloq.jms.AMIQueueConnectionFactory";
    private final String destinationClassName = "com.avaloq.jms.AMIDestination";
    private final String queueClassName = "com.avaloq.jms.AMIQueue";

    @Override
    public Object getObjectInstance(
            final Object obj, final Name name, final Context nameCtx, final Hashtable environment) {
        final Reference reference = (Reference) obj;
        if (reference.getClassName().equals("com.avaloq.jms.AMIConnectionFactory")) {
            return new AMIConnectionFactory(this.getUrl(reference));
        }
        if (reference.getClassName().equals("com.avaloq.jms.AMIQueueConnectionFactory")) {
            return new AMIQueueConnectionFactory(this.getUrl(reference));
        }
        if (reference.getClassName().equals("com.avaloq.jms.AMIDestination")) {
            return new AMIDestination(this.getName(reference));
        }
        if (reference.getClassName().equals("com.avaloq.jms.AMIQueue")) {
            return new AMIQueue(this.getName(reference));
        }
        return null;
    }

    private String getUrl(final Reference reference) {
        return (String) reference.get("url").getContent();
    }

    private String getName(final Reference reference) {
        return (String) reference.get("name").getContent();
    }
}
