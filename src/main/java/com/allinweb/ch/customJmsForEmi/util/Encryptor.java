//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.util;

import com.allinweb.ch.customJmsForEmi.s.ClassB;

public class Encryptor {
    private static final String HELP_OPTION = "help";
    private static final String VALUE_OPTION = "value";
    private static final String CHECK_OPTION = "check";
    private static final String SEPARATOR = "=";
    ClassB bb;

    public Encryptor() {
        this.bb = new ClassB();
    }

    public static void main(final String[] args) {
        final Encryptor encryptor = new Encryptor();
        final CommandLineArguments arguments = encryptor.parseCommandLineArguments(args);
        if (arguments.value != null) {
            final String encryptedValue = encryptor.bb.encrypt(arguments.value);
            System.out.println("ORIGINAL:  " + arguments.value);
            System.out.println("ENCRYPTED: " + encryptedValue);
        }
        if (arguments.check != null) {
            final int separatorIndex = arguments.check.indexOf("=");
            final String original = arguments.check.substring(0, separatorIndex);
            final String encrypted = arguments.check.substring(separatorIndex + 1, arguments.check.length());
            final String decrypted = encryptor.bb.decrypt(encrypted);
            if (original.equals(decrypted)) {
                System.out.println("Encrypted original MATCHES the provided encryption:");
            } else {
                System.out.println("Encrypted original DOES NOT MATCH the provided encryption:");
            }
            System.out.println("  ORIGINAL:   " + original);
            System.out.println("  ENCRYPTED:  " + encrypted);
        }
    }

    private CommandLineArguments parseCommandLineArguments(final String[] args) {
        final CommandLine commandLine = new CommandLine(args);
        if (commandLine.exists("help")) {
            this.showUsage();
            System.exit(0);
        }
        String value = null;
        String check = null;
        if (commandLine.isParameter("value")) {
            value = commandLine.value("value");
            if (value == null) {
                System.err.println("No value for encryption provided");
            }
        } else if (commandLine.isParameter("check")) {
            check = commandLine.value("check");
            if (check == null) {
                System.err.println("No value provided");
            } else if (check.indexOf("=ENC(") < 0) {
                System.err.println("Wrong value check syntax. Correct is: ORIGINAL=ENC(ENCRYPTED)");
            }
        } else {
            this.showUsage();
            System.exit(1);
        }
        final CommandLineArguments commandLineArguments = new CommandLineArguments();
        commandLineArguments.value = value;
        commandLineArguments.check = check;
        return commandLineArguments;
    }

    private void showUsage() {
        System.out.println(
                "Following options are supported:\n-value  The value to be encrypted\n-help   Show this help");
    }

    private class CommandLineArguments {
        String value;
        String check;
    }
}
