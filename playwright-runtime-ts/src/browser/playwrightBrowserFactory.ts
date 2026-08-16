import { randomUUID } from 'node:crypto';
import { Browser, BrowserContext, Page, chromium } from 'playwright-core';
import { BrowserScannerRequest, BrowserSessionFactory, BrowserSessionHandle } from './browserSessionFactory';
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

/** Builds an invocation expression because Playwright does not invoke a function supplied as text. */
export const scannerEvaluationExpression = (script: string, argument: unknown, hasArgument: boolean): string => {
  const serialized = hasArgument ? JSON.stringify(argument) : 'undefined';
  if (serialized === undefined) throw new Error('SCANNER_ARGUMENT_INVALID');
  return `(() => { const candidate = (${script}); return typeof candidate === 'function'`
    + ` ? candidate(${serialized}) : candidate; })()`;
};

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
    runId: string,
    configuration: BrowserLaunchConfiguration,
  ): Promise<BrowserSessionHandle> {
    const started = Date.now();
    const browserInstanceId = randomUUID();
    const contextInstanceId = randomUUID();
    const pageInstanceId = randomUUID();
    const launchArguments = [...new Set(['--start-maximized', ...(configuration.args ?? [])])];
    const channel = configuration.executablePath
      ? 'custom-executable'
      : configuration.channel ?? 'chromium';
    this.log({
      event: 'browser.launch.requested',
      runId,
      browserId: browserInstanceId,
      headless: configuration.headless,
      channel,
      argumentCount: launchArguments.length,
      viewportMode: 'native',
      serviceWorkerMode: 'allow',
    });
    let browser: Browser | undefined;
    let context: BrowserContext | undefined;
    let page: Page | undefined;
    try {
      browser = await chromium.launch({
        headless: configuration.headless,
        args: launchArguments,
        ...(configuration.executablePath
          ? { executablePath: configuration.executablePath }
          : configuration.channel && configuration.channel !== 'chromium'
            ? { channel: configuration.channel }
            : {}),
      });
      this.log({
        event: 'browser.process.started', runId, browserId: browserInstanceId,
        durationMs: Date.now() - started,
      });
      context = await browser.newContext({
        viewport: null,
        serviceWorkers: 'allow',
        acceptDownloads: false,
      });
      this.log({
        event: 'browser.context.created', runId, browserId: browserInstanceId,
        contextId: contextInstanceId, viewportMode: 'native', serviceWorkerMode: 'allow',
      });
      page = await context.newPage();
      this.log({
        event: 'browser.page.created', runId, browserId: browserInstanceId,
        contextId: contextInstanceId, pageId: pageInstanceId,
      });
      return new PlaywrightBrowserSessionHandle(
        browser,
        context,
        page,
        this.readiness,
        runId,
        this.log,
        browserInstanceId,
        contextInstanceId,
        pageInstanceId,
      );
    } catch (error) {
      this.log({
        event: 'browser.launch.failed', level: 'ERROR', runId,
        browserId: browserInstanceId, code: safeFailureCode(error, 'BROWSER_LAUNCH_FAILED'),
        durationMs: Date.now() - started,
      });
      try {
        await closeResources(page, context, browser);
      } catch {
        this.log({
          event: 'browser.launch.cleanup.failed', level: 'ERROR', runId,
          browserId: browserInstanceId, code: 'BROWSER_CLEANUP_FAILED',
        });
      }
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
    const retained = this.runId !== runId;
    this.runId = runId;
    this.log({
      event: 'browser.run.bound', runId, browserId: this.browserInstanceId,
      contextId: this.contextInstanceId, pageId: this.pageInstanceId, retained,
    });
  }

  onUnexpectedClose(handler: (code: string) => void): void {
    this.unexpectedCloseHandler = handler;
    if (this.unexpectedCloseCode) handler(this.unexpectedCloseCode);
  }

  async navigate(endpoint: string): Promise<void> {
    this.requireOpen();
    const started = Date.now();
    this.log(this.identityFields('browser.navigation.started'));
    try {
      await this.readiness.navigate(this.page, endpoint);
      const dimensions = await this.page.evaluate(() => ({
        viewportWidth: window.innerWidth,
        viewportHeight: window.innerHeight,
        screenWidth: window.screen.width,
        screenHeight: window.screen.height,
        devicePixelRatio: window.devicePixelRatio,
      }));
      this.log({
        ...this.identityFields('browser.page.ready'),
        pageKey: pageKeyFromUrl(this.page.url()),
        durationMs: Date.now() - started,
        ...dimensions,
      });
    } catch (error) {
      this.log({
        ...this.identityFields('browser.navigation.failed'), level: 'ERROR',
        code: safeFailureCode(error, 'BROWSER_NAVIGATION_FAILED'),
        durationMs: Date.now() - started,
      });
      throw error;
    }
  }

  async refresh(): Promise<void> {
    this.requireOpen();
    const started = Date.now();
    this.log({ ...this.identityFields('browser.refresh.started'), ...this.pageKeyFields() });
    try {
      await this.readiness.refresh(this.page);
      this.log({
        ...this.identityFields('browser.refresh.settled'), ...this.pageKeyFields(),
        durationMs: Date.now() - started,
      });
    } catch (error) {
      this.log({
        ...this.identityFields('browser.refresh.failed'), level: 'ERROR',
        code: safeFailureCode(error, 'BROWSER_REFRESH_FAILED'),
        durationMs: Date.now() - started,
      });
      throw error;
    }
  }

  async pageIdentity(): Promise<string> {
    this.requireOpen();
    const pageKey = pageKeyFromUrl(this.page.url());
    this.log({ ...this.identityFields('browser.page.identity'), pageKey });
    return pageKey;
  }

  async perform(request: PhysicalActionRequest): Promise<PhysicalActionResult> {
    this.requireOpen();
    if (this.activeAction) throw new Error('BROWSER_ACTION_ALREADY_ACTIVE');
    const action = new AbortController();
    this.activeAction = action;
    const started = Date.now();
    this.log({
      ...this.identityFields('browser.action.started'), ...this.pageKeyFields(),
      instructionId: request.instructionId, action: request.action, sequence: request.sequence,
      registryCandidateCount: request.registryCandidates.length,
    });
    try {
      const result = await this.actions.execute(
        new PlaywrightActionPage(this.page), request, action.signal,
      );
      this.log({
        ...this.identityFields('browser.action.settled'),
        ...this.pageKeyFields(),
        instructionId: request.instructionId,
        action: request.action,
        sequence: request.sequence,
        code: result.diagnostic.code,
        stage: result.diagnostic.stage,
        registryCandidateCount: result.diagnostic.registryCandidateCount,
        liveCandidateCount: result.diagnostic.liveCandidateCount,
        recoveryCandidateCount: result.ok ? 0 : result.recovery?.candidates.length ?? 0,
        physicalAttempts: result.diagnostic.physicalAttempts,
        frameValidated: result.diagnostic.frameValidated,
        shadowValidated: result.diagnostic.shadowValidated,
        tagValidated: result.diagnostic.tagValidated,
        actionValidated: result.diagnostic.actionValidated,
        durationMs: Date.now() - started,
      });
      return result;
    } catch (error) {
      this.log({
        ...this.identityFields('browser.action.failed'), level: 'ERROR',
        ...this.pageKeyFields(), instructionId: request.instructionId,
        action: request.action, sequence: request.sequence,
        stage: 'EXECUTION',
        code: safeFailureCode(error, action.signal.aborted ? 'ACTION_CANCELLED' : 'ACTION_FAILED'),
        registryCandidateCount: request.registryCandidates.length,
        liveCandidateCount: 0, durationMs: Date.now() - started,
      });
      throw error;
    } finally {
      if (this.activeAction === action) this.activeAction = undefined;
    }
  }

  async scanner(request: BrowserScannerRequest): Promise<unknown> {
    this.requireOpen();
    const started = Date.now();
    this.log({
      ...this.identityFields('browser.scanner.started'),
      ...this.pageKeyFields(),
      operation: request.operation,
    });
    try {
      let result: unknown;
      switch (request.operation) {
        case 'evaluate':
          result = await this.page.evaluate(scannerEvaluationExpression(
            request.script,
            request.argument,
            Object.hasOwn(request, 'argument'),
          ));
        break;
      case 'screenshot':
        result = (await this.page.screenshot({ fullPage: request.fullPage })).toString('base64');
        break;
      case 'test-element': {
        result = false;
        for (const selector of [request.xpath, request.css].filter(value => value.length > 0)) {
          const candidates = this.page.locator(selector);
          const visibleIndexes = await candidates.evaluateAll(elements => elements
            .map((element, index) => {
              const style = getComputedStyle(element);
              const rect = element.getBoundingClientRect();
              return style.display !== 'none' && style.visibility !== 'hidden'
                && rect.width > 0 && rect.height > 0 ? index : -1;
            })
            .filter(index => index >= 0));
          if (visibleIndexes.length === 0) continue;
          if (visibleIndexes.length !== 1) break;
          const target = candidates.nth(visibleIndexes[0]!);
          if (request.action === 'CLICK') await target.click();
          else await target.fill(request.value);
          result = true;
          break;
        }
        break;
      }
      case 'url': result = this.page.url(); break;
      case 'title': result = await this.page.title(); break;
      case 'content': result = await this.page.content(); break;
      case 'viewport':
        result = await this.page.evaluate(() => [
          window.innerWidth || document.documentElement.clientWidth,
          window.innerHeight || document.documentElement.clientHeight,
        ]);
        break;
      case 'wait-settled':
        {
          const readiness = await this.readiness.waitForScannerStable(this.page);
          this.log({
            ...this.identityFields('browser.scanner.readiness'),
            ...this.pageKeyFields(),
            outcome: readiness.outcome,
            samples: readiness.samples,
            stableSamples: readiness.stableSamples,
            readyState: readiness.readyState,
            nodeCount: readiness.nodeCount,
            durationMs: readiness.durationMs,
          });
        }
        result = true;
        break;
      case 'reload':
        await this.refresh();
        result = true;
        break;
      }
      this.log({
        ...this.identityFields('browser.scanner.completed'),
        ...this.pageKeyFields(),
        operation: request.operation,
        durationMs: Date.now() - started,
      });
      return result;
    } catch (error) {
      this.log({
        ...this.identityFields('browser.scanner.failed'),
        ...this.pageKeyFields(),
        operation: request.operation,
        level: 'ERROR',
        code: safeFailureCode(error, 'SCANNER_OPERATION_FAILED'),
        durationMs: Date.now() - started,
      });
      throw error;
    }
  }

  interrupt(): void {
    this.log({
      ...this.identityFields('browser.action.interrupt.requested'),
      count: this.activeAction ? 1 : 0,
    });
    this.activeAction?.abort();
  }

  async close(): Promise<void> {
    if (this.closed) return;
    const started = Date.now();
    this.log(this.identityFields('browser.close.started'));
    this.closed = true;
    this.interrupt();
    try {
      await closeResources(this.page, this.context, this.browser);
      this.log({ ...this.identityFields('browser.close.settled'), durationMs: Date.now() - started });
    } catch (error) {
      this.log({
        ...this.identityFields('browser.close.failed'), level: 'ERROR',
        code: safeFailureCode(error, 'BROWSER_CLEANUP_FAILED'), durationMs: Date.now() - started,
      });
      throw error;
    }
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
    this.log({ ...this.identityFields('browser.closed.unexpectedly'), level: 'ERROR', code });
    this.unexpectedCloseHandler?.(code);
  }

  private identityFields(event: string): SafeLogFields {
    return {
      event, runId: this.runId, browserId: this.browserInstanceId,
      contextId: this.contextInstanceId, pageId: this.pageInstanceId,
    };
  }

  private currentPageKey(): string | undefined {
    if (this.page.isClosed()) return undefined;
    try {
      return pageKeyFromUrl(this.page.url());
    } catch {
      return undefined;
    }
  }

  private pageKeyFields(): Pick<SafeLogFields, 'pageKey'> {
    const pageKey = this.currentPageKey();
    return pageKey ? { pageKey } : {};
  }
}

const safeFailureCode = (error: unknown, fallback: string): string => {
  const message = error instanceof Error ? error.message : '';
  return /^[A-Z][A-Z0-9_]{2,80}$/.test(message) ? message : fallback;
};

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
