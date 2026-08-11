import { BrowserLaunchConfiguration } from '../session/sessionContracts';
import { PhysicalActionRequest, PhysicalActionResult } from '../action/actionContracts';

export interface BrowserSessionHandle {
  readonly browserInstanceId: string;
  readonly contextInstanceId: string;
  readonly pageInstanceId: string;
  onUnexpectedClose(handler: (code: string) => void): void;
  navigate(endpoint: string): Promise<void>;
  refresh(): Promise<void>;
  perform(request: PhysicalActionRequest): Promise<PhysicalActionResult>;
  close(): Promise<void>;
}

export interface BrowserSessionFactory {
  open(runId: string, configuration: BrowserLaunchConfiguration): Promise<BrowserSessionHandle>;
}
