package com.allinweb.ch.model;

/** Persisted Page Scanner focus profile exposed to the React workspace. */
public record ScannerSearchProfile(
        int id,
        String key,
        String label,
        String searchTerms,
        int sortOrder,
        boolean protectedProfile) {}
