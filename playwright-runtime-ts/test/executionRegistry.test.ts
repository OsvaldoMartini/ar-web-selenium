import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ExecutionRegistry, ExecutionRegistryError } from '../src/coordinator/executionRegistry';
import { TEST_NOW, claimsFixture } from './testSupport';

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
