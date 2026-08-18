import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ExecutionRegistry, ExecutionRegistryError } from '../src/coordinator/executionRegistry';
import { TEST_NOW, claimsFixture } from './testSupport';

const TOKEN_A = 'A'.repeat(43);
const TOKEN_B = Buffer.alloc(32, 0x42).toString('base64url');

test('reserves exact grants idempotently and rejects conflicting run ownership', () => {
  const registry = new ExecutionRegistry(2, () => TEST_NOW);
  const claims = claimsFixture();
  assert.equal(registry.reserve(claims, 'fingerprint-a').created, true);
  assert.equal(registry.reserve(claims, 'fingerprint-a').created, false);
  assert.throws(
    () => registry.reserve({ ...claims, jti: '10000000-0000-4000-8000-000000000001' }, 'fingerprint-b'),
    (error: unknown) => error instanceof ExecutionRegistryError
      && error.code === 'RUN_ID_CONFLICT',
  );
  assert.throws(
    () => registry.getAuthorized(claims.runId, claims, 'fingerprint-b'),
    (error: unknown) => error instanceof ExecutionRegistryError
      && error.code === 'RUN_AUTHORITY_MISMATCH',
  );
});

test('enforces capacity and removes expired reservations', () => {
  let now = TEST_NOW;
  const registry = new ExecutionRegistry(1, () => now);
  const first = claimsFixture({ exp: TEST_NOW + 10 });
  registry.reserve(first, 'fingerprint-a');
  assert.throws(
    () => registry.reserve(claimsFixture(), 'fingerprint-b'),
    (error: unknown) => error instanceof ExecutionRegistryError
      && error.code === 'RUNTIME_CAPACITY_REACHED',
  );
  now = TEST_NOW + 10;
  assert.equal(registry.sweepExpired(), 1);
  assert.equal(registry.reserve(claimsFixture(), 'fingerprint-c').created, true);
});

test('release requires the exact admitted grant', () => {
  const registry = new ExecutionRegistry(1, () => TEST_NOW);
  const claims = claimsFixture();
  registry.reserve(claims, 'fingerprint-a');
  assert.throws(
    () => registry.release(claims.runId, claims, 'fingerprint-b'),
    (error: unknown) => error instanceof ExecutionRegistryError
      && error.code === 'RUN_AUTHORITY_MISMATCH',
  );
  assert.equal(registry.release(claims.runId, claims, 'fingerprint-a').runId, claims.runId);
  assert.equal(registry.size(), 0);
});

test('activates a capability-bound run token and renews an idle lease beyond grant expiry', () => {
  let now = TEST_NOW;
  const registry = new ExecutionRegistry(1, () => now, () => TOKEN_A);
  const claims = claimsFixture({
    exp: TEST_NOW + 10,
    capabilities: [
      'runtime.reserve', 'runtime.start', 'runtime.action', 'runtime.heartbeat', 'runtime.release',
    ],
  });
  const reservation = registry.reserve(claims, 'fingerprint-a');
  assert.equal(reservation.runAccessToken, TOKEN_A);
  assert.equal(registry.reserve(claims, 'fingerprint-a').runAccessToken, TOKEN_A);

  registry.activateRunLease(claims.runId, TOKEN_A, 60);
  now = TEST_NOW + 30;
  assert.equal(
    registry.authorizeActiveRun(claims.runId, TOKEN_A, 'runtime.action', 60).runId,
    claims.runId,
  );
  now = TEST_NOW + 80;
  assert.equal(
    registry.authorizeActiveRun(claims.runId, TOKEN_A, 'runtime.heartbeat', 60).runId,
    claims.runId,
  );
  assert.equal(registry.size(), 1);

  assert.throws(
    () => registry.authorizeActiveRun(claims.runId, TOKEN_B, 'runtime.action', 60),
    (error: unknown) => error instanceof ExecutionRegistryError
      && error.code === 'RUN_AUTHORITY_MISMATCH',
  );
  assert.throws(
    () => registry.authorizeActiveRun(claims.runId, TOKEN_A, 'runtime.refresh', 60),
    (error: unknown) => error instanceof ExecutionRegistryError
      && error.code === 'RUN_CAPABILITY_MISSING',
  );

  now = TEST_NOW + 141;
  assert.equal(registry.sweepExpired(), 1);
});

test('an unactivated reservation still expires with its short-lived grant', () => {
  let now = TEST_NOW;
  const registry = new ExecutionRegistry(1, () => now, () => TOKEN_A);
  registry.reserve(claimsFixture({ exp: TEST_NOW + 10 }), 'fingerprint-a');
  now = TEST_NOW + 10;
  assert.equal(registry.sweepExpired(), 1);
});
