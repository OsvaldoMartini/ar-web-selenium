//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import com.allinweb.ch.customJmsForEmi.s.ClassB;
import java.io.Serializable;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMIConnectionFactory implements ConnectionFactory, Referenceable, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER;
    private static final ClassB bb;
    private String url;
    private String user;
    private String password;
    private DataSource dataSource;

    public AMIConnectionFactory(
            final String ipAddress,
            final String port,
            final String dbInstance,
            final String user,
            final String password) {
        this(ipAddress, port, dbInstance);
        this.setUser(getB().decrypt(user));
        this.setPassword(getB().decrypt(password));
        if (AMIConnectionFactory.LOGGER.isDebugEnabled()) {
            AMIConnectionFactory.LOGGER.debug(
                    "AMIConnectionFactory ipAddress=" + ipAddress + " dbInstance=" + dbInstance + "  user="
                            + getB().alwaysEncrypted(user) + "  password=" + getB().alwaysEncrypted(password));
        }
    }

    public AMIConnectionFactory(final String ipAddress, final String port, final String dbInstance) {
        this("jdbc:oracle:thin:@//" + ipAddress + ":" + Integer.parseUnsignedInt(port) + "/" + dbInstance);
        if (AMIConnectionFactory.LOGGER.isDebugEnabled()) {
            AMIConnectionFactory.LOGGER.debug(
                    "AMIConnectionFactory ipAddress=" + ipAddress + " dbInstance=" + dbInstance);
        }
    }

    public AMIConnectionFactory(final String url) {
        this.setUrl(getB().decrypt(url));
        if (AMIConnectionFactory.LOGGER.isDebugEnabled()) {
            AMIConnectionFactory.LOGGER.debug("AMIConnectionFactory ipAddress=" + getB().alwaysEncrypted(url));
        }
    }

    public AMIConnectionFactory(final DataSource dataSource) {
        this.setDataSource(dataSource);
        if (AMIConnectionFactory.LOGGER.isDebugEnabled()) {
            AMIConnectionFactory.LOGGER.debug("AMIConnectionFactory setup with data source");
        }
    }

    public AMIConnectionFactory(final DataSource dataSource, final String user, final String password) {
        this.setDataSource(dataSource);
        this.setUser(getB().decrypt(user));
        this.setPassword(getB().decrypt(password));
        if (AMIConnectionFactory.LOGGER.isDebugEnabled()) {
            AMIConnectionFactory.LOGGER.debug("AMIConnectionFactory setup with data source");
        }
    }

    static ClassB getB() {
        return AMIConnectionFactory.bb;
    }

    public Connection createConnection() throws JMSException {
        if (this.dataSource != null) {
            return (Connection) new AMIConnection(this.getDataSource(), this.getUser(), this.getPassword());
        }
        return (Connection) new AMIConnection(this.getUrl(), this.getUser(), this.getPassword());
    }

    public Connection createConnection(final String user, final String password) throws JMSException {
        if (this.dataSource != null) {
            return (Connection) new AMIConnection(this.getDataSource(), user, password);
        }
        return (Connection) new AMIConnection(this.getUrl(), user, password);
    }

    public Reference getReference() {
        if (this.dataSource != null) {
            throw new IllegalStateException(
                    "getReference not supported if AMIConnectionFactory is used with data sources.");
        }
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr("url", this.getUrl()),
                AMIObjectFactory.class.getName(),
                null);
    }

    public String getUser() {
        return this.user;
    }

    public void setUser(final String user) {
        this.user = getB().decrypt(user);
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(final String password) {
        this.password = getB().decrypt(password);
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    public void setDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public JMSContext createContext() throws JMSRuntimeException {
        throw new JMSRuntimeException("AMI does not support contexts");
    }

    public JMSContext createContext(final int sessionMode) throws JMSRuntimeException {
        throw new JMSRuntimeException("AMI does not support contexts");
    }

    public JMSContext createContext(final String userName, final String password) throws JMSRuntimeException {
        throw new JMSRuntimeException("AMI does not support contexts");
    }

    public JMSContext createContext(final String userName, final String password, final int sessionMode)
            throws JMSRuntimeException {
        throw new JMSRuntimeException("AMI does not support contexts");
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) AMIConnectionFactory.class);
        bb = new ClassB();
    }
}
