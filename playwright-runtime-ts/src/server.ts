import { createHash } from 'node:crypto';
import { IncomingMessage, ServerResponse, createServer } from 'node:http';
import { AddressInfo } from 'node:net';
import { RuntimeConfig, loadRuntimeConfig } from './config/runtimeConfig';
import {
  EXECUTION_CONTRACT_VERSION,
  RuntimeEnvelope,
  requireCapability,
  UUID_PATTERN,
} from './contracts/executionContracts';
import { ExecutionRegistry, ExecutionRegistryError } from './coordinator/executionRegistry';
import { parsePhysicalActionRequest, PhysicalActionRequest, PhysicalActionResult } from './action/actionContracts';
import { PlaywrightBrowserFactory } from './browser/playwrightBrowserFactory';
import { createSafeLogger, SafeLogSink } from './logging/safeLogger';
import { PlaywrightWorkerPool, PlaywrightWorkerPoolError } from './pool/playwrightWorkerPool';
import { ExecutionGrantError, ExecutionGrantVerifier } from './security/executionGrantVerifier';
import { ExecutionLaunchDescriptor, ExecutionSessionSnapshot } from './session/sessionContracts';

const RUNTIME_VERSION = '0.1.0';
const MAX_REQUEST_BODY_BYTES = 1024;
const MAX_START_BODY_BYTES = 4_096;
const MAX_ACTION_BODY_BYTES = 8 * 1_024 * 1_024;
const PAGE_KEY_PATTERN = /^url-v1:[0-9a-f]{64}$/;

interface RuntimeWorkerPool {
  enqueue(descriptor: ExecutionLaunchDescriptor): ExecutionSessionSnapshot;
  snapshot(runId: string): ExecutionSessionSnapshot;
  refresh(runId: string): Promise<ExecutionSessionSnapshot>;
  pageIdentity(runId: string): Promise<string>;
  perform(runId: string, request: PhysicalActionRequest): Promise<PhysicalActionResult>;
  stop(runId: string): Promise<ExecutionSessionSnapshot>;
  release(runId: string): void;
  closeAll(): Promise<void>;
}

interface RuntimeServerOptions {
  readonly config?: RuntimeConfig;
  readonly logSink?: SafeLogSink;
  readonly nowEpochSeconds?: () => number;
  readonly workerPool?: RuntimeWorkerPool;
}

const sendJson = <T>(response: ServerResponse, status: number, payload: RuntimeEnvelope<T>): void => {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
  });
  response.end(body);
};

const failure = (response: ServerResponse, status: number, code: string, message: string): void =>
  sendJson(response, status, { ok: false, code, message });

const success = <T>(response: ServerResponse, status: number, data: T): void =>
  sendJson(response, status, { ok: true, data });

const discardSmallBody = async (request: IncomingMessage): Promise<void> => {
  let size = 0;
  for await (const chunk of request) {
    size += Buffer.byteLength(chunk as Buffer);
    if (size > MAX_REQUEST_BODY_BYTES) throw new Error('REQUEST_BODY_TOO_LARGE');
  }
};

const readJsonBody = async (request: IncomingMessage, maximumBytes: number): Promise<unknown> => {
  if (request.headers['content-type']?.split(';', 1)[0]?.trim().toLowerCase()
      !== 'application/json') throw new Error('REQUEST_CONTENT_TYPE_INVALID');
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const bytes = Buffer.from(chunk as Buffer);
    size += bytes.length;
    if (size > maximumBytes) throw new Error('REQUEST_BODY_TOO_LARGE');
    chunks.push(bytes);
  }
  if (size === 0) throw new Error('REQUEST_BODY_INVALID');
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown;
  } catch {
    throw new Error('REQUEST_BODY_INVALID');
  }
};

const bearerGrant = (request: IncomingMessage): string => {
  const value = request.headers.authorization;
  if (!value || Array.isArray(value) || !value.startsWith('Bearer ')) {
    throw new ExecutionGrantError('GRANT_REQUIRED');
  }
  const grant = value.slice('Bearer '.length);
  if (grant.length === 0 || grant.includes(' ')) throw new ExecutionGrantError('GRANT_REQUIRED');
  return grant;
};

