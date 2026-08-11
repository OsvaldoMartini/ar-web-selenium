import { Page } from 'playwright-core';

export interface PageReadinessOptions {
  readonly navigationTimeoutMs: number;
  readonly stabilityTimeoutMs: number;
  readonly stableSamples: number;
  readonly sampleIntervalMs: number;
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
