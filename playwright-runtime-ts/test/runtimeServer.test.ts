import assert from 'node:assert/strict';
import { request } from 'node:http';
import { test } from 'node:test';
import { PhysicalActionRequest, PhysicalActionResult } from '../src/action/actionContracts';
import { BrowserScannerRequest } from '../src/browser/browserSessionFactory';
import { RuntimeConfig } from '../src/config/runtimeConfig';
import { createSafeLogger } from '../src/logging/safeLogger';
import { ExecutionLaunchDescriptor, ExecutionSessionSnapshot } from '../src/session/sessionContracts';
import { createRuntimeServer } from '../src/server';
import { TEST_NOW, TEST_SECRET, claimsFixture, signGrant } from './testSupport';

interface HttpResult {
  readonly status: number;
  readonly body: unknown;
}

const call = async (
  port: number,
  method: string,
  path: string,
  grant?: string,
  body?: unknown,
  token?: string,
): Promise<HttpResult> => new Promise((resolve, reject) => {
  const encodedBody = body === undefined ? undefined : JSON.stringify(body);
  const client = request({
    host: '127.0.0.1',
    port,
    method,
    path,
    headers: {
      ...(grant ? { authorization: `Bearer ${grant}` } : {}),
      ...(token ? { 'x-arweb-run-token': token } : {}),
      ...(encodedBody ? {
        'content-type': 'application/json',
        'content-length': Buffer.byteLength(encodedBody),
      } : {}),
    },
  }, response => {
    const chunks: Buffer[] = [];
    response.on('data', chunk => chunks.push(Buffer.from(chunk)));
    response.on('end', () => {
      try {
        resolve({
          status: response.statusCode ?? 0,
          body: JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown,
        });
      } catch (error) {
        reject(error);
      }
    });
  });
  client.on('error', reject);
  client.end(encodedBody);
});

class FakeRuntimeWorkerPool {
  readonly snapshotsByRunId = new Map<string, ExecutionSessionSnapshot>();
  lastDescriptor: ExecutionLaunchDescriptor | undefined;
  actionCount = 0;
  stopCount = 0;
  releaseCount = 0;
  scannerRpcCount = 0;
  readonly scannerId = '11111111-1111-4111-8111-111111111111';
  readonly scannerToken = 's'.repeat(43);

  enqueue(descriptor: ExecutionLaunchDescriptor): ExecutionSessionSnapshot {
    if (this.snapshotsByRunId.has(descriptor.run.runId)) throw new Error('SESSION_ALREADY_EXISTS');
    this.lastDescriptor = descriptor;
    const snapshot = this.makeSnapshot(descriptor.run.runId, descriptor.run.organizationId,
      descriptor.run.homeBankingId, descriptor.run.botJobId, descriptor.run.dataMode, 'READY');
    this.snapshotsByRunId.set(descriptor.run.runId, snapshot);
    return snapshot;
  }

  snapshot(runId: string): ExecutionSessionSnapshot {
    const snapshot = this.snapshotsByRunId.get(runId);
    if (!snapshot) {
      const error = new Error('SESSION_NOT_FOUND') as Error & { code: string };
      error.code = 'SESSION_NOT_FOUND';
      error.name = 'PlaywrightWorkerPoolError';
      throw error;
    }
    return snapshot;
  }

  async refresh(runId: string): Promise<ExecutionSessionSnapshot> {
    return this.snapshot(runId);
  }

  async pageIdentity(runId: string): Promise<string> {
    this.snapshot(runId);
    return `url-v1:${'c'.repeat(64)}`;
  }

  async recoveryScanner(runId: string, request: BrowserScannerRequest): Promise<unknown> {
    this.snapshot(runId);
    this.scannerRpcCount += 1;
    return request.operation === 'url' ? 'https://example.test/current' : true;
  }

  async perform(runId: string, request: PhysicalActionRequest): Promise<PhysicalActionResult> {
    this.snapshot(runId);
    this.actionCount += 1;
    return {
      ok: true,
      diagnostic: {
        code: 'COMPLETED', stage: 'AUTHORED', action: request.action,
        instructionId: request.instructionId, registryCandidateCount: 0, liveCandidateCount: 1,
        frameValidated: true, shadowValidated: true, tagValidated: true,
        actionValidated: true, physicalAttempts: 1,
      },
    };
  }

