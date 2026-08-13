const { randomUUID } = require('node:crypto');
const { createServer } = require('node:http');
const { PlaywrightBrowserFactory } = require('../dist/src/browser/playwrightBrowserFactory.js');
const { PlaywrightWorkerPool } = require('../dist/src/pool/playwrightWorkerPool.js');

const RUNS = 5;
const HOLD_MILLIS = 90_000;

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

const mockPage = index => `<!doctype html>
<html><head><title>AR Web isolated run ${index}</title>
<style>
body{font-family:Arial,sans-serif;background:#eef3f8;color:#17324d;margin:0;padding:32px}
main{max-width:680px;margin:auto;background:#fff;border:2px solid #0b5394;border-radius:16px;padding:28px;box-shadow:0 18px 46px #1238}
h1{color:#0b5394} strong{color:#008ba3}.badge{display:inline-block;background:#dff7fb;border:1px solid #20a8bd;border-radius:999px;padding:7px 12px}
</style></head><body><main><div class="badge">ISOLATED PLAYWRIGHT V2</div>
<h1>Browser ${index} of ${RUNS}</h1><p>Dedicated process, context, page and run identity.</p>
<p>Mock acceptance only. No banking page or action was opened.</p></main></body></html>`;

const server = createServer((request, response) => {
  const match = /^\/run\/(\d+)$/.exec(new URL(request.url || '/', 'http://127.0.0.1').pathname);
  if (!match) {
    response.writeHead(404).end();
    return;
  }
  const body = mockPage(Number(match[1]));
  response.writeHead(200, {
    'content-type': 'text/html; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
  });
  response.end(body);
});

const listen = () => new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(0, '127.0.0.1', () => {
    server.off('error', reject);
    resolve(server.address());
  });
});

const closeServer = () => new Promise((resolve, reject) =>
  server.close(error => error ? reject(error) : resolve()));

const waitForReady = async (pool, runIds) => {
  const deadline = Date.now() + 45_000;
  while (Date.now() < deadline) {
    const snapshots = runIds.map(runId => pool.snapshot(runId));
    if (snapshots.every(snapshot => snapshot.state === 'READY')) return snapshots;
    const failure = snapshots.find(snapshot => snapshot.state === 'FAILED');
    if (failure) throw new Error(`RUN_FAILED:${failure.runId}:${failure.failureCode || 'UNKNOWN'}`);
    await sleep(150);
  }
  throw new Error('FIVE_BROWSER_READY_TIMEOUT');
};

const main = async () => {
  const address = await listen();
  if (!address || typeof address === 'string') throw new Error('MOCK_ADDRESS_INVALID');
  const pool = new PlaywrightWorkerPool(new PlaywrightBrowserFactory(), {
    maximumActiveRuns: RUNS,
    maximumQueuedRuns: RUNS,
    maximumActiveRunsPerOrganization: RUNS,
    maximumActiveRunsPerBotJob: 1,
  });
  const now = new Date();
  const runIds = [];
  try {
    for (let index = 1; index <= RUNS; index += 1) {
      const runId = randomUUID();
      runIds.push(runId);
      pool.enqueue({
        run: {
          runId,
          organizationId: index,
          homeBankingId: index,
          botJobId: 10_000 + index,
          workspaceEpoch: 1,
          graphRevision: 'a'.repeat(64),
          planRevision: 'b'.repeat(64),
          dataMode: 'SYNTHETIC',
          state: 'RESERVED',
          createdAt: now.toISOString(),
          expiresAt: new Date(now.getTime() + HOLD_MILLIS + 30_000).toISOString(),
        },
        endpoint: `http://127.0.0.1:${address.port}/run/${index}`,
        browser: { headless: false, channel: 'chrome' },
      });
    }
    const ready = await waitForReady(pool, runIds);
    process.stdout.write(JSON.stringify({ event: 'five-browser.ready', runs: ready }) + '\n');
    await sleep(HOLD_MILLIS);
  } finally {
    await pool.closeAll();
    for (const runId of runIds) {
      try { pool.release(runId); } catch { /* terminal evidence remains in stdout */ }
    }
    await closeServer();
  }
};

main().catch(error => {
  process.stderr.write(JSON.stringify({
    event: 'five-browser.failed',
    code: error instanceof Error ? error.message : 'UNKNOWN',
  }) + '\n');
  process.exitCode = 1;
});
