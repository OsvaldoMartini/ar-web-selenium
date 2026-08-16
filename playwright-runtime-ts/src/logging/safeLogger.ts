export interface SafeLogFields {
  readonly event: string;
  readonly level?: 'INFO' | 'WARN' | 'ERROR';
  readonly code?: string;
  readonly runId?: string;
  readonly count?: number;
  readonly durationMs?: number;
  readonly operation?: string;
  readonly status?: number;
  readonly contextId?: string;
  readonly browserId?: string;
  readonly pageId?: string;
  readonly pageKey?: string;
  readonly instructionId?: number;
  readonly action?: string;
  readonly registryCandidateCount?: number;
  readonly liveCandidateCount?: number;
  readonly viewportWidth?: number;
  readonly viewportHeight?: number;
  readonly screenWidth?: number;
  readonly screenHeight?: number;
  readonly devicePixelRatio?: number;
  readonly sequence?: number;
  readonly stage?: string;
  readonly headless?: boolean;
  readonly channel?: string;
  readonly argumentCount?: number;
  readonly retained?: boolean;
  readonly viewportMode?: string;
  readonly serviceWorkerMode?: string;
  readonly recoveryCandidateCount?: number;
  readonly physicalAttempts?: number;
  readonly frameValidated?: boolean;
  readonly shadowValidated?: boolean;
  readonly tagValidated?: boolean;
  readonly actionValidated?: boolean;
  readonly outcome?: string;
  readonly samples?: number;
  readonly stableSamples?: number;
  readonly readyState?: string;
  readonly nodeCount?: number;
}

export type SafeLogSink = (line: string) => void;

export const createSafeLogger = (sink: SafeLogSink = line => process.stdout.write(`${line}\n`)) =>
  (fields: SafeLogFields): void => {
    const safe = {
      timestamp: new Date().toISOString(),
      level: fields.level ?? 'INFO',
      event: fields.event,
      ...(fields.code ? { code: fields.code } : {}),
      ...(fields.runId ? { runId: fields.runId } : {}),
      ...(fields.count !== undefined ? { count: fields.count } : {}),
      ...(fields.durationMs !== undefined ? { durationMs: fields.durationMs } : {}),
      ...(fields.operation ? { operation: fields.operation } : {}),
      ...(fields.status !== undefined ? { status: fields.status } : {}),
      ...(fields.browserId ? { browserId: fields.browserId } : {}),
      ...(fields.contextId ? { contextId: fields.contextId } : {}),
      ...(fields.pageId ? { pageId: fields.pageId } : {}),
      ...(fields.pageKey ? { pageKey: fields.pageKey } : {}),
      ...(fields.instructionId !== undefined ? { instructionId: fields.instructionId } : {}),
      ...(fields.action ? { action: fields.action } : {}),
      ...(fields.registryCandidateCount !== undefined
        ? { registryCandidateCount: fields.registryCandidateCount } : {}),
      ...(fields.liveCandidateCount !== undefined
        ? { liveCandidateCount: fields.liveCandidateCount } : {}),
      ...(fields.viewportWidth !== undefined ? { viewportWidth: fields.viewportWidth } : {}),
      ...(fields.viewportHeight !== undefined ? { viewportHeight: fields.viewportHeight } : {}),
      ...(fields.screenWidth !== undefined ? { screenWidth: fields.screenWidth } : {}),
      ...(fields.screenHeight !== undefined ? { screenHeight: fields.screenHeight } : {}),
      ...(fields.devicePixelRatio !== undefined
        ? { devicePixelRatio: fields.devicePixelRatio } : {}),
      ...(fields.sequence !== undefined ? { sequence: fields.sequence } : {}),
      ...(fields.stage ? { stage: fields.stage } : {}),
      ...(fields.headless !== undefined ? { headless: fields.headless } : {}),
      ...(fields.channel ? { channel: fields.channel } : {}),
      ...(fields.argumentCount !== undefined ? { argumentCount: fields.argumentCount } : {}),
      ...(fields.retained !== undefined ? { retained: fields.retained } : {}),
      ...(fields.viewportMode ? { viewportMode: fields.viewportMode } : {}),
      ...(fields.serviceWorkerMode ? { serviceWorkerMode: fields.serviceWorkerMode } : {}),
      ...(fields.recoveryCandidateCount !== undefined
        ? { recoveryCandidateCount: fields.recoveryCandidateCount } : {}),
      ...(fields.physicalAttempts !== undefined ? { physicalAttempts: fields.physicalAttempts } : {}),
      ...(fields.frameValidated !== undefined ? { frameValidated: fields.frameValidated } : {}),
      ...(fields.shadowValidated !== undefined ? { shadowValidated: fields.shadowValidated } : {}),
      ...(fields.tagValidated !== undefined ? { tagValidated: fields.tagValidated } : {}),
      ...(fields.actionValidated !== undefined ? { actionValidated: fields.actionValidated } : {}),
      ...(fields.outcome ? { outcome: fields.outcome } : {}),
      ...(fields.samples !== undefined ? { samples: fields.samples } : {}),
      ...(fields.stableSamples !== undefined ? { stableSamples: fields.stableSamples } : {}),
      ...(fields.readyState ? { readyState: fields.readyState } : {}),
      ...(fields.nodeCount !== undefined ? { nodeCount: fields.nodeCount } : {}),
    };
    try {
      sink(JSON.stringify(safe));
    } catch {
      // Diagnostics are best effort. A full/locked log destination must never change browser
      // admission, navigation, physical-action, or cleanup outcomes.
    }
  };
