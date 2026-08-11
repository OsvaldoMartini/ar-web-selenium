import {
  ExecutionGrantClaims,
  ReservedRunView,
} from '../contracts/executionContracts';

interface ReservedRunRecord {
  readonly view: ReservedRunView;
  readonly grantId: string;
  readonly grantFingerprint: string;
  readonly expiresAtEpochSeconds: number;
}

export interface ReservationResult {
  readonly created: boolean;
  readonly run: ReservedRunView;
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
        return { created: false, run: existing.view };
      }
      throw new ExecutionRegistryError('RUN_ID_CONFLICT');
    }
    const priorRunId = this.runIdByGrantId.get(claims.jti);
    if (priorRunId) throw new ExecutionRegistryError('GRANT_REPLAY_CONFLICT');
    if (this.runs.size >= this.maximumRuns) {
      throw new ExecutionRegistryError('RUNTIME_CAPACITY_REACHED');
    }

    const now = this.nowEpochSeconds();
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
      expiresAtEpochSeconds: claims.exp,
    });
    this.runIdByGrantId.set(claims.jti, claims.runId);
    return { created: true, run: view };
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
    this.runs.delete(runId);
    this.runIdByGrantId.delete(record.grantId);
    return view;
  }

  sweepExpired(): number {
    const now = this.nowEpochSeconds();
    let removed = 0;
    for (const [runId, record] of this.runs) {
      if (record.expiresAtEpochSeconds > now) continue;
      this.runs.delete(runId);
      this.runIdByGrantId.delete(record.grantId);
      removed += 1;
    }
    return removed;
  }

  size(): number {
    this.sweepExpired();
    return this.runs.size;
  }
}
