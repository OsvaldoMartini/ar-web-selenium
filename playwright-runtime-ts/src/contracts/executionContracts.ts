export const EXECUTION_CONTRACT_VERSION = 1 as const;
export const EXECUTION_GRANT_TYPE = 'ARWEB-EXECUTION-GRANT' as const;
export const EXECUTION_GRANT_ALGORITHM = 'HS256' as const;
export const EXECUTION_GRANT_ISSUER = 'arweb-java-gateway' as const;
export const EXECUTION_GRANT_AUDIENCE = 'arweb-playwright-runtime' as const;
export const EXECUTION_RUNTIME = 'TYPESCRIPT_PLAYWRIGHT_V2' as const;

export const EXECUTION_CAPABILITIES = [
  'runtime.reserve',
  'runtime.bootstrap',
  'runtime.release',
  'runtime.start',
  'runtime.action',
  'runtime.refresh',
  'runtime.stop',
  'runtime.heartbeat',
] as const;

export type ExecutionCapability = (typeof EXECUTION_CAPABILITIES)[number];
export type ExecutionDataMode = 'REAL' | 'SYNTHETIC';

export interface ExecutionGrantHeader {
  readonly alg: typeof EXECUTION_GRANT_ALGORITHM;
  readonly typ: typeof EXECUTION_GRANT_TYPE;
  readonly kid: string;
}

export interface ExecutionGrantClaims {
  readonly v: typeof EXECUTION_CONTRACT_VERSION;
  readonly iss: typeof EXECUTION_GRANT_ISSUER;
  readonly aud: typeof EXECUTION_GRANT_AUDIENCE;
  readonly jti: string;
  readonly runId: string;
  readonly organizationId: number;
  readonly homeBankingId: number;
  readonly botJobId: number;
  readonly workspaceEpoch: number;
  readonly graphRevision: string;
  readonly planRevision: string;
  readonly dataMode: ExecutionDataMode;
  readonly runtime: typeof EXECUTION_RUNTIME;
  readonly capabilities: readonly ExecutionCapability[];
  readonly iat: number;
  readonly nbf: number;
  readonly exp: number;
}

export interface RuntimeErrorEnvelope {
  readonly ok: false;
  readonly code: string;
  readonly message: string;
}

export interface RuntimeSuccessEnvelope<T> {
  readonly ok: true;
  readonly data: T;
}

export type RuntimeEnvelope<T> = RuntimeSuccessEnvelope<T> | RuntimeErrorEnvelope;

export type ReservedRunState = 'RESERVED';

export interface ReservedRunView {
  readonly runId: string;
  readonly organizationId: number;
  readonly homeBankingId: number;
  readonly botJobId: number;
  readonly workspaceEpoch: number;
  readonly graphRevision: string;
  readonly planRevision: string;
  readonly dataMode: ExecutionDataMode;
  readonly state: ReservedRunState;
  readonly createdAt: string;
  readonly expiresAt: string;
}

export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export const SHA256_PATTERN = /^[0-9a-f]{64}$/;

const HEADER_KEYS = new Set(['alg', 'typ', 'kid']);
const CLAIM_KEYS = new Set([
  'v', 'iss', 'aud', 'jti', 'runId', 'organizationId', 'homeBankingId', 'botJobId',
  'workspaceEpoch', 'graphRevision', 'planRevision', 'dataMode', 'runtime', 'capabilities',
  'iat', 'nbf', 'exp',
]);
const CAPABILITY_SET = new Set<string>(EXECUTION_CAPABILITIES);

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const hasExactKeys = (value: Record<string, unknown>, allowed: ReadonlySet<string>): boolean =>
  Object.keys(value).every(key => allowed.has(key)) && Object.keys(value).length === allowed.size;

const isPositiveSafeInteger = (value: unknown): value is number =>
  Number.isSafeInteger(value) && Number(value) > 0;

const isEpochSecond = (value: unknown): value is number =>
  Number.isSafeInteger(value) && Number(value) >= 0;

export const parseExecutionGrantHeader = (value: unknown): ExecutionGrantHeader => {
  if (!isRecord(value) || !hasExactKeys(value, HEADER_KEYS)) {
    throw new Error('GRANT_HEADER_INVALID');
  }
  if (value.alg !== EXECUTION_GRANT_ALGORITHM || value.typ !== EXECUTION_GRANT_TYPE) {
    throw new Error('GRANT_HEADER_INVALID');
  }
  if (typeof value.kid !== 'string' || !/^[A-Za-z0-9._-]{1,64}$/.test(value.kid)) {
    throw new Error('GRANT_KEY_ID_INVALID');
  }
  return value as unknown as ExecutionGrantHeader;
};

export const parseExecutionGrantClaims = (value: unknown): ExecutionGrantClaims => {
  if (!isRecord(value) || !hasExactKeys(value, CLAIM_KEYS)) {
    throw new Error('GRANT_CLAIMS_INVALID');
  }
  if (value.v !== EXECUTION_CONTRACT_VERSION
      || value.iss !== EXECUTION_GRANT_ISSUER
      || value.aud !== EXECUTION_GRANT_AUDIENCE
      || value.runtime !== EXECUTION_RUNTIME) {
    throw new Error('GRANT_CONTRACT_INVALID');
  }
  if (typeof value.jti !== 'string' || !UUID_PATTERN.test(value.jti)
      || typeof value.runId !== 'string' || !UUID_PATTERN.test(value.runId)) {
    throw new Error('GRANT_IDENTITY_INVALID');
  }
  if (!isPositiveSafeInteger(value.organizationId)
      || !isPositiveSafeInteger(value.homeBankingId)
      || !isPositiveSafeInteger(value.botJobId)
      || !isPositiveSafeInteger(value.workspaceEpoch)) {
    throw new Error('GRANT_OWNER_INVALID');
  }
  if (typeof value.graphRevision !== 'string' || !SHA256_PATTERN.test(value.graphRevision)
      || typeof value.planRevision !== 'string' || !SHA256_PATTERN.test(value.planRevision)) {
    throw new Error('GRANT_REVISION_INVALID');
  }
  if (value.dataMode !== 'REAL' && value.dataMode !== 'SYNTHETIC') {
    throw new Error('GRANT_DATA_MODE_INVALID');
  }
  if (!Array.isArray(value.capabilities)
      || value.capabilities.length === 0
      || value.capabilities.length > EXECUTION_CAPABILITIES.length
      || new Set(value.capabilities).size !== value.capabilities.length
      || value.capabilities.some(capability => typeof capability !== 'string'
        || !CAPABILITY_SET.has(capability))) {
    throw new Error('GRANT_CAPABILITIES_INVALID');
  }
  if (!isEpochSecond(value.iat) || !isEpochSecond(value.nbf) || !isEpochSecond(value.exp)) {
    throw new Error('GRANT_TIME_INVALID');
  }
  return value as unknown as ExecutionGrantClaims;
};

export const requireCapability = (
  claims: ExecutionGrantClaims,
  capability: ExecutionCapability,
): void => {
  if (!claims.capabilities.includes(capability)) {
    throw new Error('GRANT_CAPABILITY_MISSING');
  }
};
