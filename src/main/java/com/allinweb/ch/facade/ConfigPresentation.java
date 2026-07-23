package com.allinweb.ch.facade;

public interface ConfigPresentation {
    String choosePath(String mode);

    default String choosePath(String mode, String currentPath) {
        return choosePath(mode);
    }

    void openOrganizations();

    void closeModal();
}
