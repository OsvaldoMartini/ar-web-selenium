import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ExecutionGrantError, ExecutionGrantVerifier } from '../src/security/executionGrantVerifier';
import { TEST_NOW, TEST_SECRET, claimsFixture, signGrant } from './testSupport';

const verifier = () => new ExecutionGrantVerifier({
  keyId: 'v1',
  secret: TEST_SECRET,
  maxLifetimeSeconds: 120,
  clockSkewSeconds: 5,
  nowEpochSeconds: () => TEST_NOW,
});

test('verifies one bounded, correctly signed execution grant', () => {
  const claims = claimsFixture();
  const result = verifier().verify(signGrant(claims));
  assert.deepEqual(result.claims, claims);
  assert.match(result.fingerprint, /^[0-9a-f]{64}$/);
});

test('rejects a tampered payload without exposing its content', () => {
  const compact = signGrant(claimsFixture());
  const [header, payload, signature] = compact.split('.');
  assert.ok(header && payload && signature);
  const tampered = `${header}.${payload.slice(0, -1)}A.${signature}`;
  assert.throws(
    () => verifier().verify(tampered),
    (error: unknown) => error instanceof ExecutionGrantError
      && error.code === 'GRANT_SIGNATURE_INVALID',
  );
});

test('rejects expired and overlong grants', () => {
  assert.throws(
    () => verifier().verify(signGrant(claimsFixture({ exp: TEST_NOW - 10 }))),
    (error: unknown) => error instanceof ExecutionGrantError
      && error.code === 'GRANT_TIME_INVALID',
  );
  assert.throws(
    () => verifier().verify(signGrant(claimsFixture({ exp: TEST_NOW + 121 }))),
    (error: unknown) => error instanceof ExecutionGrantError
      && error.code === 'GRANT_TIME_INVALID',
  );
  assert.throws(
    () => verifier().verify(signGrant(claimsFixture({
      iat: TEST_NOW - 30,
      nbf: TEST_NOW - 30,
      exp: TEST_NOW,
    }))),
    (error: unknown) => error instanceof ExecutionGrantError
      && error.code === 'GRANT_EXPIRED',
  );
});

test('rejects unknown claims even when signed', () => {
  const claims = { ...claimsFixture(), injected: 'not-allowed' };
  assert.throws(
    () => verifier().verify(signGrant(claims as never)),
    (error: unknown) => error instanceof ExecutionGrantError
      && error.code === 'GRANT_CLAIMS_INVALID',
  );
});
