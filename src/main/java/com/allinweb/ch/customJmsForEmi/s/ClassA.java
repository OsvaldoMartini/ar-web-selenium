//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.s;

import org.jasypt.encryption.pbe.config.EnvironmentStringPBEConfig;

final class ClassA extends EnvironmentStringPBEConfig {
    public ClassA() {
        this.setPassword(ClassK.$$());
        this.setAlgorithm("PBEWithMD5AndDES");
    }
}