  async stop(runId: string): Promise<ExecutionSessionSnapshot> {
    const current = this.snapshot(runId);
    this.stopCount += 1;
    const stopped = { ...current, state: 'STOPPED' as const, revision: current.revision + 1 };
    this.snapshotsByRunId.set(runId, stopped);
    return stopped;
  }

  async closeBrowser(runId: string): Promise<ExecutionSessionSnapshot> {
    return this.stop(runId);
  }

  release(runId: string): void {
    this.snapshot(runId);
    this.releaseCount += 1;
    this.snapshotsByRunId.delete(runId);
  }

  openScanner(): { scannerId: string; scannerToken: string } {
    return { scannerId: this.scannerId, scannerToken: this.scannerToken };
  }

  async scanner(
    scannerId: string,
    scannerToken: string,
    request: BrowserScannerRequest,
  ): Promise<unknown> {
    if (scannerId !== this.scannerId || scannerToken !== this.scannerToken) {
      throw new Error('SCANNER_SESSION_NOT_FOUND');
    }
    this.scannerRpcCount += 1;
    return request.operation === 'url' ? 'https://example.test/current' : true;
  }

  closeScanner(scannerId: string, scannerToken: string): void {
    if (scannerId !== this.scannerId || scannerToken !== this.scannerToken) {
      throw new Error('SCANNER_SESSION_NOT_FOUND');
    }
  }

  async closeAll(): Promise<void> {
    this.snapshotsByRunId.clear();
  }

  private makeSnapshot(
    runId: string, organizationId: number, homeBankingId: number, botJobId: number,
    dataMode: 'REAL' | 'SYNTHETIC', state: 'READY',
  ): ExecutionSessionSnapshot {
    return {
      runId, organizationId, homeBankingId, botJobId, dataMode, state, revision: 1,
      queuedAt: '2026-08-11T12:00:00.000Z',
    };
  }
}

const responseData = (result: HttpResult): Record<string, unknown> => {
  if (typeof result.body !== 'object' || result.body === null || !('data' in result.body)) {
    assert.fail('Expected a runtime success envelope.');
  }
  return (result.body as { data: Record<string, unknown> }).data;
};

const config = (secret = true): RuntimeConfig => ({
  host: '127.0.0.1',
  port: 0,
  grantKeyId: 'v1',
  ...(secret ? { grantSecret: TEST_SECRET } : {}),
  maxReservedRuns: 2,
  maxGrantSeconds: 120,
  clockSkewSeconds: 5,
  runIdleLeaseSeconds: 60,
  maximumActiveRuns: 2,
  maximumQueuedRuns: 4,
  maximumActiveRunsPerOrganization: 1,
  maximumActiveRunsPerBotJob: 1,
});

test('reports live but not ready when grant verification is unconfigured', async () => {
  const runtime = createRuntimeServer({ config: config(false), logSink: () => undefined });
  const address = await runtime.listen();
  try {
    assert.equal((await call(address.port, 'GET', '/health/live')).status, 200);
    assert.equal((await call(address.port, 'GET', '/health/ready')).status, 503);
  } finally {
    await runtime.close();
  }
});

test('reserves, replays, reads, and releases only the exact signed run', async () => {
  const runtime = createRuntimeServer({
    config: config(),
    logSink: () => undefined,
    nowEpochSeconds: () => TEST_NOW,
  });
  const address = await runtime.listen();
  const claims = claimsFixture();
  const grant = signGrant(claims);
  try {
    assert.equal((await call(address.port, 'GET', '/health/ready')).status, 200);
    assert.equal((await call(address.port, 'POST', '/v2/runs/reserve', grant)).status, 201);
    assert.equal((await call(address.port, 'POST', '/v2/runs/reserve', grant)).status, 200);
    assert.equal((await call(address.port, 'GET', `/v2/runs/${claims.runId}`, grant)).status, 200);
    assert.equal((await call(address.port, 'DELETE', `/v2/runs/${claims.runId}`, grant)).status, 200);
    assert.equal((await call(address.port, 'GET', `/v2/runs/${claims.runId}`, grant)).status, 404);
  } finally {
    await runtime.close();
  }
});

