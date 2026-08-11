import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import {
  ExecutionCapability,
  ExecutionGrantClaims,
  ReservedRunView,
} from '../contracts/executionContracts';

interface ReservedRunRecord {
  readonly view: ReservedRunView;
  readonly grantId: string;
  readonly grantFingerprint: string;
  readonly grantExpiresAtEpochSeconds: number;
  readonly runAccessToken: string;
  readonly runAccessTokenHash: Buffer;
  readonly capabilities: ReadonlySet<ExecutionCapability>;
  leaseExpiresAtEpochSeconds?: number;
}

export interface ReservationResult {
  readonly created: boolean;
  readonly run: ReservedRunView;
  readonly runAccessToken: string;
}

export class ExecutionRegistryError extends Error {
  constructor(readonly code: string) {
    super(code);
    this.name = 'ExecutionRegistryError';
  }
}

export class ExecutionRegistry {
  private readonly runs = new Map<string, ReservedRunRecord>();
  private readonly runIdByGrantId = new Map<string, string>();

  constructor(
    private readonly maximumRuns: number,
    private readonly nowEpochSeconds: () => number = () => Math.floor(Date.now() / 1000),
    private readonly tokenSupplier: () => string = () => randomBytes(32).toString('base64url'),
  ) {
    if (!Number.isInteger(maximumRuns) || maximumRuns < 1) {
      throw new Error('REGISTRY_CAPACITY_INVALID');
    }
  }

  reserve(claims: ExecutionGrantClaims, grantFingerprint: string): ReservationResult {
    this.sweepExpired();
    const existing = this.runs.get(claims.runId);
    if (existing) {
      if (existing.grantId === claims.jti && existing.grantFingerprint === grantFingerprint) {
        return {
          created: false,
          run: existing.view,
          runAccessToken: existing.runAccessToken,
        };
      }
      throw new ExecutionRegistryError('RUN_ID_CONFLICT');
    }
    const priorRunId = this.runIdByGrantId.get(claims.jti);
    if (priorRunId) throw new ExecutionRegistryError('GRANT_REPLAY_CONFLICT');
    if (this.runs.size >= this.maximumRuns) {
      throw new ExecutionRegistryError('RUNTIME_CAPACITY_REACHED');
    }

    const now = this.nowEpochSeconds();
    const runAccessToken = this.validToken(this.tokenSupplier());
    const view: ReservedRunView = Object.freeze({
      runId: claims.runId,
      organizationId: claims.organizationId,
      homeBankingId: claims.homeBankingId,
      botJobId: claims.botJobId,
      workspaceEpoch: claims.workspaceEpoch,
      graphRevision: claims.graphRevision,
      planRevision: claims.planRevision,
      dataMode: claims.dataMode,
      state: 'RESERVED',
      createdAt: new Date(now * 1000).toISOString(),
      expiresAt: new Date(claims.exp * 1000).toISOString(),
    });
    this.runs.set(claims.runId, {
      view,
      grantId: claims.jti,
      grantFingerprint,
      grantExpiresAtEpochSeconds: claims.exp,
      runAccessToken,
      runAccessTokenHash: tokenHash(runAccessToken),
      capabilities: new Set(claims.capabilities),
    });
    this.runIdByGrantId.set(claims.jti, claims.runId);
    return { created: true, run: view, runAccessToken };
  }

  activateRunLease(
    runId: string,
    runAccessToken: string,
    leaseSeconds: number,
  ): ReservedRunView {
    const record = this.authorizeRunToken(runId, runAccessToken, 'runtime.start', false);
    record.leaseExpiresAtEpochSeconds = this.leaseDeadline(leaseSeconds);
    return record.view;
  }

  authorizeActiveRun(
    runId: string,
    runAccessToken: string,
    capability: Exclude<ExecutionCapability, 'runtime.reserve' | 'runtime.start'>,
    leaseSeconds: number,
  ): ReservedRunView {
    const record = this.authorizeRunToken(runId, runAccessToken, capability, true);
    record.leaseExpiresAtEpochSeconds = this.leaseDeadline(leaseSeconds);
    return record.view;
  }

  releaseWithRunToken(runId: string, runAccessToken: string): ReservedRunView {
    const record = this.authorizeRunToken(runId, runAccessToken, 'runtime.release', false);
    this.remove(runId, record);
    return record.view;
  }

  getAuthorized(
    runId: string,
    claims: ExecutionGrantClaims,
    grantFingerprint: string,
  ): ReservedRunView {
    this.sweepExpired();
    const record = this.runs.get(runId);
    if (!record) throw new ExecutionRegistryError('RUN_NOT_FOUND');
    if (claims.runId !== runId
        || claims.jti !== record.grantId
        || grantFingerprint !== record.grantFingerprint) {
      throw new ExecutionRegistryError('RUN_AUTHORITY_MISMATCH');
    }
    return record.view;
  }

  release(
    runId: string,
    claims: ExecutionGrantClaims,
    grantFingerprint: string,
  ): ReservedRunView {
    const view = this.getAuthorized(runId, claims, grantFingerprint);
    const record = this.runs.get(runId);
    if (!record) throw new ExecutionRegistryError('RUN_NOT_FOUND');
    this.remove(runId, record);
    return view;
  }

  sweepExpired(): number {
    const now = this.nowEpochSeconds();
    let removed = 0;
    for (const [runId, record] of this.runs) {
      const deadline = record.leaseExpiresAtEpochSeconds ?? record.grantExpiresAtEpochSeconds;
      if (deadline > now) continue;
      this.remove(runId, record);
      removed += 1;
    }
    return removed;
  }

  size(): number {
    this.sweepExpired();
    return this.runs.size;
  }

  private authorizeRunToken(
    runId: string,
    runAccessToken: string,
    capability: ExecutionCapability,
    requireActive: boolean,
  ): ReservedRunRecord {
    this.sweepExpired();
    const record = this.runs.get(runId);
    if (!record) throw new ExecutionRegistryError('RUN_NOT_FOUND');
    const suppliedHash = tokenHash(this.validToken(runAccessToken));
    if (!timingSafeEqual(record.runAccessTokenHash, suppliedHash)) {
      throw new ExecutionRegistryError('RUN_AUTHORITY_MISMATCH');
    }
    if (!record.capabilities.has(capability)) {
      throw new ExecutionRegistryError('RUN_CAPABILITY_MISSING');
    }
    if (requireActive && record.leaseExpiresAtEpochSeconds === undefined) {
      throw new ExecutionRegistryError('RUN_NOT_ACTIVE');
    }
    return record;
  }

  private leaseDeadline(leaseSeconds: number): number {
    if (!Number.isInteger(leaseSeconds) || leaseSeconds < 10 || leaseSeconds > 300) {
      throw new ExecutionRegistryError('RUN_LEASE_INVALID');
    }
    return this.nowEpochSeconds() + leaseSeconds;
  }

  private validToken(value: string): string {
    if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(value)) {
      throw new ExecutionRegistryError('RUN_TOKEN_INVALID');
    }
    const bytes = Buffer.from(value, 'base64url');
    if (bytes.length !== 32 || bytes.toString('base64url') !== value) {
      throw new ExecutionRegistryError('RUN_TOKEN_INVALID');
    }
    return value;
  }

  private remove(runId: string, record: ReservedRunRecord): void {
    this.runs.delete(runId);
    this.runIdByGrantId.delete(record.grantId);
  }
}

const tokenHash = (token: string): Buffer => createHash('sha256').update(token, 'ascii').digest();
