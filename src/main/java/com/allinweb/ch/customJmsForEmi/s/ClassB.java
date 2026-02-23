//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.s;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.PBEConfig;
import org.jasypt.properties.PropertyValueEncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClassB implements StringEncryptor {
    private static final Logger LOGGER;
    private final StandardPBEStringEncryptor e;
    PropertyValueEncryptionUtils a;

    public ClassB() {
        final ClassA a = new ClassA();
        (this.e = new StandardPBEStringEncryptor()).setConfig((PBEConfig) a);
    }

    public String alwaysEncrypted(final String value) {
        if (value == null) {
            return null;
        }
        if (isEncryptedValue(value)) {
            return value;
        }
        return this.encrypt(value);
    }

    public String encrypt(final String value) {
        if (value == null) {
            return null;
        }
        return PropertyValueEncryptionUtils.encrypt(value, (StringEncryptor) this.e);
    }

    public String decrypt(final String value) {
        if (value == null) {
            return null;
        }
        try {
            if (isEncryptedValue(value)) {
                return PropertyValueEncryptionUtils.decrypt(value, (StringEncryptor) this.e);
            }
        } catch (Throwable t) {
            if (ClassB.LOGGER.isWarnEnabled()) {
                ClassB.LOGGER.warn("Cannot decrypt, will use value=" + value, t);
            }
        }
        return value;
    }

    public static boolean isEncryptedValue(final String value) {
        if (value == null) {
            return false;
        }
        try {
            return PropertyValueEncryptionUtils.isEncryptedValue(value);
        } catch (Throwable t) {
            if (ClassB.LOGGER.isWarnEnabled()) {
                ClassB.LOGGER.warn("Cannot check is-decrypted for value=" + value, t);
            }
            return false;
        }
    }

    static {
        LOGGER = LoggerFactory.getLogger((Class) StringEncryptor.class);
    }
}
