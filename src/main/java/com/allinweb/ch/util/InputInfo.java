package com.allinweb.ch.util;

// Keep this OUTSIDE DomIntrospectionUtil (top-level record)
public record InputInfo(
        String tag, String id, String name, String type, String labelText, String identifier, String printable) {}
