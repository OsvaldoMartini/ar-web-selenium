import { ReservedRunView } from '../contracts/executionContracts';

export type ExecutionSessionState =
  | 'QUEUED'
  | 'STARTING'
  | 'LOADING_PAGE'
  | 'READY'
  | 'REFRESHING'
  | 'STOPPING'
  | 'STOPPED'
  | 'FAILED';

export interface BrowserLaunchConfiguration {
  readonly channel?: 'chrome' | 'msedge' | 'chromium';
  readonly executablePath?: string;
  readonly headless: boolean;
}

/** Internal-only launch facts. A future Java-authorized adapter must construct this object. */
export interface ExecutionLaunchDescriptor {
  readonly run: ReservedRunView;
  readonly endpoint: string;
  readonly browser: BrowserLaunchConfiguration;
}

export interface ExecutionSessionSnapshot {
  readonly runId: string;
  readonly organizationId: number;
  readonly homeBankingId: number;
  readonly botJobId: number;
  readonly dataMode: 'REAL' | 'SYNTHETIC';
  readonly state: ExecutionSessionState;
  readonly revision: number;
  readonly queuedAt: string;
  readonly startedAt?: string;
  readonly readyAt?: string;
  readonly stoppedAt?: string;
  readonly failureCode?: string;
  readonly browserInstanceId?: string;
  readonly contextInstanceId?: string;
  readonly pageInstanceId?: string;
}

export const validateExecutionEndpoint = (raw: string): string => {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > 2048) {
    throw new Error('EXECUTION_ENDPOINT_INVALID');
  }
  let endpoint: URL;
  try {
    endpoint = new URL(raw);
  } catch {
    throw new Error('EXECUTION_ENDPOINT_INVALID');
  }
  if ((endpoint.protocol !== 'https:' && endpoint.protocol !== 'http:')
      || endpoint.username !== ''
      || endpoint.password !== '') {
    throw new Error('EXECUTION_ENDPOINT_INVALID');
  }
  return endpoint.toString();
};
