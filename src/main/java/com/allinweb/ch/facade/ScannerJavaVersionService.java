package com.allinweb.ch.facade;

public class ScannerJavaVersionService {

    public int majorVersion(String version) {
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }

        String[] parts = version.split("\\.");
        return Integer.parseInt(parts[0]);
    }
}
