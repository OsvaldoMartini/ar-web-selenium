import { BrowserLaunchConfiguration } from '../session/sessionContracts';

export interface BrowserSessionHandle {
  readonly browserInstanceId: string;
  readonly contextInstanceId: string;
  readonly pageInstanceId: string;
  onUnexpectedClose(handler: (code: string) => void): void;
  navigate(endpoint: string): Promise<void>;
  refresh(): Promise<void>;
  close(): Promise<void>;
}

export interface BrowserSessionFactory {
  open(runId: string, configuration: BrowserLaunchConfiguration): Promise<BrowserSessionHandle>;
}
