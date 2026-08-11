import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  ActionElementInspection,
  ActionElementPort,
  ActionPagePort,
  PhysicalActionExecutor,
} from '../src/action/physicalActionExecutor';
import { PhysicalAction, PhysicalActionRequest } from '../src/action/actionContracts';
import { pageKeyFromUrl } from '../src/action/pageIdentity';

class FakeElement implements ActionElementPort {
  clicks = 0;
  fills: string[] = [];

  constructor(
    readonly identity: string,
    private readonly names: readonly string[] = [],
    private readonly tagName = 'button',
    private readonly output = '',
    private readonly validActions: readonly PhysicalAction[] = ['CLICK', 'OUTPUT'],
  ) {}

  async sameElement(other: ActionElementPort): Promise<boolean> {
    return other instanceof FakeElement && other.identity === this.identity;
  }

  async inspect(
    action: PhysicalAction,
    expectedTag: string,
    _requireSameOriginFrame: boolean,
    allowExplicitClickOverride: boolean,
  ): Promise<ActionElementInspection> {
    return {
      visible: true,
      frameValidated: true,
      shadowValidated: true,
      tagValidated: !expectedTag || expectedTag === this.tagName,
      actionValidated: this.validActions.includes(action)
        || (action === 'CLICK' && allowExplicitClickOverride),
      tagName: this.tagName,
      names: this.names,
    };
  }

  async click(): Promise<void> {
    this.clicks += 1;
  }

  async fill(value: string): Promise<void> {
    this.fills.push(value);
  }

  async read(): Promise<string> {
    return this.output;
  }
}

class FakePage implements ActionPagePort {
  readonly queries = new Map<string, readonly FakeElement[]>();
  live: readonly FakeElement[] = [];
  pageKeys = [PAGE_KEY];
  private pageKeyReads = 0;

  async pageKey(): Promise<string> {
    const index = Math.min(this.pageKeyReads, this.pageKeys.length - 1);
    this.pageKeyReads += 1;
    return this.pageKeys[index] ?? PAGE_KEY;
  }

  async query(selector: string, iframeXPath: string): Promise<readonly ActionElementPort[]> {
    return this.queries.get(`${iframeXPath}\u0000${selector}`) ?? [];
  }

  async liveCandidates(): Promise<readonly ActionElementPort[]> {
    return this.live;
  }
}

const PAGE_KEY = `url-v1:${'a'.repeat(64)}`;

const request = (overrides: Partial<PhysicalActionRequest> = {}): PhysicalActionRequest => ({
  instructionId: 1733,
  sequence: 1,
  action: 'CLICK',
  pageKey: PAGE_KEY,
  authoredSelectors: ['#login'],
  registryCandidates: [],
  canonicalName: 'log_in',
  expectedTag: 'button',
  ...overrides,
});

test('uses the first unique authored selector and performs exactly one click', async () => {
  const page = new FakePage();
  const target = new FakeElement('login');
  page.queries.set('\u0000#login', [target]);

  const result = await new PhysicalActionExecutor().execute(page, request());

  assert.equal(result.ok, true);
  assert.equal(result.diagnostic.stage, 'AUTHORED');
  assert.equal(result.diagnostic.physicalAttempts, 1);
  assert.equal(target.clicks, 1);
});

test('defers stale authored ambiguity and uses one owner-scoped registry locator', async () => {
  const page = new FakePage();
  const staleOne = new FakeElement('stale-1');
  const staleTwo = new FakeElement('stale-2');
  const healed = new FakeElement('healed');
  page.queries.set('\u0000#login', [staleOne, staleTwo]);
  page.queries.set('\u0000[data-testid="login"]', [healed]);

  const result = await new PhysicalActionExecutor().execute(page, request({
    registryCandidates: [{
      candidateId: 91,
      tier: 'LOCATOR',
      selectors: ['[data-testid="login"]'],
      expectedTag: 'button',
    }],
  }));

  assert.equal(result.ok, true);
  assert.equal(result.diagnostic.stage, 'REGISTRY_LOCATOR');
  assert.equal(staleOne.clicks + staleTwo.clicks, 0);
  assert.equal(healed.clicks, 1);
});

test('refuses a registry tier resolving to two elements with zero physical attempts', async () => {
  const page = new FakePage();
  const first = new FakeElement('first');
  const second = new FakeElement('second');
  page.queries.set('\u0000#first', [first]);
  page.queries.set('\u0000#second', [second]);

  const result = await new PhysicalActionExecutor().execute(page, request({
    authoredSelectors: [],
    registryCandidates: [
      { candidateId: 1, tier: 'LOCATOR', selectors: ['#first'] },
      { candidateId: 2, tier: 'LOCATOR', selectors: ['#second'] },
    ],
  }));

  assert.equal(result.ok, false);
  assert.equal(result.diagnostic.code, 'AMBIGUOUS_TARGET');
  assert.equal(result.diagnostic.stage, 'REGISTRY_LOCATOR');
  assert.equal(result.diagnostic.physicalAttempts, 0);
  assert.equal(first.clicks + second.clicks, 0);
});

