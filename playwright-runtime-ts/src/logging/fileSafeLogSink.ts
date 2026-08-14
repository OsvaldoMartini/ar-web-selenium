import { appendFileSync, existsSync, mkdirSync, renameSync, rmSync, statSync } from 'node:fs';
import { isAbsolute, join, resolve } from 'node:path';
import type { SafeLogSink } from './safeLogger';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_ARCHIVES = 5;
const FILE_NAME = 'ar_web_execution_v2.log';

export interface FileSafeLogSink {
  readonly path: string;
  readonly sink: SafeLogSink;
}

/** Optional bounded JSON-lines sink for the standalone Node runtime. */
export const createFileSafeLogSink = (
  rawDirectory: string | undefined,
): FileSafeLogSink | undefined => {
  if (rawDirectory === undefined || rawDirectory.trim() === '') return undefined;
  const requested = rawDirectory.trim();
  if (!isAbsolute(requested) || requested.includes('\0') || requested.length > 1_024) {
    throw new Error('ARWEB_EXECUTION_V2_LOG_DIRECTORY_INVALID');
  }
  const directory = resolve(requested);
  mkdirSync(directory, { recursive: true });
  const path = join(directory, FILE_NAME);
  const rotate = (): void => {
    if (!existsSync(path) || statSync(path).size < MAX_FILE_BYTES) return;
    for (let index = MAX_ARCHIVES; index >= 1; index -= 1) {
      const source = index === 1 ? path : `${path}.${index - 1}`;
      const destination = `${path}.${index}`;
      if (existsSync(source)) {
        if (existsSync(destination)) rmSync(destination);
        renameSync(source, destination);
      }
    }
  };
  return {
    path,
    sink: line => {
      rotate();
      appendFileSync(path, `${line}\n`, { encoding: 'utf8', flag: 'a' });
    },
  };
};