test('refuses missing grants and wrong-run authority without leaking token data', async () => {
  const lines: string[] = [];
  const runtime = createRuntimeServer({
    config: config(),
    logSink: line => lines.push(line),
    nowEpochSeconds: () => TEST_NOW,
  });
  const address = await runtime.listen();
  const claims = claimsFixture();
  const grant = signGrant(claims);
  const otherGrant = signGrant(claimsFixture());
  try {
    assert.equal((await call(address.port, 'POST', '/v2/runs/reserve')).status, 401);
    assert.equal((await call(address.port, 'POST', '/v2/runs/reserve', grant)).status, 201);
    assert.equal((await call(
      address.port,
      'GET',
      `/v2/runs/${claims.runId}`,
      otherGrant,
    )).status, 403);
    assert.ok(lines.every(line => !line.includes(grant)));
  } finally {
    await runtime.close();
  }
});

test('runs token-authorized start, heartbeat, action, stop, and release without reusing the grant', async () => {
  const pool = new FakeRuntimeWorkerPool();
  const runtime = createRuntimeServer({
    config: config(), workerPool: pool, logSink: () => undefined,
    nowEpochSeconds: () => TEST_NOW,
  });
  const address = await runtime.listen();
  const claims = claimsFixture({
    capabilities: [
      'runtime.reserve', 'runtime.start', 'runtime.action', 'runtime.refresh',
      'runtime.stop', 'runtime.heartbeat', 'runtime.release',
    ],
  });
  const grant = signGrant(claims);
  try {
    const reserved = await call(address.port, 'POST', '/v2/runs/reserve', grant);
    const token = String(responseData(reserved).runAccessToken);
    const launch = {
      endpoint: 'https://example.test/',
      browser: { headless: true, args: ['--disable-popup-blocking'] },
    };
    assert.equal((await call(
      address.port, 'POST', `/v2/runs/${claims.runId}/start`, undefined, launch, token,
    )).status, 202);
    assert.equal((await call(
      address.port, 'POST', `/v2/runs/${claims.runId}/start`, undefined, launch, token,
    )).status, 200);
    assert.deepEqual(pool.lastDescriptor?.browser.args, ['--disable-popup-blocking']);
    assert.equal((await call(
      address.port, 'GET', `/v2/runs/${claims.runId}/heartbeat`, undefined, undefined, token,
    )).status, 200);
    const identity = await call(
      address.port, 'GET', `/v2/runs/${claims.runId}/page-identity`, undefined, undefined, token,
    );
    assert.deepEqual(responseData(identity), { pageKey: `url-v1:${'c'.repeat(64)}` });
    assert.ok(!JSON.stringify(identity.body).includes('example.test'));
    assert.equal((await call(
      address.port,
      'POST',
      `/v2/runs/${claims.runId}/actions`,
      undefined,
      {
        instructionId: 1733, sequence: 1, action: 'CLICK',
        pageKey: `url-v1:${'a'.repeat(64)}`, authoredSelectors: ['#login'],
        registryCandidates: [],
      },
      token,
    )).status, 200);
    assert.equal(pool.actionCount, 1);
    const recoveryScanner = await call(
      address.port,
      'POST',
      `/v2/runs/${claims.runId}/scanner`,
      undefined,
      { operation: 'url' },
      token,
    );
    assert.equal(recoveryScanner.status, 200);
    assert.equal(responseData(recoveryScanner).value, 'https://example.test/current');
    assert.equal(pool.scannerRpcCount, 1);
    assert.equal((await call(
      address.port,
      'POST',
      `/v2/runs/${claims.runId}/scanner`,
      undefined,
      { operation: 'url' },
      Buffer.alloc(32, 0x42).toString('base64url'),
    )).status, 403);
    assert.equal((await call(
      address.port,
      'POST',
      `/v2/runs/${claims.runId}/scanner`,
      undefined,
      { operation: 'unbounded-scanner-operation' },
      token,
    )).status, 400);
    assert.equal(pool.scannerRpcCount, 1);
    assert.equal((await call(
      address.port, 'POST', `/v2/runs/${claims.runId}/stop`, undefined, undefined, token,
    )).status, 200);
    assert.equal((await call(
      address.port, 'DELETE', `/v2/runs/${claims.runId}/release`, undefined, undefined, token,
    )).status, 200);
    assert.equal(pool.releaseCount, 1);
  } finally {
    await runtime.close();
  }
});