const runAccessToken = (request: IncomingMessage): string => {
  const value = request.headers['x-arweb-run-token'];
  if (!value || Array.isArray(value)) throw new ExecutionRegistryError('RUN_TOKEN_REQUIRED');
  return value;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const parseLaunchBody = (value: unknown): Omit<ExecutionLaunchDescriptor, 'run'> => {
  if (!isRecord(value) || Object.keys(value).some(key => !['endpoint', 'browser'].includes(key))
      || Object.keys(value).length !== 2 || typeof value.endpoint !== 'string'
      || !isRecord(value.browser)
      || Object.keys(value.browser).some(key => !['headless', 'channel'].includes(key))
      || typeof value.browser.headless !== 'boolean'
      || (value.browser.channel !== undefined
        && !['chrome', 'msedge', 'chromium'].includes(String(value.browser.channel)))) {
    throw new Error('RUN_START_CONTRACT_INVALID');
  }
  return {
    endpoint: value.endpoint,
    browser: {
      headless: value.browser.headless,
      ...(value.browser.channel === undefined
        ? {}
        : { channel: value.browser.channel as 'chrome' | 'msedge' | 'chromium' }),
    },
  };
};

const launchFingerprint = (launch: Omit<ExecutionLaunchDescriptor, 'run'>): string =>
  createHash('sha256').update(JSON.stringify(launch), 'utf8').digest('hex');

const errorStatus = (code: string): number => {
  if (code === 'RUNTIME_NOT_READY') return 503;
  if (code === 'RUNTIME_CAPACITY_REACHED') return 429;
  if (code === 'RUN_NOT_FOUND') return 404;
  if (code === 'RUN_AUTHORITY_MISMATCH' || code === 'RUN_CAPABILITY_MISSING') return 403;
  if (code === 'RUN_TOKEN_REQUIRED') return 401;
  if (code === 'WORKER_QUEUE_CAPACITY_REACHED') return 429;
  if (code.endsWith('_CONFLICT')) return 409;
  if (code === 'RUN_ACTIVE_RELEASE_REQUIRES_TOKEN' || code === 'RUN_NOT_ACTIVE') return 409;
  if (code === 'REQUEST_BODY_TOO_LARGE') return 413;
  if (code.endsWith('_INVALID') || code.startsWith('ACTION_')) return 400;
  if (code.startsWith('GRANT_')) return 401;
  return 500;
};

export const createRuntimeServer = (options: RuntimeServerOptions = {}) => {
  const config = options.config ?? loadRuntimeConfig();
  const log = createSafeLogger(options.logSink);
  const workerPool: RuntimeWorkerPool = options.workerPool ?? new PlaywrightWorkerPool(
    new PlaywrightBrowserFactory(),
    {
      maximumActiveRuns: config.maximumActiveRuns,
      maximumQueuedRuns: config.maximumQueuedRuns,
      maximumActiveRunsPerOrganization: config.maximumActiveRunsPerOrganization,
      maximumActiveRunsPerBotJob: config.maximumActiveRunsPerBotJob,
    },
  );
  const registry = new ExecutionRegistry(
    config.maxReservedRuns,
    options.nowEpochSeconds,
    undefined,
    runId => {
      void workerPool.stop(runId)
        .catch(() => undefined)
        .then(() => {
          try {
            workerPool.release(runId);
          } catch {
            // An unactivated reservation has no worker entry. Active entries are best-effort
            // stopped and released when their authority lease expires.
          }
        });
    },
  );
  const verifier = config.grantSecret
    ? new ExecutionGrantVerifier({
      keyId: config.grantKeyId,
      secret: config.grantSecret,
      maxLifetimeSeconds: config.maxGrantSeconds,
      clockSkewSeconds: config.clockSkewSeconds,
      ...(options.nowEpochSeconds ? { nowEpochSeconds: options.nowEpochSeconds } : {}),
    })
    : undefined;

  const server = createServer(async (request, response) => {
    const started = Date.now();
    const method = request.method ?? '';
    const path = new URL(request.url ?? '/', 'http://127.0.0.1').pathname;
    try {
      if (method === 'GET' && path === '/health/live') {
        success(response, 200, { status: 'LIVE', version: RUNTIME_VERSION });
        return;
      }
      if (method === 'GET' && path === '/health/ready') {
        if (!verifier) {
          failure(response, 503, 'RUNTIME_NOT_READY', 'Execution grant verification is not configured.');
          return;
        }
        success(response, 200, {
          status: 'READY',
          version: RUNTIME_VERSION,
          reservedRuns: registry.size(),
          capacity: config.maxReservedRuns,
        });
        return;
      }
      if (method === 'GET' && path === '/version') {
        success(response, 200, {
          version: RUNTIME_VERSION,
          contractVersion: EXECUTION_CONTRACT_VERSION,
          browserActionsEnabled: true,
        });
        return;
      }
      if (!verifier) throw new ExecutionGrantError('RUNTIME_NOT_READY');

      if (method === 'POST' && path === '/v2/runs/reserve') {
        const verified = verifier.verify(bearerGrant(request));
        requireCapability(verified.claims, 'runtime.reserve');
        await discardSmallBody(request);
        const reservation = registry.reserve(verified.claims, verified.fingerprint);
        log({
          event: reservation.created ? 'run.reserved' : 'run.reserve.replayed',
          runId: reservation.run.runId,
          count: registry.size(),
        });
        success(response, reservation.created ? 201 : 200, reservation);
        return;
      }

      const operationMatch = /^\/v2\/runs\/([0-9a-f-]+)\/(start|session|actions|page-identity|refresh|stop|heartbeat|release)$/i.exec(path);
      const operationRunId = operationMatch?.[1];
      const operation = operationMatch?.[2]?.toLowerCase();
      if (operationRunId && UUID_PATTERN.test(operationRunId) && operation) {
        const token = runAccessToken(request);
        if (method === 'POST' && operation === 'start') {
          registry.authorizeRunStart(operationRunId, token);
          const launch = parseLaunchBody(await readJsonBody(request, MAX_START_BODY_BYTES));
          const fingerprint = launchFingerprint(launch);
          const activation = registry.activateRunLease(
            operationRunId, token, config.runIdleLeaseSeconds, fingerprint,
          );
          try {
            const snapshot = activation.created
              ? workerPool.enqueue({ run: activation.run, ...launch })
              : workerPool.snapshot(operationRunId);
            success(response, activation.created ? 202 : 200, snapshot);
          } catch (error) {
            if (activation.created) registry.cancelRunActivation(operationRunId, fingerprint);
            throw error;
          }
          return;
        }
        if (method === 'GET' && (operation === 'session' || operation === 'heartbeat')) {
          registry.authorizeActiveRun(
            operationRunId, token, 'runtime.heartbeat', config.runIdleLeaseSeconds,
          );
          success(response, 200, workerPool.snapshot(operationRunId));
          return;
        }
        if (method === 'POST' && operation === 'actions') {
          registry.authorizeActiveRun(
            operationRunId, token, 'runtime.action', config.runIdleLeaseSeconds,
          );
          const action = parsePhysicalActionRequest(
            await readJsonBody(request, MAX_ACTION_BODY_BYTES),
          );
          success(response, 200, await workerPool.perform(operationRunId, action));
          return;
        }
        if (method === 'GET' && operation === 'page-identity') {
          registry.authorizeActiveRun(
            operationRunId, token, 'runtime.action', config.runIdleLeaseSeconds,
          );
          const pageKey = await workerPool.pageIdentity(operationRunId);
          if (!PAGE_KEY_PATTERN.test(pageKey)) throw new Error('PAGE_IDENTITY_INVALID');
          success(response, 200, { pageKey });
          return;
        }
        if (method === 'POST' && operation === 'refresh') {
          await discardSmallBody(request);
          registry.authorizeActiveRun(
            operationRunId, token, 'runtime.refresh', config.runIdleLeaseSeconds,
          );
          success(response, 200, await workerPool.refresh(operationRunId));
          return;
        }
        if (method === 'POST' && operation === 'stop') {
          await discardSmallBody(request);
          registry.authorizeActiveRun(
            operationRunId, token, 'runtime.stop', config.runIdleLeaseSeconds,
          );
          success(response, 200, await workerPool.stop(operationRunId));
          return;
        }
        if (method === 'DELETE' && operation === 'release') {
          await discardSmallBody(request);
          registry.authorizeActiveRun(
            operationRunId, token, 'runtime.release', config.runIdleLeaseSeconds,
          );
          workerPool.release(operationRunId);
          success(response, 200, registry.releaseWithRunToken(operationRunId, token));
          return;
        }
        failure(response, 405, 'METHOD_NOT_ALLOWED', 'The HTTP method is not supported.');
        return;
      }

      const runMatch = /^\/v2\/runs\/([0-9a-f-]+)$/i.exec(path);
      const runId = runMatch?.[1];
      if (!runId || !UUID_PATTERN.test(runId)) {
        failure(response, 404, 'ROUTE_NOT_FOUND', 'The runtime route does not exist.');
        return;
      }
      if (method === 'GET') {
        const verified = verifier.verify(bearerGrant(request));
        requireCapability(verified.claims, 'runtime.bootstrap');
        success(response, 200, registry.getAuthorized(
          runId, verified.claims, verified.fingerprint,
        ));
        return;
      }
      if (method === 'DELETE') {
        const verified = verifier.verify(bearerGrant(request));
        requireCapability(verified.claims, 'runtime.release');
        await discardSmallBody(request);
        try {
          workerPool.snapshot(runId);
          throw new ExecutionRegistryError('RUN_ACTIVE_RELEASE_REQUIRES_TOKEN');
        } catch (error) {
          if (!(error instanceof PlaywrightWorkerPoolError)
              || error.code !== 'SESSION_NOT_FOUND') throw error;
        }
        const released = registry.release(runId, verified.claims, verified.fingerprint);
        log({ event: 'run.released', runId, count: registry.size() });
        success(response, 200, released);
        return;
      }
      failure(response, 405, 'METHOD_NOT_ALLOWED', 'The HTTP method is not supported.');
    } catch (error) {
      const code = error instanceof ExecutionGrantError
        || error instanceof ExecutionRegistryError
        || error instanceof PlaywrightWorkerPoolError
        ? error.code
        : error instanceof Error ? error.message : 'RUNTIME_FAILURE';
      log({ event: 'request.refused', level: 'WARN', code, durationMs: Date.now() - started });
      failure(response, errorStatus(code), code, 'The runtime request was refused.');
    }
  });

  server.requestTimeout = 5_000;
  server.headersTimeout = 5_000;
  server.keepAliveTimeout = 2_000;
  const sweepTimer = setInterval(() => {
    const removed = registry.sweepExpired();
    if (removed > 0) log({ event: 'run.expired', count: removed });
  }, 30_000);
  sweepTimer.unref();
  server.on('close', () => clearInterval(sweepTimer));

  return {
    server,
    registry,
    async listen(): Promise<AddressInfo> {
      await new Promise<void>((resolve, reject) => {
        server.once('error', reject);
        server.listen(config.port, config.host, () => {
          server.off('error', reject);
          resolve();
        });
      });
      const address = server.address();
      if (!address || typeof address === 'string') throw new Error('RUNTIME_ADDRESS_INVALID');
      log({ event: 'runtime.listening' });
      return address;
    },
    async close(): Promise<void> {
      await workerPool.closeAll();
      if (server.listening) {
        await new Promise<void>((resolve, reject) => server.close(
          error => error ? reject(error) : resolve(),
        ));
      }
    },
  };
};

const runFromCommandLine = async (): Promise<void> => {
  const runtime = createRuntimeServer();
  const address = await runtime.listen();
  process.stdout.write(JSON.stringify({
    timestamp: new Date().toISOString(),
    level: 'INFO',
    event: 'runtime.process.started',
    host: address.address,
    port: address.port,
  }) + '\n');

  let stopping = false;
  const stop = async (signal: string) => {
    if (stopping) return;
    stopping = true;
    process.stdout.write(JSON.stringify({
      timestamp: new Date().toISOString(), level: 'INFO', event: 'runtime.stopping', signal,
    }) + '\n');
    await runtime.close();
  };
  process.once('SIGINT', () => void stop('SIGINT'));
  process.once('SIGTERM', () => void stop('SIGTERM'));
};

if (require.main === module) {
  void runFromCommandLine().catch(error => {
    process.stderr.write(JSON.stringify({
      timestamp: new Date().toISOString(),
      level: 'ERROR',
      event: 'runtime.start.failed',
      code: error instanceof Error ? error.message : 'RUNTIME_START_FAILED',
    }) + '\n');
    process.exitCode = 1;
  });
}
