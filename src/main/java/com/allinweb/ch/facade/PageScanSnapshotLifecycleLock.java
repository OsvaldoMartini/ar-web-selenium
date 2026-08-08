package com.allinweb.ch.facade;

/**
 * One process-wide ordering boundary for snapshot writes, retention, and owner deletion/replacement.
 * Filesystem moves and their authoritative database mutation must never interleave across these
 * otherwise independent lifecycle services.
 */
final class PageScanSnapshotLifecycleLock {

    static final Object MONITOR = new Object();

    private PageScanSnapshotLifecycleLock() {}
}