test('opens an owner-authorized scanner and uses only its opaque lease token for RPC and close', async () => {
  const pool = new FakeRuntimeWorkerPool();
  const lines: string[] = [];
  const runtime = createRuntimeServer({
    config: config(), workerPool: pool, logSink: line => lines.push(line),
    nowEpochSeconds: () => TEST_NOW,
  });
  const address = await runtime.listen();
  const claims = claimsFixture({ capabilities: ['runtime.action'] });
  const grant = signGrant(claims);
  try {
    const opened = await call(address.port, 'POST', '/v2/scanners/open', grant);
    assert.equal(opened.status, 201);
    const lease = responseData(opened);
    assert.equal(lease.scannerId, pool.scannerId);
    assert.equal(lease.scannerToken, pool.scannerToken);

    const rpc = await call(
      address.port,
      'POST',
      `/v2/scanners/${pool.scannerId}/rpc`,
      undefined,
      { operation: 'url' },
      pool.scannerToken,
    );
    assert.equal(rpc.status, 200);
    assert.equal(responseData(rpc).value, 'https://example.test/current');
    const tested = await call(
      address.port,
      'POST',
      `/v2/scanners/${pool.scannerId}/rpc`,
      undefined,
      { operation: 'test-element', action: 'CLICK', xpath: "//*[@id='login']", css: '', value: '' },
      pool.scannerToken,
    );
    assert.equal(tested.status, 200);
    assert.equal(responseData(tested).value, true);
    assert.equal(pool.scannerRpcCount, 2);
    assert.equal((await call(
      address.port,
      'POST',
      `/v2/scanners/${pool.scannerId}/rpc`,
      undefined,
      { operation: 'url' },
      'x'.repeat(43),
    )).status, 404);
    assert.equal((await call(
      address.port,
      'POST',
      `/v2/scanners/${pool.scannerId}/close`,
      undefined,
      undefined,
      pool.scannerToken,
    )).status, 200);
    assert.ok(lines.every(line => !line.includes(grant) && !line.includes(pool.scannerToken)));
  } finally {
    await runtime.close();
  }
});

test('refuses unbounded or malformed browser arguments before worker admission', async () => {
  const pool = new FakeRuntimeWorkerPool();
  const runtime = createRuntimeServer({
    config: config(), nowEpochSeconds: () => TEST_NOW, workerPool: pool,
  });
  const address = await runtime.listen();
  const claims = claimsFixture({ capabilities: ['runtime.reserve', 'runtime.start'] });
  try {
    const reserved = await call(address.port, 'POST', '/v2/runs/reserve', signGrant(claims));
    const token = String(responseData(reserved).runAccessToken);
    const response = await call(
      address.port,
      'POST',
      `/v2/runs/${claims.runId}/start`,
      undefined,
      { endpoint: 'https://example.test/', browser: { headless: false, args: ['user-data-dir=x'] } },
      token,
    );
    assert.equal(response.status, 400);
    assert.equal(pool.lastDescriptor, undefined);
  } finally {
    await runtime.close();
  }
});

test('refuses conflicting starts, unknown action fields, and wrong run tokens', async () => {
  const pool = new FakeRuntimeWorkerPool();
  const runtime = createRuntimeServer({
    config: config(), workerPool: pool, logSink: () => undefined,
    nowEpochSeconds: () => TEST_NOW,
  });
  const address = await runtime.listen();
  const claims = claimsFixture({
    capabilities: [
      'runtime.reserve', 'runtime.start', 'runtime.action', 'runtime.stop',
      'runtime.heartbeat', 'runtime.release',
    ],
  });
  try {
    const reserved = await call(address.port, 'POST', '/v2/runs/reserve', signGrant(claims));
    const token = String(responseData(reserved).runAccessToken);
    const launch = { endpoint: 'https://example.test/', browser: { headless: true } };
    await call(address.port, 'POST', `/v2/runs/${claims.runId}/start`, undefined, launch, token);
    assert.equal((await call(
      address.port, 'POST', `/v2/runs/${claims.runId}/start`, undefined,
      { ...launch, endpoint: 'https://other.test/' }, token,
    )).status, 409);
    assert.equal((await call(
      address.port, 'GET', `/v2/runs/${claims.runId}/heartbeat`, undefined, undefined,
      Buffer.alloc(32, 0x42).toString('base64url'),
    )).status, 403);
    assert.equal((await call(
      address.port, 'POST', `/v2/runs/${claims.runId}/actions`, undefined,
      {
        instructionId: 1733, sequence: 1, action: 'CLICK',
        pageKey: `url-v1:${'a'.repeat(64)}`, authoredSelectors: ['#login'],
        registryCandidates: [], unexpected: true,
      }, token,
    )).status, 400);
    assert.equal(pool.actionCount, 0);
  } finally {
    await runtime.close();
  }
});

