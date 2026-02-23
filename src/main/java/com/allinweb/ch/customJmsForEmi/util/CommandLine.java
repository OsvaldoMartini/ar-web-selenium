//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandLine {
    private final List<String> switches;
    private final Map<String, String> options;
    private static final String OPTION_NAME_PREFIX = "-";

    public CommandLine(final String[] args) {
        this.switches = new ArrayList<String>();
        this.options = new HashMap<String, String>();
        this.processCommandLine(args);
    }

    public CommandLine() {
        this.switches = new ArrayList<String>();
        this.options = new HashMap<String, String>();
    }

    public final boolean exists(final String name) {
        return this.switches.contains(name) || this.options.containsKey(name);
    }

    public boolean isSwitch(final String name) {
        return this.switches.contains(name);
    }

    public boolean isParameter(final String name) {
        return this.options.containsKey(name);
    }

    public String value(final String name) {
        String result = null;
        if (this.options.containsKey(name)) {
            result = this.options.get(name);
        }
        return result;
    }

    public String value(final String name, final String defaultValue) {
        final String result = this.value(name);
        return (result != null) ? result : defaultValue;
    }

    public final boolean add(final String name, final String value) {
        return this.add(name, value, true);
    }

    public final boolean add(final String name, final String value, final boolean overwrite) {
        boolean result = false;
        if (value == null) {
            if (this.switches.contains(name) && overwrite) {
                this.switches.add(name);
                result = true;
            } else if (!this.switches.contains(name)) {
                this.switches.add(name);
                result = true;
            }
        } else if (this.options.containsKey(name) && overwrite) {
            this.options.put(name, value);
            result = true;
        } else if (!this.options.containsKey(name)) {
            this.options.put(name, value);
            result = true;
        }
        return result;
    }

    private final void processCommandLine(final String[] args) {
        boolean prev_was_hyphen = false;
        String prev_key = null;
        for (int index = 0; index < args.length; ++index) {
            if (args[index].startsWith("-")) {
                if (prev_was_hyphen) {
                    this.add(prev_key, null);
                }
                prev_key = args[index].substring(1);
                prev_was_hyphen = true;
                if (index == args.length - 1) {
                    this.add(prev_key, null);
                    break;
                }
            } else {
                if (prev_key != null) {
                    this.add(prev_key, args[index]);
                    prev_key = null;
                }
                prev_was_hyphen = false;
            }
        }
    }
}
