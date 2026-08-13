import { BrowserSessionFactory, BrowserSessionHandle } from '../browser/browserSessionFactory';
import {
  ExecutionLaunchDescriptor,
  ExecutionSessionSnapshot,
  ExecutionSessionState,
  validateExecutionEndpoint,
} from './sessionContracts';
import {
  physicalActionRequestFingerprint,
  PhysicalActionRequest,
  PhysicalActionResult,
  validatePhysicalActionRequest,
} from '../action/actionContracts';

interface SettledAction {
  readonly fingerprint: string;
  readonly result: PhysicalActionResult;
}

const MAX_SETTLED_ACTIONS = 4_096;

const safeFailureCode = (error: unknown): string => {
  const raw = error instanceof Error ? error.message : 'SESSION_FAILURE';
  return /^[A-Z][A-Z0-9_]{2,80}$/.test(raw) ? raw : 'SESSION_FAILURE';
};

export class ExecutionSession {
  private state: ExecutionSessionState = 'QUEUED';
  private revision = 0;
  private readonly queuedAt = new Date().toISOString();
  private startedAt?: string;
  private readyAt?: string;
  private stoppedAt?: string;
  private failureCode?: string;
  private handle: BrowserSessionHandle | undefined;
  private browserInstanceId?: string;
  private contextInstanceId?: string;
  private pageInstanceId?: string;
  private stopRequested = false;
  private nextActionSequence = 1;
  private readonly settledActions = new Map<number, SettledAction>();
  private serial: Promise<void> = Promise.resolve();
  private readonly endpoint: string;

  constructor(
    readonly descriptor: ExecutionLaunchDescriptor,
    private readonly factory: BrowserSessionFactory,
    private readonly onTerminal?: (snapshot: ExecutionSessionSnapshot) => void,
  ) {
    this.endpoint = validateExecutionEndpoint(descriptor.endpoint);
  }

  start(): Promise<void> {
    return this.exclusive(async () => {
      if (this.state !== 'QUEUED') throw new Error('SESSION_START_STATE_INVALID');
      this.transition('STARTING');
      this.startedAt = new Date().toISOString();
      try {
        this.handle = await this.factory.open(
          this.descriptor.run.runId,
          this.descriptor.browser,
        );
        this.browserInstanceId = this.handle.browserInstanceId;
        this.contextInstanceId = this.handle.contextInstanceId;
        this.pageInstanceId = this.handle.pageInstanceId;
        this.handle.onUnexpectedClose(code => void this.unexpectedClose(code));
        if (this.stopRequested) {
          await this.closeHandleSafely();
          this.stoppedAt = new Date().toISOString();
          this.transition('STOPPED');
          return;
        }
        this.transition('LOADING_PAGE');
        await this.handle.navigate(this.endpoint);
        if (this.stopRequested) {
          await this.closeHandleSafely();
          this.stoppedAt = new Date().toISOString();
          this.transition('STOPPED');
          return;
        }
        this.readyAt = new Date().toISOString();
        this.transition('READY');
      } catch (error) {
        if (this.stopRequested) {
          await this.finishStoppedAfterInterruption();
          return;
        }
        await this.finishFailed(error);
        throw error;
      }
    });
  }

  refresh(): Promise<void> {
    return this.exclusive(async () => {
      if (this.state !== 'READY' || !this.handle) {
        throw new Error('SESSION_REFRESH_STATE_INVALID');
      }
      this.transition('REFRESHING');
      try {
        await this.handle.refresh();
        this.transition('READY');
      } catch (error) {
        if (this.stopRequested) {
          await this.finishStoppedAfterInterruption();
          return;
        }
        await this.finishFailed(error);
        throw error;
      }
    });
  }

  pageIdentity(): Promise<string> {
    return this.exclusive(async () => {
      if (this.state !== 'READY' || !this.handle) {
        throw new Error('SESSION_PAGE_IDENTITY_STATE_INVALID');
      }
      return this.handle.pageIdentity();
    });
  }

  perform(request: PhysicalActionRequest): Promise<PhysicalActionResult> {
    return this.exclusive(async () => {
      validatePhysicalActionRequest(request);
      const fingerprint = physicalActionRequestFingerprint(request);
      const settled = this.settledActions.get(request.sequence);
      if (settled) {
        if (settled.fingerprint !== fingerprint) throw new Error('ACTION_SEQUENCE_CONFLICT');
        return settled.result;
      }
      if (this.state !== 'READY' || !this.handle) {
        throw new Error('SESSION_ACTION_STATE_INVALID');
      }
      if (request.sequence !== this.nextActionSequence) {
        throw new Error('ACTION_SEQUENCE_OUT_OF_ORDER');
      }
      if (this.settledActions.size >= MAX_SETTLED_ACTIONS) {
        throw new Error('ACTION_RESULT_CAPACITY_REACHED');
      }
      let result: PhysicalActionResult;
      try {
        result = await this.handle.perform(request);
      } catch {
        if (this.stopRequested) {
          await this.finishStoppedAfterInterruption();
          return this.cancelledAction(request);
        }
        result = {
          ok: false,
          diagnostic: {
            code: 'ACTION_OUTCOME_UNKNOWN',
            stage: 'RESOLUTION',
            action: request.action,
            instructionId: request.instructionId,
            registryCandidateCount: request.registryCandidates.length,
            liveCandidateCount: 0,
            frameValidated: false,
            shadowValidated: false,
            tagValidated: false,
            actionValidated: false,
            physicalAttempts: 1,
          },
        };
        this.settleAction(request.sequence, fingerprint, result);
        await this.finishFailed(new Error('ACTION_OUTCOME_UNKNOWN'));
        return result;
      }
      if (this.stopRequested) {
        await this.finishStoppedAfterInterruption();
        return this.cancelledAction(request);
      }
      this.settleAction(request.sequence, fingerprint, result);
      return result;
    });
  }

