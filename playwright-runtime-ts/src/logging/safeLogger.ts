export interface SafeLogFields {
  readonly event: string;
  readonly level?: 'INFO' | 'WARN' | 'ERROR';
  readonly code?: string;
  readonly runId?: string;
  readonly count?: number;
  readonly durationMs?: number;
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
    };
    sink(JSON.stringify(safe));
  };
