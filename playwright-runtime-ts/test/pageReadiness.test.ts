import assert from 'node:assert/strict';
import test from 'node:test';
import type { Page } from 'playwright-core';
import { PageReadiness } from '../src/browser/pageReadiness';

type Observation = { readyState: string; nodeCount: number };

const fakePage = (
  observations: Array<Observation | Error>,
  options: { networkIdleFails?: boolean; url?: string } = {},
): Page => {
  let index = 0;
  return {
    waitForLoadState: async () => {
      if (options.networkIdleFails) throw new Error('network remains active');
    },
    evaluate: async () => {
      const value = observations[Math.min(index, observations.length - 1)];
      index += 1;
      if (value instanceof Error) throw value;
      return value;
    },
    waitForTimeout: async () => undefined,
    url: () => options.url ?? 'https://example.test/page',
  } as unknown as Page;
};

const readiness = (timeout = 50): PageReadiness => new PageReadiness({
  navigationTimeoutMs: 50,
  stabilityTimeoutMs: timeout,
  stableSamples: 3,
  sampleIntervalMs: 1,
});

test('scanner readiness succeeds after two confirming stable node-count samples', async () => {
  const result = await readiness().waitForScannerStable(fakePage([
    { readyState: 'interactive', nodeCount: 10 },
    { readyState: 'complete', nodeCount: 10 },
    { readyState: 'complete', nodeCount: 10 },
  ]));
  assert.equal(result.outcome, 'STABLE');
  assert.equal(result.samples, 3);
  assert.equal(result.nodeCount, 10);
});

test('scanner readiness ignores network and body-content churn when actionable node count is stable', async () => {
  const result = await readiness().waitForScannerStable(fakePage([
    { readyState: 'complete', nodeCount: 21 },
    { readyState: 'complete', nodeCount: 21 },
    { readyState: 'complete', nodeCount: 21 },
  ], { networkIdleFails: true }));
  assert.equal(result.outcome, 'STABLE');
});

test('scanner readiness reports evaluation interruption without trapping the scanner lease', async () => {
  const result = await readiness().waitForScannerStable(fakePage([new Error('context changed')], {
    networkIdleFails: true,
  }));
  assert.equal(result.outcome, 'EVALUATION_INTERRUPTED');
  assert.equal(result.samples, 0);
});

test('scanner readiness reports a bounded timeout instead of throwing', async () => {
  let count = 0;
  const page = {
    waitForLoadState: async () => { throw new Error('busy'); },
    evaluate: async () => ({ readyState: 'complete', nodeCount: count++ }),
    waitForTimeout: async (milliseconds: number) => new Promise(resolve => setTimeout(resolve, milliseconds)),
    url: () => 'https://example.test/page',
  } as unknown as Page;
  const result = await readiness(8).waitForScannerStable(page);
  assert.equal(result.outcome, 'TIMEOUT');
  assert.ok(result.durationMs < 100);
});
