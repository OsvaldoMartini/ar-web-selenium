import { Page } from 'playwright-core';

export interface PageReadinessOptions {
  readonly navigationTimeoutMs: number;
  readonly stabilityTimeoutMs: number;
  readonly stableSamples: number;
  readonly sampleIntervalMs: number;
}

export interface ScannerReadinessResult {
  readonly outcome: 'STABLE' | 'TIMEOUT' | 'EVALUATION_INTERRUPTED';
  readonly samples: number;
  readonly stableSamples: number;
  readonly readyState: string;
  readonly nodeCount: number;
  readonly durationMs: number;
}

const safeWebUrl = (value: string): boolean => {
  try {
    const url = new URL(value);
    return (url.protocol === 'https:' || url.protocol === 'http:')
      && url.username === ''
      && url.password === '';
  } catch {
    return false;
  }
};

export class PageReadiness {
  constructor(private readonly options: PageReadinessOptions) {}

  async navigate(page: Page, endpoint: string): Promise<void> {
    const response = await page.goto(endpoint, {
      waitUntil: 'domcontentloaded',
      timeout: this.options.navigationTimeoutMs,
    });
    if (response && response.status() >= 400) throw new Error('PAGE_NAVIGATION_HTTP_ERROR');
    await this.waitForStableDocument(page);
  }

  async refresh(page: Page): Promise<void> {
    const response = await page.reload({
      waitUntil: 'domcontentloaded',
      timeout: this.options.navigationTimeoutMs,
    });
    if (response && response.status() >= 400) throw new Error('PAGE_NAVIGATION_HTTP_ERROR');
    await this.waitForStableDocument(page);
  }

  async waitForStable(page: Page): Promise<void> {
    await this.waitForStableDocument(page);
  }

  /**
   * Matches the established Java V1 Page Scanner wait: network-idle is best effort, then only the
   * element count must remain stable. Banking SPAs may continuously update text/HTML without
   * changing the actionable DOM, so scanner readiness never fails solely on body-content churn.
   */
  async waitForScannerStable(page: Page): Promise<ScannerReadinessResult> {
    const started = Date.now();
    const deadline = started + this.options.stabilityTimeoutMs;
    try {
      await page.waitForLoadState('networkidle', {
        timeout: Math.max(1, Math.min(this.options.stabilityTimeoutMs / 2, deadline - Date.now())),
      });
    } catch {
      // Analytics and long-polling are common on banking pages; the bounded DOM probe decides.
    }

    let previousCount = -1;
    let stable = 0;
    let samples = 0;
    let readyState = 'unknown';
    let nodeCount = -1;
    while (Date.now() < deadline && stable < 2) {
      try {
        const observation = await page.evaluate(() => ({
          readyState: document.readyState,
          nodeCount: document.querySelectorAll('*').length,
        }));
        readyState = String(observation?.readyState ?? 'unknown');
        nodeCount = Number.isFinite(observation?.nodeCount) ? Number(observation.nodeCount) : -1;
      } catch {
        return {
          outcome: 'EVALUATION_INTERRUPTED', samples, stableSamples: stable, readyState, nodeCount,
          durationMs: Date.now() - started,
        };
      }
      samples += 1;
      stable = nodeCount >= 0 && nodeCount === previousCount && readyState !== 'loading'
        ? stable + 1 : 0;
      previousCount = nodeCount;
      if (stable < 2 && Date.now() < deadline) {
        await page.waitForTimeout(Math.min(500, Math.max(1, deadline - Date.now())));
      }
    }
    return {
      outcome: stable >= 2 && safeWebUrl(page.url()) ? 'STABLE' : 'TIMEOUT',
      samples,
      stableSamples: stable,
      readyState,
      nodeCount,
      durationMs: Date.now() - started,
    };
  }

  private async waitForStableDocument(page: Page): Promise<void> {
    const deadline = Date.now() + this.options.stabilityTimeoutMs;
    let prior = '';
    let stable = 0;
    while (Date.now() < deadline) {
      const observation = await page.evaluate(() => ({
        readyState: document.readyState,
        nodeCount: document.getElementsByTagName('*').length,
        bodyLength: document.body?.innerHTML.length ?? -1,
      }));
      const current = `${observation.readyState}:${observation.nodeCount}:${observation.bodyLength}`;
      stable = current === prior && observation.readyState !== 'loading' ? stable + 1 : 0;
      prior = current;
      if (stable >= this.options.stableSamples && safeWebUrl(page.url())) return;
      await page.waitForTimeout(this.options.sampleIntervalMs);
    }
    throw new Error('PAGE_READINESS_TIMEOUT');
  }
}
