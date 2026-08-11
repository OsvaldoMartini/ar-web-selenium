import { randomUUID } from 'node:crypto';
import { Browser, BrowserContext, Page, chromium } from 'playwright-core';
import { BrowserSessionFactory, BrowserSessionHandle } from './browserSessionFactory';
import { PageReadiness } from './pageReadiness';
import { BrowserLaunchConfiguration } from '../session/sessionContracts';

export interface PlaywrightBrowserFactoryOptions {
  readonly navigationTimeoutMs?: number;
  readonly stabilityTimeoutMs?: number;
}

export class PlaywrightBrowserFactory implements BrowserSessionFactory {
  private readonly readiness: PageReadiness;

  constructor(options: PlaywrightBrowserFactoryOptions = {}) {
    this.readiness = new PageReadiness({
      navigationTimeoutMs: options.navigationTimeoutMs ?? 30_000,
      stabilityTimeoutMs: options.stabilityTimeoutMs ?? 15_000,
      stableSamples: 3,
      sampleIntervalMs: 200,
    });
  }

  async open(
    _runId: string,
    configuration: BrowserLaunchConfiguration,
  ): Promise<BrowserSessionHandle> {
    let browser: Browser | undefined;
    let context: BrowserContext | undefined;
    let page: Page | undefined;
    try {
      browser = await chromium.launch({
        headless: configuration.headless,
        ...(configuration.executablePath
          ? { executablePath: configuration.executablePath }
          : configuration.channel && configuration.channel !== 'chromium'
            ? { channel: configuration.channel }
            : {}),
      });
      context = await browser.newContext({
        serviceWorkers: 'block',
        acceptDownloads: false,
      });
      page = await context.newPage();
      return new PlaywrightBrowserSessionHandle(
        browser,
        context,
        page,
        this.readiness,
        randomUUID(),
        randomUUID(),
        randomUUID(),
      );
    } catch (error) {
      await closeResources(page, context, browser);
      throw error;
    }
  }
}

class PlaywrightBrowserSessionHandle implements BrowserSessionHandle {
  private closed = false;
  private unexpectedCloseSignaled = false;
  private unexpectedCloseCode?: string;
  private unexpectedCloseHandler?: (code: string) => void;

  constructor(
    private readonly browser: Browser,
    private readonly context: BrowserContext,
    private readonly page: Page,
    private readonly readiness: PageReadiness,
    readonly browserInstanceId: string,
    readonly contextInstanceId: string,
    readonly pageInstanceId: string,
  ) {
    browser.on('disconnected', () => this.signalUnexpectedClose('BROWSER_PROCESS_DISCONNECTED'));
    page.on('close', () => this.signalUnexpectedClose('BROWSER_PAGE_CLOSED'));
  }

  onUnexpectedClose(handler: (code: string) => void): void {
    this.unexpectedCloseHandler = handler;
    if (this.unexpectedCloseCode) handler(this.unexpectedCloseCode);
  }

  async navigate(endpoint: string): Promise<void> {
    this.requireOpen();
    await this.readiness.navigate(this.page, endpoint);
  }

  async refresh(): Promise<void> {
    this.requireOpen();
    await this.readiness.refresh(this.page);
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.closed = true;
    await closeResources(this.page, this.context, this.browser);
  }

  private requireOpen(): void {
    if (this.closed || !this.browser.isConnected() || this.page.isClosed()) {
      throw new Error('BROWSER_SESSION_CLOSED');
    }
  }

  private signalUnexpectedClose(code: string): void {
    if (this.closed || this.unexpectedCloseSignaled) return;
    this.unexpectedCloseSignaled = true;
    this.unexpectedCloseCode = code;
    this.unexpectedCloseHandler?.(code);
  }
}

const closeResources = async (
  page?: Page,
  context?: BrowserContext,
  browser?: Browser,
): Promise<void> => {
  const failures: unknown[] = [];
  for (const close of [
    page && !page.isClosed() ? () => page.close({ runBeforeUnload: false }) : undefined,
    context ? () => context.close() : undefined,
    browser && browser.isConnected() ? () => browser.close() : undefined,
  ]) {
    if (!close) continue;
    try {
      await close();
    } catch (error) {
      failures.push(error);
    }
  }
  if (failures.length > 0) throw new AggregateError(failures, 'BROWSER_CLEANUP_FAILED');
};
