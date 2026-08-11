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
import { createSafeLogger, SafeLogSink } from './logging/safeLogger';
import { ExecutionGrantError, ExecutionGrantVerifier } from './security/executionGrantVerifier';

const RUNTIME_VERSION = '0.1.0';
const MAX_REQUEST_BODY_BYTES = 1024;

interface RuntimeServerOptions {
  readonly config?: RuntimeConfig;
  readonly logSink?: SafeLogSink;
  readonly nowEpochSeconds?: () => number;
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

const bearerGrant = (request: IncomingMessage): string => {
  const value = request.headers.authorization;
  if (!value || Array.isArray(value) || !value.startsWith('Bearer ')) {
    throw new ExecutionGrantError('GRANT_REQUIRED');
  }
  const grant = value.slice('Bearer '.length);
  if (grant.length === 0 || grant.includes(' ')) throw new ExecutionGrantError('GRANT_REQUIRED');
  return grant;
};

const errorStatus = (code: string): number => {
  if (code === 'RUNTIME_NOT_READY') return 503;
  if (code === 'RUNTIME_CAPACITY_REACHED') return 429;
  if (code === 'RUN_NOT_FOUND') return 404;
  if (code === 'RUN_AUTHORITY_MISMATCH') return 403;
  if (code.endsWith('_CONFLICT')) return 409;
  if (code === 'REQUEST_BODY_TOO_LARGE') return 413;
  if (code.startsWith('GRANT_')) return 401;
  return 500;
};

export const createRuntimeServer = (options: RuntimeServerOptions = {}) => {
  const config = options.config ?? loadRuntimeConfig();
  const log = createSafeLogger(options.logSink);
  const registry = new ExecutionRegistry(config.maxReservedRuns, options.nowEpochSeconds);
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
          browserActionsEnabled: false,
        });
        return;
      }
      if (!verifier) throw new ExecutionGrantError('RUNTIME_NOT_READY');

      const verified = verifier.verify(bearerGrant(request));
      if (method === 'POST' && path === '/v2/runs/reserve') {
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

      const runMatch = /^\/v2\/runs\/([0-9a-f-]+)$/i.exec(path);
      const runId = runMatch?.[1];
      if (!runId || !UUID_PATTERN.test(runId)) {
        failure(response, 404, 'ROUTE_NOT_FOUND', 'The runtime route does not exist.');
        return;
      }
      if (method === 'GET') {
        requireCapability(verified.claims, 'runtime.bootstrap');
        success(response, 200, registry.getAuthorized(
          runId, verified.claims, verified.fingerprint,
        ));
        return;
      }
      if (method === 'DELETE') {
        requireCapability(verified.claims, 'runtime.release');
        await discardSmallBody(request);
        const released = registry.release(runId, verified.claims, verified.fingerprint);
        log({ event: 'run.released', runId, count: registry.size() });
        success(response, 200, released);
        return;
      }
      failure(response, 405, 'METHOD_NOT_ALLOWED', 'The HTTP method is not supported.');
    } catch (error) {
      const code = error instanceof ExecutionGrantError || error instanceof ExecutionRegistryError
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
      if (!server.listening) return;
      await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
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
