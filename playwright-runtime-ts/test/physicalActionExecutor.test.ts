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
      type: this.tagName === 'input' ? 'text' : '',
      role: this.tagName === 'button' ? 'button' : '',
      xpath: `/html[1]/body[1]/${this.tagName}[1]`,
      css: `html:nth-of-type(1) > body:nth-of-type(1) > ${this.tagName}:nth-of-type(1)`,
      stableAttributes: { 'data-testid': this.identity },
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
  readonly availableAfter = new Map<string, number>();
  readonly queryAttempts = new Map<string, number>();
  live: readonly FakeElement[] = [];
  pageKeys = [PAGE_KEY];
  private pageKeyReads = 0;

  async pageKey(): Promise<string> {
    const index = Math.min(this.pageKeyReads, this.pageKeys.length - 1);
    this.pageKeyReads += 1;
    return this.pageKeys[index] ?? PAGE_KEY;
  }

  async query(selector: string, iframeXPath: string): Promise<readonly ActionElementPort[]> {
    const key = `${iframeXPath}\u0000${selector}`;
    const attempts = (this.queryAttempts.get(key) ?? 0) + 1;
    this.queryAttempts.set(key, attempts);
    if (attempts <= (this.availableAfter.get(key) ?? 0)) return [];
    return this.queries.get(key) ?? [];
  }

  async liveCandidates(): Promise<readonly ActionElementPort[]> {
    return this.live;
  }

  async waitForRender(delayMs: number): Promise<void> {
    await new Promise(resolve => setTimeout(resolve, delayMs));
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

test('honors Test ID priority without probing a stale later XPath selector', async () => {
  const page = new FakePage();
  const target = new FakeElement('test-id-login');
  page.queries.set('\u0000[data-testid="login-action"]', [target]);
  page.queries.set('\u0000xpath=//button[@id="stale-login"]', []);

  const result = await new PhysicalActionExecutor().execute(page, request({
    authoredSelectors: ['[data-testid="login-action"]', 'xpath=//button[@id="stale-login"]'],
  }));

  assert.equal(result.ok, true);
  assert.equal(result.diagnostic.stage, 'AUTHORED');
  assert.equal(target.clicks, 1);
  assert.equal(page.queryAttempts.get('\u0000[data-testid="login-action"]'), 1);
  assert.equal(page.queryAttempts.has('\u0000xpath=//button[@id="stale-login"]'), false);
});

test('waits within one bounded deadline for a late rendered authored element', async () => {
  const page = new FakePage();
  const target = new FakeElement('late-login');
  page.queries.set('\u0000#login', [target]);
  page.availableAfter.set('\u0000#login', 2);

  const result = await new PhysicalActionExecutor(100, 1).execute(page, request());

  assert.equal(result.ok, true);
  assert.equal(result.diagnostic.stage, 'AUTHORED');
  assert.equal(page.queryAttempts.get('\u0000#login'), 3);
  assert.equal(target.clicks, 1);
});

test('returns target not found after the shared render deadline expires', async () => {
  const page = new FakePage();

  const result = await new PhysicalActionExecutor(8, 1).execute(page, request({
    canonicalName: '',
  }));

  assert.equal(result.ok, false);
  assert.equal(result.diagnostic.code, 'TARGET_NOT_FOUND');
  assert.equal(result.diagnostic.physicalAttempts, 0);
  assert.ok((page.queryAttempts.get('\u0000#login') ?? 0) > 1);
});

test('interrupts a pending locator render wait without closing the page', async () => {
  const page = new FakePage();
  const controller = new AbortController();
  const pending = new PhysicalActionExecutor(10_000, 1_000).execute(
    page,
    request({ canonicalName: '' }),
    controller.signal,
  );
  setTimeout(() => controller.abort(), 5);

  await assert.rejects(pending, /abort|cancel/i);
  assert.ok((page.queryAttempts.get('\u0000#login') ?? 0) >= 1);
});

test('returns bounded comparison evidence without acting when one target remains unresolved', async () => {
  const page = new FakePage();
  const possible = new FakeElement('new-login', ['account login button']);
  page.live = [possible];

  const result = await new PhysicalActionExecutor(8, 1).execute(page, request({
    authoredSelectors: ['#old-login'],
    canonicalName: 'account login',
    registryCandidates: [{
      candidateId: 41,
      tier: 'CANONICAL',
      selectors: ['#old-login'],
      canonicalName: 'account login',
      clientName: 'sign in',
      ocrName: 'Account Login',
      previousPageKey: PAGE_KEY,
      xpath: '/html[1]/body[1]/button[2]',
      customXPath: '//*[@id="old-login"]',
      cssSelector: '#old-login',
      stableAttributes: { 'data-testid': 'old-login' },
      expectedTag: 'button',
      expectedRole: 'button',
    }],
  }));

  assert.equal(result.ok, false);
  assert.equal(possible.clicks, 0);
  assert.equal(result.diagnostic.physicalAttempts, 0);
  assert.equal(result.ok || result.recovery?.state, 'AWAITING_USER');
  const candidate = !result.ok ? result.recovery?.candidates[0] : undefined;
  assert.equal(candidate?.registryCandidateId, 41);
  assert.equal(candidate?.matches.xpath, false);
  assert.equal(candidate?.matches.css, false);
  assert.equal(candidate?.tag, 'button');
  assert.ok((candidate?.confidence ?? 0) >= 0.5);
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
