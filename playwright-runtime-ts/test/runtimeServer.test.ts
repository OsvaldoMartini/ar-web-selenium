import assert from 'node:assert/strict';
import { request } from 'node:http';
import { test } from 'node:test';
import { RuntimeConfig } from '../src/config/runtimeConfig';
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
): Promise<HttpResult> => new Promise((resolve, reject) => {
  const client = request({
    host: '127.0.0.1',
    port,
    method,
    path,
    headers: grant ? { authorization: `Bearer ${grant}` } : {},
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
  client.end();
});

const config = (secret = true): RuntimeConfig => ({
  host: '127.0.0.1',
  port: 0,
  grantKeyId: 'v1',
  ...(secret ? { grantSecret: TEST_SECRET } : {}),
  maxReservedRuns: 2,
  maxGrantSeconds: 120,
  clockSkewSeconds: 5,
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