  stop(): Promise<void> {
    this.stopRequested = true;
    const interrupt = this.handle?.close().then(
      () => undefined,
      error => error,
    );
    return this.exclusive(async () => {
      if (this.state === 'STOPPED') return;
      if (this.state === 'FAILED') {
        if (interrupt) {
          const failure = await interrupt;
          if (failure) throw failure;
        } else await this.closeHandleSafely();
        return;
      }
      this.transition('STOPPING');
      try {
        if (interrupt) {
          const failure = await interrupt;
          if (failure) throw failure;
        }
        await this.closeHandleSafely();
        this.stoppedAt = new Date().toISOString();
        this.transition('STOPPED');
      } catch (error) {
        this.failureCode = safeFailureCode(error);
        this.transition('FAILED');
        throw error;
      }
    });
  }

  snapshot(): ExecutionSessionSnapshot {
    const run = this.descriptor.run;
    return {
      runId: run.runId,
      organizationId: run.organizationId,
      homeBankingId: run.homeBankingId,
      botJobId: run.botJobId,
      dataMode: run.dataMode,
      state: this.state,
      revision: this.revision,
      queuedAt: this.queuedAt,
      ...(this.startedAt ? { startedAt: this.startedAt } : {}),
      ...(this.readyAt ? { readyAt: this.readyAt } : {}),
      ...(this.stoppedAt ? { stoppedAt: this.stoppedAt } : {}),
      ...(this.failureCode ? { failureCode: this.failureCode } : {}),
      ...(this.browserInstanceId ? { browserInstanceId: this.browserInstanceId } : {}),
      ...(this.contextInstanceId ? { contextInstanceId: this.contextInstanceId } : {}),
      ...(this.pageInstanceId ? { pageInstanceId: this.pageInstanceId } : {}),
    };
  }

  private transition(next: ExecutionSessionState): void {
    const wasTerminal = this.state === 'STOPPED' || this.state === 'FAILED';
    this.state = next;
    this.revision += 1;
    if (!wasTerminal && (next === 'STOPPED' || next === 'FAILED')) {
      this.onTerminal?.(this.snapshot());
    }
  }

  private exclusive<T>(operation: () => Promise<T>): Promise<T> {
    const next = this.serial.then(operation, operation);
    this.serial = next.then(() => undefined, () => undefined);
    return next;
  }

  private async closeHandleSafely(): Promise<void> {
    const current = this.handle;
    this.handle = undefined;
    if (current) await current.close();
  }

  private async finishFailed(error: unknown): Promise<void> {
    this.failureCode = safeFailureCode(error);
    try {
      await this.closeHandleSafely();
    } catch {
      this.failureCode = 'BROWSER_CLEANUP_FAILED';
    }
    this.transition('FAILED');
  }

  private async finishStoppedAfterInterruption(): Promise<void> {
    try {
      await this.closeHandleSafely();
      this.stoppedAt = new Date().toISOString();
      this.transition('STOPPED');
    } catch {
      this.failureCode = 'BROWSER_CLEANUP_FAILED';
      this.transition('FAILED');
    }
  }

  private settleAction(
    sequence: number,
    fingerprint: string,
    result: PhysicalActionResult,
  ): void {
    this.settledActions.set(sequence, { fingerprint, result });
    this.nextActionSequence += 1;
  }

  private cancelledAction(request: PhysicalActionRequest): PhysicalActionResult {
    return {
      ok: false,
      diagnostic: {
        code: 'ACTION_CANCELLED',
        stage: 'RESOLUTION',
        action: request.action,
        instructionId: request.instructionId,
        registryCandidateCount: request.registryCandidates.length,
        liveCandidateCount: 0,
        frameValidated: false,
        shadowValidated: false,
        tagValidated: false,
        actionValidated: false,
        physicalAttempts: 0,
      },
    };
  }

  private unexpectedClose(code: string): Promise<void> {
    return this.exclusive(async () => {
      if (this.stopRequested
          || this.state === 'STOPPED'
          || this.state === 'STOPPING'
          || this.state === 'FAILED') return;
      this.failureCode = safeFailureCode(new Error(code));
      try {
        await this.closeHandleSafely();
      } catch {
        // Preserve the authoritative unexpected-close cause. Cleanup is best effort here;
        // the handle attempts every owned resource before it reports an aggregate failure.
      }
      this.transition('FAILED');
    });
  }
}