test('supports INPUT and preserves a legitimate empty OUTPUT value', async () => {
  const inputPage = new FakePage();
  const input = new FakeElement('username', ['username'], 'input', '', ['INPUT']);
  inputPage.queries.set('\u0000#username', [input]);
  const executor = new PhysicalActionExecutor();

  const inputResult = await executor.execute(inputPage, request({
    action: 'INPUT',
    authoredSelectors: ['#username'],
    expectedTag: 'input',
    canonicalName: 'username',
    inputValue: 'protected runtime value',
  }));
  assert.equal(inputResult.ok, true);
  assert.deepEqual(input.fills, ['protected runtime value']);

  const outputPage = new FakePage();
  const output = new FakeElement('balance', ['balance'], 'output', '', ['OUTPUT']);
  outputPage.queries.set('\u0000#balance', [output]);
  const outputResult = await executor.execute(outputPage, request({
    action: 'OUTPUT',
    authoredSelectors: ['#balance'],
    expectedTag: 'output',
    canonicalName: 'balance',
  }));
  assert.equal(outputResult.ok, true);
  assert.equal(outputResult.ok && outputResult.output, '');
});

test('revalidates page identity immediately before the physical action', async () => {
  const page = new FakePage();
  const target = new FakeElement('login');
  page.queries.set('\u0000#login', [target]);
  page.pageKeys = [PAGE_KEY, `url-v1:${'b'.repeat(64)}`];

  const result = await new PhysicalActionExecutor().execute(page, request());

  assert.equal(result.ok, false);
  assert.equal(result.diagnostic.code, 'PAGE_CONTEXT_CHANGED');
  assert.equal(result.diagnostic.physicalAttempts, 0);
  assert.equal(target.clicks, 0);
});

test('keeps shadow-scoped instructions fail-closed before querying the page', async () => {
  const page = new FakePage();
  const result = await new PhysicalActionExecutor().execute(page, request({ shadowHost: '#root' }));

  assert.equal(result.ok, false);
  assert.equal(result.diagnostic.code, 'SHADOW_SCOPE_UNSUPPORTED');
  assert.equal(result.diagnostic.physicalAttempts, 0);
  assert.equal(page.queries.size, 0);
});

test('a stale shadow registry row cannot block one unique authored target', async () => {
  const page = new FakePage();
  const target = new FakeElement('login');
  page.queries.set('\u0000#login', [target]);

  const result = await new PhysicalActionExecutor().execute(page, request({
    registryCandidates: [{
      candidateId: 99,
      tier: 'LOCATOR',
      selectors: ['#inside-shadow'],
      shadowHost: '#legacy-root',
    }],
  }));

  assert.equal(result.ok, true);
  assert.equal(result.diagnostic.stage, 'AUTHORED');
  assert.equal(target.clicks, 1);
});

test('uses exact live canonical then client-alias names only when uniquely resolved', async () => {
  const canonicalPage = new FakePage();
  const canonical = new FakeElement('canonical', ['  LOG_IN  ']);
  canonicalPage.live = [canonical];
  const executor = new PhysicalActionExecutor();

  const canonicalResult = await executor.execute(canonicalPage, request({
    authoredSelectors: [],
  }));
  assert.equal(canonicalResult.ok, true);
  assert.equal(canonicalResult.diagnostic.stage, 'LIVE_CANONICAL');
  assert.equal(canonical.clicks, 1);

  const aliasPage = new FakePage();
  const alias = new FakeElement('alias', ['Banca   Stato']);
  aliasPage.live = [alias];
  const aliasResult = await executor.execute(aliasPage, request({
    authoredSelectors: [],
    canonicalName: 'missing canonical',
    clientName: 'banca stato',
    registryCandidates: [{ candidateId: 17, tier: 'ALIAS', selectors: [] }],
  }));
  assert.equal(aliasResult.ok, true);
  assert.equal(aliasResult.diagnostic.stage, 'LIVE_ALIAS');
  assert.equal(alias.clicks, 1);
});

test('keeps Node page keys compatible with established Java live-page identities', () => {
  assert.equal(
    pageKeyFromUrl('https://www.bancastato.ch/apertura-conto'),
    'url-v1:9b090d75a5a21dc1018007b2aebdd2d2c50f844d9f706279f8e7d465c96775e8',
  );
  assert.equal(
    pageKeyFromUrl('https://www.lloydsbank.com/'),
    'url-v1:b8f9e17b00eeafa88d8705a09d8efa24cdb4af3c362fd4421003665bbce092a2',
  );
});
