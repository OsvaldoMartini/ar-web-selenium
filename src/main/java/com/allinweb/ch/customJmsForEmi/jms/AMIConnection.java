//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import com.allinweb.ch.customJmsForEmi.jms.db.AMIMessageInterface;
import com.allinweb.ch.customJmsForEmi.s.ClassB;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import javax.jms.*;
import javax.sql.DataSource;
import oracle.jdbc.driver.OracleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMIConnection implements Connection {
    private static final Logger LOGGER;
    ClassB bb;
    public final int SQL_CONNECTION_POOL_SIZE = 20;
    private final Properties connectionProperties;
    private String clientID;
    private ExceptionListener exceptionListener;
    private boolean closed;
    private boolean started;
    private Collection<AMISession> sessions;
    private List<AMIMessageInterface> messageInterfaces;
    private AMIConnectionMetaData metaData;
    private String url;
    private DataSource dataSource;

    protected AMIConnection(final String url, final Properties connectionProperties) throws JMSException {
        this.bb = AMIConnectionFactory.getB();
        this.url = url;
        this.connectionProperties = connectionProperties;
        this.init();
    }

    protected AMIConnection(final String url, final String user, final String password) throws JMSException {
        this.bb = AMIConnectionFactory.getB();
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("AMIConnection url=" + this.bb.alwaysEncrypted(url) + " user="
                    + this.bb.alwaysEncrypted(user) + " password=" + this.bb.alwaysEncrypted(password));
        }
        this.url = url;
        this.connectionProperties = new Properties();
        if (user != null && password != null) {
            this.connectionProperties.put("user", user);
            this.connectionProperties.put("password", password);
        }
        this.connectionProperties.put("v$session.program", "JMSforAvaloq");
        this.init();
    }

    protected AMIConnection(final DataSource dataSource, final String user, final String password) throws JMSException {
        this.bb = AMIConnectionFactory.getB();
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("AMIConnection with dataSource=" + dataSource.toString());
        }
        this.dataSource = dataSource;
        this.connectionProperties = new Properties();
        if (user != null && password != null) {
            this.connectionProperties.put("user", user);
            this.connectionProperties.put("password", password);
        }
        this.init();
    }

    private void init() throws JMSException {
        this.sessions = new Vector<AMISession>();
        this.messageInterfaces = new Vector<AMIMessageInterface>();
        this.metaData = new AMIConnectionMetaData(1, 1, "EMI (Avaloq External Message Interface)", 1, 0);
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            final JMSException jmsE = new JMSException(e.getMessage());
            jmsE.setLinkedException((Exception) e);
            throw jmsE;
        }
    }

    public synchronized AMISession createSession(final boolean transacted, final int acknowledgeMode, Integer bu_id)
            throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug(
                    "createSession transacted=" + transacted + " acknowledgeMode=" + acknowledgeMode);
        }
        this.checkClosed();
        return new AMISession(this, transacted, acknowledgeMode, bu_id);
    }

    public synchronized String getClientID() throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("getClientID");
        }
        this.checkClosed();
        return this.clientID;
    }

    public synchronized void setClientID(final String clientID) throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("setClientID clientID=" + clientID);
        }
        this.checkClosed();
        if (this.isStarted()) {
            throw new java.lang.IllegalStateException("set client id before start");
        }
        this.clientID = clientID;
    }

    public ConnectionMetaData getMetaData() throws JMSException {
        return (ConnectionMetaData) this.metaData;
    }

    protected synchronized JMSException notifyExceptionListener(final JMSException exception) throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("notifyExceptionListener exception=" + exception);
        }
        final ExceptionListener exceptionListener = this.getExceptionListener();
        if (exceptionListener != null) {
            exceptionListener.onException(exception);
        }
        return exception;
    }

    public synchronized ExceptionListener getExceptionListener() throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("getExceptionListener");
        }
        this.checkClosed();
        return this.exceptionListener;
    }

    public synchronized void setExceptionListener(final ExceptionListener listener) throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("setExceptionListener listener=" + listener);
        }
        this.checkClosed();
        this.exceptionListener = listener;
    }

    public synchronized void start() throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("start");
        }
        this.checkClosed();
        if (!this.isStarted()) {
            this.started = true;
            final Iterator<AMISession> sessions = this.sessions.iterator();
            while (sessions.hasNext()) {
                sessions.next().start();
            }
        }
    }

    public synchronized void stop() throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("stop");
        }
        this.checkClosed();
        if (this.isStarted()) {
            this.started = false;
            final Iterator<AMISession> sessions = this.sessions.iterator();
            while (sessions.hasNext()) {
                sessions.next().stop();
            }
        }
    }

    public synchronized void close() throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("close");
        }
        Collection<AMISession> sessionsToClose = null;
        synchronized (this) {
            if (!this.isClosed()) {
                this.closed = true;
                for (final AMIMessageInterface messageInterface : this.messageInterfaces) {
                    try {
                        messageInterface.close();
                    } catch (SQLException e) {
                        throw new AMIJMSException(e, "close");
                    }
                }
                sessionsToClose = this.sessions;
            }
        }
        if (sessionsToClose != null) {
            for (final AMISession session : sessionsToClose) {
                session.close();
            }
        }
    }

    public synchronized ConnectionConsumer createConnectionConsumer(
            final Destination destination,
            final String messageSelector,
            final ServerSessionPool sessionPool,
            final int maxMessages)
            throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("createConnectionConsumer destination=" + destination + " messageSelector="
                    + messageSelector + " sessionPool=" + sessionPool + " maxMessages=" + maxMessages);
        }
        this.checkClosed();
        return (ConnectionConsumer)
                new AMIConnectionConsumer(this, destination, messageSelector, sessionPool, maxMessages);
    }

    public synchronized ConnectionConsumer createDurableConnectionConsumer(
            final Topic topic,
            final String subscriptionName,
            final String messageSelector,
            final ServerSessionPool sessionPool,
            final int maxMessages)
            throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("createDurableConnectionConsumer topic=" + topic + " subscriptionName="
                    + subscriptionName + " messageSelector=" + messageSelector + " sessionPool=" + sessionPool
                    + " maxMessages=" + maxMessages);
        }
        this.checkClosed();
        throw new AMINotImplementedException("publish/subscribe model is not implemented");
    }

    public boolean isStarted() {
        return this.started;
    }

    protected boolean isClosed() {
        return this.closed;
    }

    protected void checkClosed() {
        if (this.isClosed()) {
            throw new java.lang.IllegalStateException("Forbidden call on a closed connection.");
        }
    }

    protected synchronized void registerSession(final AMISession session) {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("registerSession session=" + session);
        }
        this.sessions.add(session);
    }

    protected synchronized void deregisterSession(final AMISession session) throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("deregisterSession session=" + session);
        }
        this.sessions.remove(session);
        final AMIMessageInterface messageInterface = session.getMessageInterface();
        if (this.messageInterfaces.contains(messageInterface)) {
            throw new JMSException("Internal error: messageInterface is already in pool");
        }
        Label_0117:
        {
            if (this.messageInterfaces.size() < 20) {
                if (!this.isClosed()) {
                    break Label_0117;
                }
            }

            try {
                messageInterface.close();
                return;

            } catch (SQLException e) {
                throw new AMIJMSException(e, "close");

            } finally {

                try {
                    messageInterface.rollback();
                } catch (SQLException e) {
                    throw new AMIJMSException(e, "rollback");
                }
            }
        }
        this.messageInterfaces.add(messageInterface);
    }

    protected synchronized AMIMessageInterface createMessageInterface(final boolean transacted, Integer bu_id)
            throws JMSException {
        if (AMIConnection.LOGGER.isDebugEnabled()) {
            AMIConnection.LOGGER.debug("createMessageInterface transacted=" + transacted);
        }
        this.checkClosed();
        if (this.messageInterfaces.size() > 0) {
            final AMIMessageInterface messageInterface =
                    this.messageInterfaces.remove(this.messageInterfaces.size() - 1);
            try {
                messageInterface.checkValid();
            } catch (SQLException e) {
                AMIConnection.LOGGER.error("Cannot use message interface.", (Throwable) e);
                throw this.notifyExceptionListener(new AMIJMSException(e, "check valid"));
            }
            try {
                messageInterface.setTransacted(transacted);
            } catch (SQLException e) {
                AMIConnection.LOGGER.error("Cannot use message interface.", (Throwable) e);
                throw this.notifyExceptionListener(new AMIJMSException(e, "set transacted"));
            }
            return messageInterface;
        }
        try {
            java.sql.Connection connection;
            if (this.dataSource != null) {
                if (this.connectionProperties.getProperty("user") != null) {
                    connection = this.dataSource.getConnection(
                            this.connectionProperties.getProperty("user"),
                            this.connectionProperties.getProperty("password"));
                } else {
                    connection = this.dataSource.getConnection();
                }
                if (!connection.isWrapperFor(OracleConnection.class)) {
                    throw new IllegalArgumentException("Not an OracleConnection.");
                }
            } else {
                connection = DriverManager.getConnection(this.url, this.connectionProperties);
            }
            return new AMIMessageInterface(connection, transacted, bu_id);
        } catch (SQLException e2) {
            final String connectionSource =
                    (this.dataSource != null) ? "for data source." : ("for URL: " + this.bb.alwaysEncrypted(this.url));
            AMIConnection.LOGGER.error("Cannot create connection " + connectionSource, (Throwable) e2);
            throw this.notifyExceptionListener(new AMIJMSException(e2, "create connection " + connectionSource));
        }
    }

    public ConnectionConsumer createSharedDurableConnectionConsumer(
            final Topic topic,
            final String subscriptionName,
            final String messageSelector,
            final ServerSessionPool sessionPool,
            final int maxMessages)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support shared durable connection consumers");
    }

    public ConnectionConsumer createSharedConnectionConsumer(
            final Topic topic,
            final String subscriptionName,
            final String messageSelector,
            final ServerSessionPool sessionPool,
            final int maxMessages)
            throws JMSException {
        throw new AMINotImplementedException("AMI does not support durable connection consumers");
    }

    public AMISession createSession() throws JMSException {
        throw new AMINotImplementedException("AMI does not support this specification of createSession");
    }

    @Override
    public Session createSession(boolean b, int i) throws JMSException {
        return null;
    }

    public AMISession createSession(final int sessionMode) throws JMSException {
        throw new AMINotImplementedException("CUSTOM AMI does not support this specification of createSession");
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) AMIConnection.class);
    }
}