test('an expired active lease stops and releases only its exact worker session', async () => {
  let now = TEST_NOW;
  const pool = new FakeRuntimeWorkerPool();
  const runtime = createRuntimeServer({
    config: config(), workerPool: pool, logSink: () => undefined,
    nowEpochSeconds: () => now,
  });
  const address = await runtime.listen();
  const claims = claimsFixture({
    capabilities: [
      'runtime.reserve', 'runtime.start', 'runtime.heartbeat', 'runtime.release',
    ],
  });
  try {
    const reserved = await call(address.port, 'POST', '/v2/runs/reserve', signGrant(claims));
    const token = String(responseData(reserved).runAccessToken);
    await call(address.port, 'POST', `/v2/runs/${claims.runId}/start`, undefined,
      { endpoint: 'https://example.test/', browser: { headless: true } }, token);
    now += 61;
    assert.equal((await call(address.port, 'GET', '/health/ready')).status, 200);
    await new Promise<void>(resolve => setImmediate(resolve));
    assert.equal(pool.stopCount, 1);
    assert.equal(pool.releaseCount, 1);
    assert.equal((await call(
      address.port, 'GET', `/v2/runs/${claims.runId}/heartbeat`, undefined, undefined, token,
    )).status, 404);
  } finally {
    await runtime.close();
  }
});

test('safe logger records bounded browser diagnostics without payload fields', () => {
  const lines: string[] = [];
  createSafeLogger(line => lines.push(line))({
    event: 'browser.action.settled', runId: 'run-1', browserId: 'browser-1',
    contextId: 'context-1', pageId: 'page-1', pageKey: `url-v1:${'a'.repeat(64)}`,
    instructionId: 17, action: 'CLICK', sequence: 3, code: 'COMPLETED',
    stage: 'AUTHORED', physicalAttempts: 1, frameValidated: true, shadowValidated: true,
    tagValidated: true, actionValidated: true, recoveryCandidateCount: 0,
    registryCandidateCount: 2, liveCandidateCount: 1, durationMs: 45,
    viewportWidth: 1920, viewportHeight: 1040, screenWidth: 1920, screenHeight: 1080,
    devicePixelRatio: 1, argumentCount: 2, viewportMode: 'native',
    serviceWorkerMode: 'allow', retained: false,
    outcome: 'STABLE', samples: 3, stableSamples: 2, readyState: 'complete', nodeCount: 239,
  });
  assert.equal(lines.length, 1);
  const logged = JSON.parse(lines[0] ?? '{}') as Record<string, unknown>;
  assert.equal(logged.event, 'browser.action.settled');
  assert.equal(logged.pageKey, `url-v1:${'a'.repeat(64)}`);
  assert.equal(logged.registryCandidateCount, 2);
  assert.equal(logged.physicalAttempts, 1);
  assert.equal(logged.frameValidated, true);
  assert.equal(logged.viewportWidth, 1920);
  assert.equal(logged.serviceWorkerMode, 'allow');
  assert.equal(logged.outcome, 'STABLE');
  assert.equal(logged.samples, 3);
  assert.equal(logged.stableSamples, 2);
  assert.equal(logged.readyState, 'complete');
  assert.equal(logged.nodeCount, 239);
  assert.equal('endpoint' in logged, false);
  assert.equal('locator' in logged, false);
  assert.equal('inputValue' in logged, false);
});

test('safe logger failure never changes runtime behavior', () => {
  const logger = createSafeLogger(() => { throw new Error('disk unavailable'); });
  assert.doesNotThrow(() => logger({ event: 'browser.launch.requested', runId: 'run-1' }));
});
