import { Buffer } from 'node:buffer';
import { createHmac, randomUUID } from 'node:crypto';
import {
  EXECUTION_CONTRACT_VERSION,
  EXECUTION_GRANT_ALGORITHM,
  EXECUTION_GRANT_AUDIENCE,
  EXECUTION_GRANT_ISSUER,
  EXECUTION_GRANT_TYPE,
  EXECUTION_RUNTIME,
  ExecutionGrantClaims,
} from '../src/contracts/executionContracts';

export const TEST_SECRET = Buffer.alloc(32, 0x5a);
export const TEST_NOW = 1_786_400_000;

export const claimsFixture = (
  overrides: Partial<ExecutionGrantClaims> = {},
): ExecutionGrantClaims => ({
  v: EXECUTION_CONTRACT_VERSION,
  iss: EXECUTION_GRANT_ISSUER,
  aud: EXECUTION_GRANT_AUDIENCE,
  jti: randomUUID(),
  runId: randomUUID(),
  organizationId: 13,
  homeBankingId: 13,
  botJobId: 29,
  workspaceEpoch: 7,
  graphRevision: 'a'.repeat(64),
  planRevision: 'b'.repeat(64),
  dataMode: 'REAL',
  runtime: EXECUTION_RUNTIME,
  capabilities: ['runtime.reserve', 'runtime.bootstrap', 'runtime.release'],
  iat: TEST_NOW,
  nbf: TEST_NOW,
  exp: TEST_NOW + 90,
  ...overrides,
});

export const signGrant = (
  claims: ExecutionGrantClaims,
  secret: Buffer = TEST_SECRET,
  keyId = 'v1',
): string => {
  const header = Buffer.from(JSON.stringify({
    alg: EXECUTION_GRANT_ALGORITHM,
    typ: EXECUTION_GRANT_TYPE,
    kid: keyId,
  })).toString('base64url');
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  const signature = createHmac('sha256', secret)
    .update(`${header}.${payload}`, 'ascii')
    .digest('base64url');
  return `${header}.${payload}.${signature}`;
};
