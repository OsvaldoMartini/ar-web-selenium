package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobToolbarAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Zero-queue foreground-operation guard shared by headless and native toolbar actions. */
public final class BotJobToolbarConcurrencyGuard {

    private final AtomicReference<ActiveOperation> active = new AtomicReference<>();
    private final AtomicReference<Process> externalEngine = new AtomicReference<>();

    public Lease tryAcquire(int botJobId, long workspaceEpoch, BotJobToolbarAction action) {
        if (action == null) throw new IllegalArgumentException("Toolbar action is required");
        ActiveOperation operation = new ActiveOperation(botJobId, workspaceEpoch, action);
        return active.compareAndSet(null, operation) ? new Lease(this, operation) : null;
    }

    public ActiveOperation activeOperation() {
        return active.get();
    }

    public boolean trackExternalEngine(Process process) {
        if (process == null) throw new IllegalArgumentException("External Engine process is required");
        while (true) {
            Process current = externalEngine.get();
            if (current != null) {
                if (current.isAlive()) return false;
                externalEngine.compareAndSet(current, null);
                continue;
            }
            return !process.isAlive() || externalEngine.compareAndSet(null, process);
        }
    }

    public boolean externalEngineRunning() {
        Process current = externalEngine.get();
        if (current == null) return false;
        if (current.isAlive()) return true;
        externalEngine.compareAndSet(current, null);
        return false;
    }

    public void externalEngineFinished(Process process) {
        if (process != null) externalEngine.compareAndSet(process, null);
    }

    private void release(ActiveOperation operation) {
        active.compareAndSet(operation, null);
    }

    public record ActiveOperation(int botJobId, long workspaceEpoch, BotJobToolbarAction action) {}

    public static final class Lease implements AutoCloseable {
        private final BotJobToolbarConcurrencyGuard owner;
        private final ActiveOperation operation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(BotJobToolbarConcurrencyGuard owner, ActiveOperation operation) {
            this.owner = owner;
            this.operation = operation;
        }

        public ActiveOperation operation() {
            return operation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) owner.release(operation);
        }
    }
}
