import { BrowserLaunchConfiguration } from '../session/sessionContracts';
import { PhysicalActionRequest, PhysicalActionResult } from '../action/actionContracts';

export interface BrowserSessionHandle {
  readonly browserInstanceId: string;
  readonly contextInstanceId: string;
  readonly pageInstanceId: string;
  bindRun(runId: string): void;
  onUnexpectedClose(handler: (code: string) => void): void;
  navigate(endpoint: string): Promise<void>;
  pageIdentity(): Promise<string>;
  refresh(): Promise<void>;
  perform(request: PhysicalActionRequest): Promise<PhysicalActionResult>;
  scanner(request: BrowserScannerRequest): Promise<unknown>;
  interrupt(): void;
  close(): Promise<void>;
}

export type BrowserScannerRequest =
  | { readonly operation: 'evaluate'; readonly script: string; readonly argument?: unknown }
  | { readonly operation: 'screenshot'; readonly fullPage: boolean }
  | {
    readonly operation: 'test-element';
    readonly action: 'CLICK' | 'INPUT';
    readonly xpath: string;
    readonly css: string;
    readonly value: string;
  }
  | { readonly operation: 'url' | 'title' | 'content' | 'viewport' | 'wait-settled' | 'reload' };

export interface BrowserSessionFactory {
  open(runId: string, configuration: BrowserLaunchConfiguration): Promise<BrowserSessionHandle>;
}
