package com.allinweb.ch.facade;

/** UI-independent capability gate and presentation port for the organization manager. */
public final class BotJobOrganizationCoordinator {

    private final BotJobWorkspaceCapabilityService capabilities;
    private final OrganizationPort organization;

    public BotJobOrganizationCoordinator(
            BotJobWorkspaceCapabilityService capabilities, OrganizationPort organization) {
        this.capabilities = capabilities;
        this.organization = organization;
    }

    public void open(boolean licenseGuardEnabled, boolean activeLicense) {
        capabilities.requireOrganizationManager(licenseGuardEnabled, activeLicense);
        organization.open();
    }

    @FunctionalInterface
    public interface OrganizationPort {
        void open();
    }
}
