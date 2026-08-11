import { BrowserSessionFactory } from '../browser/browserSessionFactory';
import { ExecutionSession } from '../session/executionSession';
import { ExecutionLaunchDescriptor, ExecutionSessionSnapshot } from '../session/sessionContracts';

export interface WorkerPoolLimits {
  readonly maximumActiveRuns: number;
  readonly maximumQueuedRuns: number;
  readonly maximumActiveRunsPerOrganization: number;
  readonly maximumActiveRunsPerBotJob: number;
}

interface PoolEntry {
  readonly session: ExecutionSession;
  admitted: boolean;
}

export class PlaywrightWorkerPoolError extends Error {
  constructor(readonly code: string) {
    super(code);
    this.name = 'PlaywrightWorkerPoolError';
  }
}

export class PlaywrightWorkerPool {
  private readonly entries = new Map<string, PoolEntry>();
  private readonly queue: string[] = [];
  private draining = false;

  constructor(
    private readonly factory: BrowserSessionFactory,
    private readonly limits: WorkerPoolLimits,
  ) {
    for (const value of Object.values(limits)) {
      if (!Number.isInteger(value) || value < 1) throw new Error('WORKER_POOL_LIMIT_INVALID');
    }
  }

  enqueue(descriptor: ExecutionLaunchDescriptor): ExecutionSessionSnapshot {
    const runId = descriptor.run.runId;
    if (this.entries.has(runId)) throw new PlaywrightWorkerPoolError('SESSION_ALREADY_EXISTS');
    if (this.queue.length >= this.limits.maximumQueuedRuns) {
      throw new PlaywrightWorkerPoolError('WORKER_QUEUE_CAPACITY_REACHED');
    }
    const session = new ExecutionSession(descriptor, this.factory, () => {
      const terminalEntry = this.entries.get(runId);
      if (!terminalEntry || !terminalEntry.admitted) return;
      terminalEntry.admitted = false;
      this.scheduleDrain();
    });
    this.entries.set(runId, { session, admitted: false });
    this.queue.push(runId);
    this.scheduleDrain();
    return session.snapshot();
  }

  snapshot(runId: string): ExecutionSessionSnapshot {
    return this.requireEntry(runId).session.snapshot();
  }

  snapshots(): readonly ExecutionSessionSnapshot[] {
    return [...this.entries.values()].map(entry => entry.session.snapshot());
  }

  async refresh(runId: string): Promise<ExecutionSessionSnapshot> {
    const entry = this.requireEntry(runId);
    if (!entry.admitted) throw new PlaywrightWorkerPoolError('SESSION_NOT_ACTIVE');
    try {
      await entry.session.refresh();
    } finally {
      if (entry.session.snapshot().state === 'FAILED') {
        entry.admitted = false;
        this.scheduleDrain();
      }
    }
    return entry.session.snapshot();
  }

  async stop(runId: string): Promise<ExecutionSessionSnapshot> {
    const entry = this.requireEntry(runId);
    if (!entry.admitted) {
      const index = this.queue.indexOf(runId);
      if (index >= 0) this.queue.splice(index, 1);
    }
    try {
      await entry.session.stop();
    } finally {
      if (entry.admitted) {
        entry.admitted = false;
        this.scheduleDrain();
      }
    }
    return entry.session.snapshot();
  }

  release(runId: string): void {
    const entry = this.requireEntry(runId);
    const state = entry.session.snapshot().state;
    if (entry.admitted || (state !== 'STOPPED' && state !== 'FAILED')) {
      throw new PlaywrightWorkerPoolError('SESSION_RELEASE_STATE_INVALID');
    }
    this.entries.delete(runId);
  }

  async closeAll(): Promise<void> {
    const runIds = [...this.entries.keys()];
    await Promise.allSettled(runIds.map(runId => this.stop(runId)));
  }

  private scheduleDrain(): void {
    if (this.draining) return;
    this.draining = true;
    queueMicrotask(() => void this.drain());
  }

  private async drain(): Promise<void> {
    try {
      while (true) {
        const index = this.queue.findIndex(runId => {
          const entry = this.entries.get(runId);
          return Boolean(entry && this.canAdmit(entry.session.snapshot()));
        });
        if (index < 0) return;
        const [runId] = this.queue.splice(index, 1);
        if (!runId) return;
        const entry = this.entries.get(runId);
        if (!entry) continue;
        entry.admitted = true;
        void entry.session.start().catch(() => {
          entry.admitted = false;
          this.scheduleDrain();
        });
      }
    } finally {
      this.draining = false;
      if (this.queue.some(runId => {
        const entry = this.entries.get(runId);
        return Boolean(entry && this.canAdmit(entry.session.snapshot()));
      })) this.scheduleDrain();
    }
  }

  private canAdmit(candidate: ExecutionSessionSnapshot): boolean {
    const active = [...this.entries.values()]
      .filter(entry => entry.admitted)
      .map(entry => entry.session.snapshot());
    if (active.length >= this.limits.maximumActiveRuns) return false;
    if (active.filter(run => run.organizationId === candidate.organizationId).length
        >= this.limits.maximumActiveRunsPerOrganization) return false;
    return active.filter(run => run.botJobId === candidate.botJobId).length
      < this.limits.maximumActiveRunsPerBotJob;
  }

  private requireEntry(runId: string): PoolEntry {
    const entry = this.entries.get(runId);
    if (!entry) throw new PlaywrightWorkerPoolError('SESSION_NOT_FOUND');
    return entry;
  }
}
