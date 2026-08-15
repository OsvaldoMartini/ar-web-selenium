import { randomUUID } from 'node:crypto';
import { Browser, BrowserContext, Page, chromium } from 'playwright-core';
import { BrowserSessionFactory, BrowserSessionHandle } from './browserSessionFactory';
import { PageReadiness } from './pageReadiness';
import { BrowserLaunchConfiguration } from '../session/sessionContracts';
import { PhysicalActionRequest, PhysicalActionResult } from '../action/actionContracts';
import { PhysicalActionExecutor } from '../action/physicalActionExecutor';
import { PlaywrightActionPage } from '../action/playwrightActionPage';
import { pageKeyFromUrl } from '../action/pageIdentity';
import { createSafeLogger, SafeLogFields, SafeLogSink } from '../logging/safeLogger';

export interface PlaywrightBrowserFactoryOptions {
  readonly navigationTimeoutMs?: number;
  readonly stabilityTimeoutMs?: number;
  readonly logSink?: SafeLogSink;
}

export class PlaywrightBrowserFactory implements BrowserSessionFactory {
  private readonly readiness: PageReadiness;
  private readonly log: (fields: SafeLogFields) => void;

  constructor(options: PlaywrightBrowserFactoryOptions = {}) {
    this.readiness = new PageReadiness({
      navigationTimeoutMs: options.navigationTimeoutMs ?? 30_000,
      stabilityTimeoutMs: options.stabilityTimeoutMs ?? 15_000,
      stableSamples: 3,
      sampleIntervalMs: 200,
    });
    this.log = createSafeLogger(options.logSink);
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
        args: [...new Set(['--start-maximized', ...(configuration.args ?? [])])],
        ...(configuration.executablePath
          ? { executablePath: configuration.executablePath }
          : configuration.channel && configuration.channel !== 'chromium'
            ? { channel: configuration.channel }
            : {}),
      });
      context = await browser.newContext({
        viewport: null,
        serviceWorkers: 'allow',
        acceptDownloads: false,
      });
      page = await context.newPage();
      const browserInstanceId = randomUUID();
      const contextInstanceId = randomUUID();
      const pageInstanceId = randomUUID();
      return new PlaywrightBrowserSessionHandle(
        browser,
        context,
        page,
        this.readiness,
        _runId,
        this.log,
        browserInstanceId,
        contextInstanceId,
        pageInstanceId,
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
  private readonly actions = new PhysicalActionExecutor();
  private activeAction: AbortController | undefined;

  constructor(
    private readonly browser: Browser,
    private readonly context: BrowserContext,
    private readonly page: Page,
    private readonly readiness: PageReadiness,
    private runId: string,
    private readonly log: (fields: SafeLogFields) => void,
    readonly browserInstanceId: string,
    readonly contextInstanceId: string,
    readonly pageInstanceId: string,
  ) {
    browser.on('disconnected', () => this.signalUnexpectedClose('BROWSER_PROCESS_DISCONNECTED'));
    page.on('close', () => this.signalUnexpectedClose('BROWSER_PAGE_CLOSED'));
  }

  bindRun(runId: string): void {
    this.runId = runId;
  }

  onUnexpectedClose(handler: (code: string) => void): void {
    this.unexpectedCloseHandler = handler;
    if (this.unexpectedCloseCode) handler(this.unexpectedCloseCode);
  }

  async navigate(endpoint: string): Promise<void> {
    this.requireOpen();
    await this.readiness.navigate(this.page, endpoint);
    const dimensions = await this.page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
      screenWidth: window.screen.width,
      screenHeight: window.screen.height,
      devicePixelRatio: window.devicePixelRatio,
    }));
    this.log({
      event: 'browser.page.ready',
      runId: this.runId,
      contextId: this.contextInstanceId,
      pageId: this.pageInstanceId,
      pageKey: pageKeyFromUrl(this.page.url()),
      ...dimensions,
    });
  }

  async refresh(): Promise<void> {
    this.requireOpen();
    await this.readiness.refresh(this.page);
  }

  async pageIdentity(): Promise<string> {
    this.requireOpen();
    return pageKeyFromUrl(this.page.url());
  }

  async perform(request: PhysicalActionRequest): Promise<PhysicalActionResult> {
    this.requireOpen();
    if (this.activeAction) throw new Error('BROWSER_ACTION_ALREADY_ACTIVE');
    const action = new AbortController();
    this.activeAction = action;
    try {
      const result = await this.actions.execute(
        new PlaywrightActionPage(this.page), request, action.signal,
      );
      this.log({
        event: 'browser.action.settled',
        runId: this.runId,
        contextId: this.contextInstanceId,
        pageId: this.pageInstanceId,
        pageKey: pageKeyFromUrl(this.page.url()),
        instructionId: request.instructionId,
        action: request.action,
        code: result.diagnostic.code,
        registryCandidateCount: result.diagnostic.registryCandidateCount,
        liveCandidateCount: result.diagnostic.liveCandidateCount,
      });
      return result;
    } finally {
      if (this.activeAction === action) this.activeAction = undefined;
    }
  }

  interrupt(): void {
    this.activeAction?.abort();
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.closed = true;
    this.interrupt();
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
