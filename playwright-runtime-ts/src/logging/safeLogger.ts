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
    };
    sink(JSON.stringify(safe));
  };
