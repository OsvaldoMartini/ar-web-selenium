import { BrowserScannerRequest, BrowserSessionFactory, BrowserSessionHandle } from '../browser/browserSessionFactory';
import { randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { ExecutionSession } from '../session/executionSession';
import { ExecutionLaunchDescriptor, ExecutionSessionSnapshot } from '../session/sessionContracts';
import { PhysicalActionRequest, PhysicalActionResult } from '../action/actionContracts';

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
  private readonly retained = new Map<string, BrowserSessionHandle>();
  private readonly scanners = new Map<string, {
    ownerKey: string;
    handle: BrowserSessionHandle;
    token: string;
    idleTimer: NodeJS.Timeout;
    busy: boolean;
  }>();
  private draining = false;

  constructor(
    private readonly factory: BrowserSessionFactory,
    private readonly limits: WorkerPoolLimits,
    private readonly trace: (event: string) => void = () => undefined,
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
    const ownerKey = this.ownerKey(descriptor.run);
    if ([...this.scanners.values()].some(scanner => scanner.ownerKey === ownerKey)) {
      throw new PlaywrightWorkerPoolError('SCANNER_SESSION_CONFLICT');
    }
    const retainedHandle = this.retained.get(ownerKey);
    if (retainedHandle) this.retained.delete(ownerKey);
    if (!retainedHandle && this.retained.size >= this.limits.maximumActiveRuns) {
      throw new PlaywrightWorkerPoolError('RETAINED_BROWSER_CAPACITY_REACHED');
    }
    const session = new ExecutionSession(descriptor, this.factory, terminal => {
      const terminalEntry = this.entries.get(runId);
      if (!terminalEntry || !terminalEntry.admitted) return;
      terminalEntry.admitted = false;
      // STOPPED owns a reusable browser until release atomically parks it. FAILED has no
      // reusable handle and may release pool capacity immediately.
      if (terminal.state === 'FAILED') this.scheduleDrain();
    }, retainedHandle);
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

  async perform(runId: string, request: PhysicalActionRequest): Promise<PhysicalActionResult> {
    const entry = this.requireEntry(runId);
    return entry.session.perform(request);
  }

  async pageIdentity(runId: string): Promise<string> {
    const entry = this.requireEntry(runId);
    return entry.session.pageIdentity();
  }

  async recoveryScanner(runId: string, request: BrowserScannerRequest): Promise<unknown> {
    const entry = this.requireEntry(runId);
    if (!entry.admitted) throw new PlaywrightWorkerPoolError('SESSION_NOT_ACTIVE');
    return entry.session.scanner(request);
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
      }
    }
    return entry.session.snapshot();
  }

  async closeBrowser(runId: string): Promise<ExecutionSessionSnapshot> {
    const entry = this.requireEntry(runId);
    try {
      await entry.session.closeBrowser();
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
    const retainedHandle = state === 'STOPPED' ? entry.session.takeRetainedHandle() : undefined;
    if (retainedHandle) {
      const key = this.ownerKey(entry.session.descriptor.run);
      if (this.retained.has(key)) throw new PlaywrightWorkerPoolError('RETAINED_SESSION_CONFLICT');
      this.retained.set(key, retainedHandle);
    }
    this.entries.delete(runId);
    this.scheduleDrain();
  }

  openScanner(owner: { organizationId: number; homeBankingId: number; botJobId: number }): {
    scannerId: string;
    scannerToken: string;
  } {
    const ownerKey = this.ownerKey(owner);
    if ([...this.scanners.values()].some(scanner => scanner.ownerKey === ownerKey)) {
      throw new PlaywrightWorkerPoolError('SCANNER_SESSION_CONFLICT');
    }
    const handle = this.retained.get(ownerKey);
    if (!handle) throw new PlaywrightWorkerPoolError('RETAINED_BROWSER_NOT_FOUND');
    this.retained.delete(ownerKey);
    const scannerId = randomUUID();
    const scannerToken = randomBytes(32).toString('base64url');
    const idleTimer = this.scannerIdleTimer(scannerId);
    this.scanners.set(scannerId, { ownerKey, handle, token: scannerToken, idleTimer, busy: false });
    return { scannerId, scannerToken };
  }

  async scanner(
    scannerId: string,
    scannerToken: string,
    request: BrowserScannerRequest,
  ): Promise<unknown> {
    const scanner = this.scanners.get(scannerId);
    if (!scanner || !this.scannerTokenMatches(scanner.token, scannerToken)) {
      throw new PlaywrightWorkerPoolError('SCANNER_SESSION_NOT_FOUND');
    }
    if (scanner.busy) throw new PlaywrightWorkerPoolError('SCANNER_SESSION_BUSY');
    scanner.busy = true;
    clearTimeout(scanner.idleTimer);
    try {
      return await scanner.handle.scanner(request);
    } finally {
      if (this.scanners.get(scannerId) === scanner) {
        scanner.busy = false;
        scanner.idleTimer = this.scannerIdleTimer(scannerId);
      }
    }
  }

  closeScanner(
    scannerId: string,
    scannerToken: string,
  ): void {
    const scanner = this.scanners.get(scannerId);
    if (!scanner || !this.scannerTokenMatches(scanner.token, scannerToken)) {
      throw new PlaywrightWorkerPoolError('SCANNER_SESSION_NOT_FOUND');
    }
    if (scanner.busy) throw new PlaywrightWorkerPoolError('SCANNER_SESSION_BUSY');
    if (this.retained.has(scanner.ownerKey)) {
      throw new PlaywrightWorkerPoolError('RETAINED_SESSION_CONFLICT');
    }
    this.parkScanner(scannerId, scanner);
  }

  async closeAll(): Promise<void> {
    const runIds = [...this.entries.keys()];
    await Promise.allSettled(runIds.map(runId => this.closeBrowser(runId)));
    await Promise.allSettled([...this.retained.values()].map(handle => handle.close()));
    await Promise.allSettled([...this.scanners.values()].map(scanner => {
      clearTimeout(scanner.idleTimer);
      return scanner.handle.close();
    }));
    this.retained.clear();
    this.scanners.clear();
  }

  private scannerIdleTimer(scannerId: string): NodeJS.Timeout {
    const timer = setTimeout(() => {
      const scanner = this.scanners.get(scannerId);
      if (scanner) {
        this.parkScanner(scannerId, scanner);
        this.trace('scanner.lease.expired');
      }
    }, 10 * 60 * 1_000);
    timer.unref();
    return timer;
  }

  private parkScanner(
    scannerId: string,
    scanner: {
      ownerKey: string;
      handle: BrowserSessionHandle;
      token: string;
      idleTimer: NodeJS.Timeout;
      busy: boolean;
    },
  ): void {
    clearTimeout(scanner.idleTimer);
    this.scanners.delete(scannerId);
    if (!this.retained.has(scanner.ownerKey)) this.retained.set(scanner.ownerKey, scanner.handle);
  }

  private scannerTokenMatches(expected: string, supplied: string): boolean {
    const expectedBytes = Buffer.from(expected, 'utf8');
    const suppliedBytes = Buffer.from(supplied || '', 'utf8');
    return expectedBytes.length === suppliedBytes.length && timingSafeEqual(expectedBytes, suppliedBytes);
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

  private ownerKey(owner: {
    organizationId: number;
    homeBankingId: number;
    botJobId: number;
  }): string {
    return `${owner.organizationId}:${owner.homeBankingId}:${owner.botJobId}`;
  }
}
