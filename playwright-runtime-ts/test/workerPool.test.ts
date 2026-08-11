import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { test } from 'node:test';
import { BrowserSessionFactory, BrowserSessionHandle } from '../src/browser/browserSessionFactory';
import { PlaywrightWorkerPool } from '../src/pool/playwrightWorkerPool';
import { ExecutionLaunchDescriptor, ExecutionSessionState } from '../src/session/sessionContracts';

type Behavior = 'READY' | 'BLOCK_NAVIGATION' | 'FAIL_NAVIGATION';

class FakeHandle implements BrowserSessionHandle {
  readonly browserInstanceId = randomUUID();
  readonly contextInstanceId = randomUUID();
  readonly pageInstanceId = randomUUID();
  closed = false;
  refreshCount = 0;
  private rejectNavigation?: (error: Error) => void;
  private unexpectedCloseCode?: string;
  private unexpectedCloseHandler?: (code: string) => void;

  constructor(private readonly behavior: Behavior) {}

  onUnexpectedClose(handler: (code: string) => void): void {
    this.unexpectedCloseHandler = handler;
    if (this.unexpectedCloseCode) handler(this.unexpectedCloseCode);
  }

  async navigate(): Promise<void> {
    if (this.behavior === 'FAIL_NAVIGATION') throw new Error('PAGE_READINESS_TIMEOUT');
    if (this.behavior === 'BLOCK_NAVIGATION') {
      await new Promise<void>((_resolve, reject) => {
        this.rejectNavigation = reject;
      });
    }
  }

  async refresh(): Promise<void> {
    if (this.closed) throw new Error('BROWSER_SESSION_CLOSED');
    this.refreshCount += 1;
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.closed = true;
    this.rejectNavigation?.(new Error('BROWSER_SESSION_CLOSED'));
  }

  crash(): void {
    if (this.closed) return;
    this.unexpectedCloseCode = 'BROWSER_PROCESS_DISCONNECTED';
    this.unexpectedCloseHandler?.('BROWSER_PROCESS_DISCONNECTED');
  }
}

class FakeFactory implements BrowserSessionFactory {
  readonly handles = new Map<string, FakeHandle>();

  constructor(private readonly behaviorByRunId = new Map<string, Behavior>()) {}

  async open(runId: string): Promise<BrowserSessionHandle> {
    const handle = new FakeHandle(this.behaviorByRunId.get(runId) ?? 'READY');
    this.handles.set(runId, handle);
    return handle;
  }
}

const descriptor = (
  organizationId: number,
  botJobId: number,
  runId = randomUUID(),
): ExecutionLaunchDescriptor => ({
  run: {
    runId,
    organizationId,
    homeBankingId: organizationId,
    botJobId,
    workspaceEpoch: 1,
    graphRevision: 'a'.repeat(64),
    planRevision: 'b'.repeat(64),
    dataMode: 'SYNTHETIC',
    state: 'RESERVED',
    createdAt: '2026-08-11T12:00:00.000Z',
    expiresAt: '2026-08-11T12:02:00.000Z',
  },
  endpoint: 'https://example.test/',
  browser: { headless: true, channel: 'chromium' },
});

