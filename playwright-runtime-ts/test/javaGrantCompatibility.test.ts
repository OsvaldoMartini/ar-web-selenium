import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { ExecutionGrantClaims } from '../src/contracts/executionContracts';
import { ExecutionGrantVerifier } from '../src/security/executionGrantVerifier';

interface JavaGrantFixture {
  readonly secretBase64Url: string;
  readonly keyId: string;
  readonly claims: ExecutionGrantClaims;
  readonly compactGrant: string;
}

test('accepts the deterministic compact grant emitted by the Java signer', () => {
  const fixture = JSON.parse(readFileSync(
    'fixtures/java-hs256-grant-v1.json', 'utf8',
  )) as JavaGrantFixture;
  const verifier = new ExecutionGrantVerifier({
    keyId: fixture.keyId,
    secret: Buffer.from(fixture.secretBase64Url, 'base64url'),
    maxLifetimeSeconds: 120,
    clockSkewSeconds: 5,
    nowEpochSeconds: () => fixture.claims.iat,
  });

  const verified = verifier.verify(fixture.compactGrant);
  assert.deepEqual(verified.claims, fixture.claims);
});
