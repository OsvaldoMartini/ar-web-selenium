//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.jms;

import java.util.Enumeration;
import java.util.Vector;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

public class AMIConnectionMetaData implements ConnectionMetaData {
    private final int jmsMajorVersion;
    private final int jmsMinorVersion;
    private final String providerName;
    private final int providerMajorVersion;
    private final int providerMinorVersion;

    protected AMIConnectionMetaData(
            final int jmsMajorVersion,
            final int jmsMinorVersion,
            final String providerName,
            final int providerMajorVersion,
            final int providerMinorVersion) {
        this.jmsMajorVersion = jmsMajorVersion;
        this.jmsMinorVersion = jmsMinorVersion;
        this.providerName = providerName;
        this.providerMajorVersion = providerMajorVersion;
        this.providerMinorVersion = providerMinorVersion;
    }

    public String getJMSVersion() throws JMSException {
        return this.jmsMajorVersion + "." + this.jmsMinorVersion;
    }

    public int getJMSMajorVersion() throws JMSException {
        return this.jmsMajorVersion;
    }

    public int getJMSMinorVersion() throws JMSException {
        return this.jmsMinorVersion;
    }

    public String getJMSProviderName() throws JMSException {
        return this.providerName;
    }

    public String getProviderVersion() throws JMSException {
        return this.providerMajorVersion + "." + this.providerMinorVersion;
    }

    public int getProviderMajorVersion() throws JMSException {
        return this.providerMajorVersion;
    }

    public int getProviderMinorVersion() throws JMSException {
        return this.providerMinorVersion;
    }

    public Enumeration getJMSXPropertyNames() throws JMSException {
        return new Vector().elements();
    }
}
