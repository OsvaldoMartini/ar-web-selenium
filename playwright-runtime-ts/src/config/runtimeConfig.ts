import { Buffer } from 'node:buffer';

export interface RuntimeConfig {
  readonly host: '127.0.0.1';
  readonly port: number;
  readonly grantKeyId: string;
  readonly grantSecret?: Buffer;
  readonly maxReservedRuns: number;
  readonly maxGrantSeconds: number;
  readonly clockSkewSeconds: number;
  readonly runIdleLeaseSeconds: number;
  readonly maximumActiveRuns: number;
  readonly maximumQueuedRuns: number;
  readonly maximumActiveRunsPerOrganization: number;
  readonly maximumActiveRunsPerBotJob: number;
}

const integerSetting = (
  environment: NodeJS.ProcessEnv,
  name: string,
  fallback: number,
  minimum: number,
  maximum: number,
): number => {
  const raw = environment[name];
  if (raw === undefined || raw.trim() === '') return fallback;
  if (!/^[0-9]+$/.test(raw)) throw new Error(`${name}_INVALID`);
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name}_INVALID`);
  }
  return parsed;
};

const decodeSecret = (raw: string | undefined): Buffer | undefined => {
  if (raw === undefined || raw.trim() === '') return undefined;
  const canonical = raw.trim();
  if (!/^[A-Za-z0-9_-]+$/.test(canonical)) throw new Error('GRANT_SECRET_INVALID');
  const decoded = Buffer.from(canonical, 'base64url');
  if (decoded.length < 32 || decoded.toString('base64url') !== canonical) {
    throw new Error('GRANT_SECRET_INVALID');
  }
  return decoded;
};

export const loadRuntimeConfig = (environment: NodeJS.ProcessEnv = process.env): RuntimeConfig => {
  const grantKeyId = (environment.ARWEB_EXECUTION_V2_GRANT_KID || 'v1').trim();
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(grantKeyId)) throw new Error('GRANT_KEY_ID_INVALID');

  const grantSecret = decodeSecret(environment.ARWEB_EXECUTION_V2_GRANT_SECRET_BASE64URL);
  return {
    host: '127.0.0.1',
    port: integerSetting(environment, 'ARWEB_EXECUTION_V2_PORT', 60110, 1, 65535),
    grantKeyId,
    ...(grantSecret ? { grantSecret } : {}),
    maxReservedRuns: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_MAX_RESERVED_RUNS', 32, 1, 256,
    ),
    maxGrantSeconds: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_MAX_GRANT_SECONDS', 120, 10, 300,
    ),
    clockSkewSeconds: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_CLOCK_SKEW_SECONDS', 5, 0, 30,
    ),
    runIdleLeaseSeconds: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_RUN_IDLE_LEASE_SECONDS', 60, 10, 300,
    ),
    maximumActiveRuns: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_MAX_ACTIVE_RUNS', 4, 1, 64,
    ),
    maximumQueuedRuns: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_MAX_QUEUED_RUNS', 32, 1, 256,
    ),
    maximumActiveRunsPerOrganization: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_MAX_ACTIVE_PER_ORGANIZATION', 2, 1, 32,
    ),
    maximumActiveRunsPerBotJob: integerSetting(
      environment, 'ARWEB_EXECUTION_V2_MAX_ACTIVE_PER_BOT_JOB', 1, 1, 8,
    ),
  };
};
