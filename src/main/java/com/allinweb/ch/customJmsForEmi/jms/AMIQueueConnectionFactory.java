//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.sql.DataSource;

public class AMIQueueConnectionFactory extends AMIConnectionFactory implements QueueConnectionFactory {
    private static final long serialVersionUID = 1L;

    public AMIQueueConnectionFactory(
            final String ipAddress,
            final String port,
            final String dbInstance,
            final String user,
            final String password) {
        super(ipAddress, port, dbInstance, user, password);
    }

    public AMIQueueConnectionFactory(final String ipAddress, final String port, final String dbInstance) {
        super(ipAddress, port, dbInstance);
    }

    public AMIQueueConnectionFactory(final String url) {
        super(url);
    }

    public AMIQueueConnectionFactory(final DataSource dataSource) {
        super(dataSource);
    }

    public QueueConnection createQueueConnection() throws JMSException {
        return this.createQueueConnection(null, null);
    }

    public QueueConnection createQueueConnection(final String userName, final String password) throws JMSException {
        if (this.getDataSource() != null) {
            return (QueueConnection) new AMIQueueConnection(this.getDataSource(), userName, password);
        }
        return (QueueConnection) new AMIQueueConnection(this.getUrl(), userName, password);
    }
}