const waitForState = async (
  pool: PlaywrightWorkerPool,
  runId: string,
  expected: ExecutionSessionState,
): Promise<void> => {
  const deadline = Date.now() + 2_000;
  while (Date.now() < deadline) {
    if (pool.snapshot(runId).state === expected) return;
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  assert.equal(pool.snapshot(runId).state, expected);
};

const limits = {
  maximumActiveRuns: 2,
  maximumQueuedRuns: 8,
  maximumActiveRunsPerOrganization: 1,
  maximumActiveRunsPerBotJob: 1,
};

test('admits independent organizations while preserving per-owner queue isolation', async () => {
  const factory = new FakeFactory();
  const pool = new PlaywrightWorkerPool(factory, limits);
  const first = descriptor(13, 29);
  const sameOrganization = descriptor(13, 30);
  const otherOrganization = descriptor(2, 32);

  pool.enqueue(first);
  pool.enqueue(sameOrganization);
  pool.enqueue(otherOrganization);
  await waitForState(pool, first.run.runId, 'READY');
  await waitForState(pool, otherOrganization.run.runId, 'READY');
  assert.equal(pool.snapshot(sameOrganization.run.runId).state, 'QUEUED');

  const firstSnapshot = pool.snapshot(first.run.runId);
  const otherSnapshot = pool.snapshot(otherOrganization.run.runId);
  assert.notEqual(firstSnapshot.browserInstanceId, otherSnapshot.browserInstanceId);
  assert.notEqual(firstSnapshot.contextInstanceId, otherSnapshot.contextInstanceId);
  assert.notEqual(firstSnapshot.pageInstanceId, otherSnapshot.pageInstanceId);

  const stoppedFirst = await pool.stop(first.run.runId);
  assert.equal(stoppedFirst.browserInstanceId, firstSnapshot.browserInstanceId);
  assert.equal(stoppedFirst.contextInstanceId, firstSnapshot.contextInstanceId);
  assert.equal(stoppedFirst.pageInstanceId, firstSnapshot.pageInstanceId);
  await waitForState(pool, sameOrganization.run.runId, 'READY');
  assert.equal(factory.handles.get(otherOrganization.run.runId)?.closed, false);
  await pool.closeAll();
});

test('stop interrupts a run that is still loading without affecting another run', async () => {
  const blocked = descriptor(13, 29);
  const healthy = descriptor(2, 32);
  const factory = new FakeFactory(new Map([[blocked.run.runId, 'BLOCK_NAVIGATION']]));
  const pool = new PlaywrightWorkerPool(factory, limits);

  pool.enqueue(blocked);
  pool.enqueue(healthy);
  await waitForState(pool, blocked.run.runId, 'LOADING_PAGE');
  await waitForState(pool, healthy.run.runId, 'READY');
  await pool.stop(blocked.run.runId);

  assert.equal(pool.snapshot(blocked.run.runId).state, 'STOPPED');
  assert.equal(factory.handles.get(blocked.run.runId)?.closed, true);
  assert.equal(pool.snapshot(healthy.run.runId).state, 'READY');
  assert.equal(factory.handles.get(healthy.run.runId)?.closed, false);
  await pool.closeAll();
});

test('failed navigation cleans its exact browser and releases capacity for the queue', async () => {
  const failed = descriptor(13, 29);
  const next = descriptor(13, 30);
  const factory = new FakeFactory(new Map([[failed.run.runId, 'FAIL_NAVIGATION']]));
  const pool = new PlaywrightWorkerPool(factory, {
    ...limits,
    maximumActiveRuns: 1,
  });

  pool.enqueue(failed);
  pool.enqueue(next);
  await waitForState(pool, failed.run.runId, 'FAILED');
  await waitForState(pool, next.run.runId, 'READY');

  assert.equal(pool.snapshot(failed.run.runId).failureCode, 'PAGE_READINESS_TIMEOUT');
  assert.equal(factory.handles.get(failed.run.runId)?.closed, true);
  assert.equal(factory.handles.get(next.run.runId)?.closed, false);
  await pool.refresh(next.run.runId);
  assert.equal(factory.handles.get(next.run.runId)?.refreshCount, 1);
  await pool.closeAll();
});

test('one browser crash fails only its run and admits the next eligible run', async () => {
  const crashed = descriptor(13, 29);
  const queued = descriptor(13, 30);
  const independent = descriptor(2, 32);
  const factory = new FakeFactory();
  const pool = new PlaywrightWorkerPool(factory, limits);

  pool.enqueue(crashed);
  pool.enqueue(queued);
  pool.enqueue(independent);
  await waitForState(pool, crashed.run.runId, 'READY');
  await waitForState(pool, independent.run.runId, 'READY');
  factory.handles.get(crashed.run.runId)?.crash();

  await waitForState(pool, crashed.run.runId, 'FAILED');
  await waitForState(pool, queued.run.runId, 'READY');
  assert.equal(
    pool.snapshot(crashed.run.runId).failureCode,
    'BROWSER_PROCESS_DISCONNECTED',
  );
  assert.equal(pool.snapshot(independent.run.runId).state, 'READY');
  assert.equal(factory.handles.get(independent.run.runId)?.closed, false);
  await pool.closeAll();
});
