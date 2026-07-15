package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;

/** Canonical UI-independent capability policy shared by Bot Job workspace hosts. */
public final class BotJobWorkspaceCapabilityService {

    private static final BotJobWorkspaceCapabilityService INSTANCE = new BotJobWorkspaceCapabilityService();

    private BotJobWorkspaceCapabilityService() {}

    public static BotJobWorkspaceCapabilityService getInstance() {
        return INSTANCE;
    }

    public boolean supportsDesktopBrowserTools(String projectType) {
        String normalized = projectType == null ? "" : projectType.trim();
        return normalized.isEmpty()
                || ARPropertyEnum.WEB_APP.getValue().equalsIgnoreCase(normalized)
                || "Rest Api".equalsIgnoreCase(normalized);
    }

    public boolean supportsNativeMobileTools(String projectType) {
        String normalized = projectType == null ? "" : projectType.trim();
        return ARPropertyEnum.ANDROID.getValue().equalsIgnoreCase(normalized)
                || ARPropertyEnum.IOS.getValue().equalsIgnoreCase(normalized);
    }

    public void requirePreScan(String projectType) {
        if (!supportsDesktopBrowserTools(projectType)) {
            throw new IllegalStateException("Pre Scan is unavailable for this Bot Job type");
        }
    }

    public void requireOrganizationManager(boolean licenseGuardEnabled, boolean activeLicense) {
        if (licenseGuardEnabled && !activeLicense) {
            throw new IllegalStateException("An active license is required to manage environments");
        }
    }
}
