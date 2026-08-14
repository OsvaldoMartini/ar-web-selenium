import { createHash } from 'node:crypto';
import {
  ActionDiagnostic,
  hasUnsupportedShadowScope,
  PhysicalAction,
  PhysicalActionRequest,
  PhysicalActionResult,
  RegistryActionCandidate,
  ResolutionStage,
  RecoveryCandidate,
  validatePhysicalActionRequest,
} from './actionContracts';

export interface ActionElementInspection {
  readonly visible: boolean;
  readonly frameValidated: boolean;
  readonly shadowValidated: boolean;
  readonly tagValidated: boolean;
  readonly actionValidated: boolean;
  readonly tagName: string;
  readonly names: readonly string[];
  readonly type?: string;
  readonly role?: string;
  readonly xpath?: string;
  readonly css?: string;
  readonly stableAttributes?: Readonly<Record<string, string>>;
}

export interface ActionElementPort {
  sameElement(other: ActionElementPort): Promise<boolean>;
  inspect(action: PhysicalAction, expectedTag: string, requireSameOriginFrame: boolean,
    allowExplicitClickOverride: boolean): Promise<ActionElementInspection>;
  click(): Promise<void>;
  fill(value: string, pressEnter: boolean, pressTab: boolean): Promise<void>;
  read(): Promise<string>;
}

export interface ActionPagePort {
  pageKey(): Promise<string>;
  query(selector: string, iframeXPath: string): Promise<readonly ActionElementPort[]>;
  liveCandidates(action: PhysicalAction, iframeXPath: string): Promise<readonly ActionElementPort[]>;
  waitForRender(delayMs: number): Promise<void>;
}

interface ResolvedTarget {
  readonly element: ActionElementPort;
  readonly stage: ResolutionStage;
  readonly inspection: ActionElementInspection;
}

interface Probe {
  readonly target?: ResolvedTarget;
  readonly ambiguous: boolean;
  readonly stage: ResolutionStage;
  readonly observed: number;
}

interface ResolutionAttempt {
  readonly target?: ResolvedTarget;
  readonly observed: number;
  readonly terminal?: PhysicalActionResult;
}

const MAX_LIVE_CANDIDATES = 100;
const LIVE_ACTION_SELECTOR = 'a,button,input,textarea,select,option,label,summary,[role],[onclick],[tabindex]';
const LIVE_OUTPUT_SELECTOR = 'input,textarea,select,output,[role],p,span,div,td,th,label';
const MAX_RECOVERY_CANDIDATES = 25;

const normalizeName = (value: string | undefined): string =>
  value?.trim().replace(/\s+/g, ' ').toLowerCase() ?? '';

const tag = (value: string | undefined): string => value?.trim().toLowerCase() ?? '';

export class PhysicalActionExecutor {
  constructor(
    private readonly resolutionTimeoutMs = 10_000,
    private readonly retryIntervalMs = 150,
  ) {
    if (!Number.isFinite(resolutionTimeoutMs) || resolutionTimeoutMs < 0
        || !Number.isFinite(retryIntervalMs) || retryIntervalMs <= 0) {
      throw new Error('ACTION_RESOLUTION_OPTIONS_INVALID');
    }
  }

  async execute(
    page: ActionPagePort,
    unsafeRequest: PhysicalActionRequest,
    signal?: AbortSignal,
  ): Promise<PhysicalActionResult> {
    const request = validatePhysicalActionRequest(unsafeRequest);
    signal?.throwIfAborted();
    let initialPageKey: string;
    try {
      initialPageKey = await page.pageKey();
    } catch {
      return this.failed(request, 'PAGE_CONTEXT_UNAVAILABLE', 'PAGE');
    }
    if (initialPageKey !== request.pageKey) return this.failed(request, 'PAGE_CONTEXT_CHANGED', 'PAGE');
    if (hasUnsupportedShadowScope(request.shadowHost)
        || hasUnsupportedShadowScope(request.shadowRoot)) {
      return this.failed(request, 'SHADOW_SCOPE_UNSUPPORTED', 'AUTHORED');
    }

    const deadline = Date.now() + this.resolutionTimeoutMs;
    while (true) {
      signal?.throwIfAborted();
      const attempt = await this.resolveOnce(page, request);
      signal?.throwIfAborted();
      if (attempt.target) {
        return this.executeTarget(page, request, attempt.target, attempt.observed);
      }
      if (attempt.terminal) {
        if (!attempt.terminal.ok && attempt.terminal.diagnostic.code === 'AMBIGUOUS_TARGET') {
          return this.failedWithRecovery(
            request,
            'AMBIGUOUS_TARGET',
            attempt.terminal.diagnostic.stage,
            attempt.observed,
            await this.recoveryCandidates(page, request, signal),
          );
        }
        return attempt.terminal;
      }
      const remaining = deadline - Date.now();
      if (remaining <= 0) {
        return this.failedWithRecovery(
          request, 'TARGET_NOT_FOUND', 'RESOLUTION', attempt.observed,
          await this.recoveryCandidates(page, request, signal),
        );
      }
      // Keep the render wait interruptible without destroying the owner-scoped browser.
      await this.waitForRender(page, Math.min(this.retryIntervalMs, remaining), signal);
    }
  }

  private async waitForRender(
    page: ActionPagePort,
    delayMs: number,
    signal?: AbortSignal,
  ): Promise<void> {
    if (!signal) {
      await page.waitForRender(delayMs);
      return;
    }
    signal.throwIfAborted();
    await new Promise<void>((resolve, reject) => {
      const aborted = (): void => {
        signal.removeEventListener('abort', aborted);
        reject(new Error('ACTION_CANCELLED'));
      };
      signal.addEventListener('abort', aborted, { once: true });
      void page.waitForRender(delayMs).then(
        () => {
          signal.removeEventListener('abort', aborted);
          resolve();
        },
        error => {
          signal.removeEventListener('abort', aborted);
          reject(error);
        },
      );
    });
  }

  private async recoveryCandidates(
    page: ActionPagePort,
    request: PhysicalActionRequest,
    signal?: AbortSignal,
  ): Promise<readonly RecoveryCandidate[]> {
    signal?.throwIfAborted();
    const registry = [...new Map(request.registryCandidates.map(candidate =>
      [candidate.candidateId, candidate])).values()];
    if (registry.length === 0) return [];
    let elements: readonly ActionElementPort[];
    try {
      elements = await page.liveCandidates(request.action, request.iframeXPath ?? '');
    } catch (error) {
      if (signal?.aborted) throw error;
      return [];
    }
    const rows: RecoveryCandidate[] = [];
    for (const element of elements.slice(0, MAX_LIVE_CANDIDATES)) {
      signal?.throwIfAborted();
      const inspection = await element.inspect(
        request.action, '', Boolean(request.iframeXPath?.trim()), false,
      );
      if (!inspection.visible || !inspection.actionValidated) continue;
      for (const saved of registry) {
        signal?.throwIfAborted();
        const score = this.scoreRecovery(saved, inspection, request);
        if (score.confidence < 0.35) continue;
        const liveXPath = inspection.xpath ?? '';
        const liveCss = inspection.css ?? '';
        const liveAttributes = inspection.stableAttributes ?? {};
        const basis = [saved.candidateId, liveXPath, liveCss,
          JSON.stringify(liveAttributes)].join('\u0000');
        const recoveryCandidateId = this.sha256(basis);
        rows.push({
          recoveryCandidateId,
          registryCandidateId: saved.candidateId,
          savedCanonicalName: saved.canonicalName ?? request.canonicalName ?? '',
          savedClientName: saved.clientName ?? request.clientName ?? '',
          ocrMappedName: saved.ocrName ?? '',
          previousXPath: saved.xpath ?? '',
          previousCustomXPath: saved.customXPath ?? '',
          previousCss: saved.cssSelector ?? '',
          previousStableAttributes: saved.stableAttributes ?? {},
          newXPath: liveXPath,
          newCss: liveCss,
          newStableAttributes: liveAttributes,
          previousPageIdentity: saved.previousPageKey ?? request.pageKey,
          currentPageIdentity: request.pageKey,
          tag: inspection.tagName,
          type: inspection.type ?? '',
          role: inspection.role ?? '',
          expectedAction: request.action,
          confidence: score.confidence,
          reasons: score.reasons,
          ambiguityWarnings: score.warnings,
          matches: {
            xpath: this.match(saved.xpath, liveXPath),
            customXPath: this.match(saved.customXPath, liveXPath),
            css: this.match(saved.cssSelector, liveCss),
            stableAttributes: this.attributeMatch(saved.stableAttributes, liveAttributes),
            frame: saved.iframeXPath?.trim() ? Boolean(request.iframeXPath?.trim()) : null,
            shadow: saved.shadowHost?.trim() || saved.shadowRoot?.trim()
              ? inspection.shadowValidated : null,
          },
        });
      }
    }
    return rows.sort((left, right) => right.confidence - left.confidence
      || left.registryCandidateId - right.registryCandidateId
      || left.recoveryCandidateId.localeCompare(right.recoveryCandidateId))
      .slice(0, MAX_RECOVERY_CANDIDATES);
  }

  private scoreRecovery(
    saved: RegistryActionCandidate,
    live: ActionElementInspection,
    request: PhysicalActionRequest,
  ): { confidence: number; reasons: string[]; warnings: string[] } {
    const expectedNames = [saved.clientName, saved.canonicalName, saved.ocrName,
      request.clientName, request.canonicalName].map(normalizeName).filter(Boolean);
    const liveNames = live.names.map(normalizeName).filter(Boolean);
    const exactName = expectedNames.some(expected => liveNames.includes(expected));
    const tokenName = expectedNames.some(expected => liveNames.some(candidate =>
      expected.length >= 4 && candidate.length >= 4
      && (candidate.includes(expected) || expected.includes(candidate))));
    const expectedTag = tag(saved.expectedTag ?? request.expectedTag);
    const expectedType = normalizeName(saved.expectedType);
    const expectedRole = normalizeName(saved.expectedRole);
    const tagMatch = !expectedTag || live.tagName === expectedTag;
    const typeMatch = !expectedType || normalizeName(live.type) === expectedType;
    const roleMatch = !expectedRole || normalizeName(live.role) === expectedRole;
    const attrs = this.attributeMatch(saved.stableAttributes, live.stableAttributes ?? {}) === true;
    let confidence = 0;
    const reasons: string[] = [];
    if (exactName) { confidence += 0.55; reasons.push('Exact saved/OCR name match'); }
    else if (tokenName) { confidence += 0.3; reasons.push('Partial normalized name match'); }
    if (expectedTag && tagMatch) { confidence += 0.15; reasons.push('Compatible tag'); }
    if (expectedType && typeMatch) { confidence += 0.1; reasons.push('Compatible type'); }
    if (expectedRole && roleMatch) { confidence += 0.1; reasons.push('Compatible role'); }
    if (attrs) { confidence += 0.1; reasons.push('Stable attribute match'); }
    const warnings: string[] = [];
    if (!exactName) warnings.push('Name is not an exact match');
    if (!tagMatch || !typeMatch || !roleMatch) warnings.push('Element semantics changed');
    return { confidence: Math.min(1, Math.round(confidence * 100) / 100), reasons, warnings };
  }

  private match(previous: string | undefined, current: string): boolean | null {
    const left = previous?.trim() ?? '';
    const right = current.trim();
    return !left || !right ? null : left === right;
  }

  private attributeMatch(
    previous: Readonly<Record<string, string>> | undefined,
    current: Readonly<Record<string, string>>,
  ): boolean | null {
    const entries = Object.entries(previous ?? {});
    if (entries.length === 0 || Object.keys(current).length === 0) return null;
    return entries.some(([key, value]) => current[key] === value);
  }

  private sha256(value: string): string {
    return createHash('sha256').update(value, 'utf8').digest('hex');
  }

  private async resolveOnce(
    page: ActionPagePort,
    request: PhysicalActionRequest,
  ): Promise<ResolutionAttempt> {
    let observed = 0;
    let deferredAmbiguity: Probe | undefined;
    const authored = await this.probeSelectors(
      page, request.authoredSelectors, request.iframeXPath ?? '', request.expectedTag ?? '',
      request.action, 'AUTHORED', '', true,
    );
    observed += authored.observed;
    if (authored.target) return { target: authored.target, observed };
    if (authored.ambiguous) deferredAmbiguity = authored;

    if (request.registryCandidates.some(candidate => hasUnsupportedShadowScope(candidate.shadowHost)
        || hasUnsupportedShadowScope(candidate.shadowRoot))) {
      return {
        observed,
        terminal: this.failed(request, 'SHADOW_SCOPE_UNSUPPORTED', 'REGISTRY', observed),
      };
    }

    for (const tier of ['LOCATOR', 'CANONICAL', 'ALIAS'] as const) {
      const stage = `REGISTRY_${tier}` as ResolutionStage;
      const candidates = request.registryCandidates.filter(candidate => candidate.tier === tier);
      const probe = await this.probeRegistryTier(page, request, candidates, stage);
      observed += probe.observed;
      if (probe.target) return { target: probe.target, observed };
      if (probe.ambiguous && !deferredAmbiguity) deferredAmbiguity = probe;
    }

    const canonical = await this.probeLiveName(
      page, request, request.canonicalName, 'LIVE_CANONICAL', request.iframeXPath ?? '',
      request.expectedTag ?? '',
    );
    observed += canonical.observed;
    if (canonical.target) return { target: canonical.target, observed };
    if (canonical.ambiguous && !deferredAmbiguity) deferredAmbiguity = canonical;

    const aliasOwners = request.registryCandidates.filter(candidate => candidate.tier === 'ALIAS');
    if (aliasOwners.length > 1 && !deferredAmbiguity) {
      deferredAmbiguity = { ambiguous: true, stage: 'LIVE_ALIAS', observed: aliasOwners.length };
    } else if (aliasOwners.length === 1) {
      const owner = aliasOwners[0];
      if (owner) {
        const alias = await this.probeLiveName(
          page, request, request.clientName, 'LIVE_ALIAS',
          request.iframeXPath ?? owner.iframeXPath ?? '', request.expectedTag ?? owner.expectedTag ?? '',
        );
        observed += alias.observed;
        if (alias.target) return { target: alias.target, observed };
        if (alias.ambiguous && !deferredAmbiguity) deferredAmbiguity = alias;
      }
    }

    return deferredAmbiguity
      ? {
        observed,
        terminal: this.failed(request, 'AMBIGUOUS_TARGET', deferredAmbiguity.stage, observed),
      }
      : { observed };
  }

  private async probeRegistryTier(
    page: ActionPagePort,
    request: PhysicalActionRequest,
    candidates: readonly RegistryActionCandidate[],
    stage: ResolutionStage,
  ): Promise<Probe> {
    const unique: ResolvedTarget[] = [];
    let observed = 0;
    for (const candidate of candidates) {
      if (request.iframeXPath?.trim()
          && request.iframeXPath.trim() !== candidate.iframeXPath?.trim()) continue;
      const probe = await this.probeSelectors(
        page, candidate.selectors, candidate.iframeXPath ?? '',
        candidate.expectedTag ?? request.expectedTag ?? '', request.action, stage, '',
        stage === 'REGISTRY_LOCATOR',
      );
      observed += probe.observed;
      if (probe.ambiguous) return { ambiguous: true, stage, observed };
      if (!probe.target) continue;
      let duplicate = false;
      for (const target of unique) {
        if (await target.element.sameElement(probe.target.element)) {
          duplicate = true;
          break;
        }
      }
      if (!duplicate) unique.push(probe.target);
      if (unique.length > 1) return { ambiguous: true, stage, observed };
    }
    const target = unique[0];
    return target ? { target, ambiguous: false, stage, observed } : { ambiguous: false, stage, observed };
  }

  private async probeSelectors(
    page: ActionPagePort,
    selectors: readonly string[],
    iframeXPath: string,
    expectedTag: string,
    action: PhysicalAction,
    stage: ResolutionStage,
    expectedName: string,
    allowExplicitClickOverride: boolean,
  ): Promise<Probe> {
    let observed = 0;
    let ambiguous = false;
    for (const raw of selectors) {
      try {
        const elements = await page.query(raw.trim(), iframeXPath.trim());
        if (elements.length > MAX_LIVE_CANDIDATES) {
          observed += elements.length;
          ambiguous = true;
          continue;
        }
        const valid: ResolvedTarget[] = [];
        for (const element of elements) {
          const inspection = await element.inspect(
            action, tag(expectedTag), iframeXPath.trim().length > 0, allowExplicitClickOverride,
          );
          if (!inspection.visible) continue;
          observed += 1;
          if (expectedName && !inspection.names.some(name => normalizeName(name) === expectedName)) continue;
          if (inspection.frameValidated && inspection.shadowValidated
              && inspection.tagValidated && inspection.actionValidated) {
            valid.push({ element, stage, inspection });
          }
        }
        if (valid.length > 1) {
          ambiguous = true;
          continue;
        }
        const target = valid[0];
        if (target) return { target, ambiguous: false, stage, observed };
      } catch {
        // A malformed or stale selector grants no authority. Continue without acting.
      }
    }
    return { ambiguous, stage, observed };
  }

  private async probeLiveName(
    page: ActionPagePort,
    request: PhysicalActionRequest,
    name: string | undefined,
    stage: ResolutionStage,
    iframeXPath: string,
    expectedTag: string,
  ): Promise<Probe> {
    const expected = normalizeName(name);
    if (!expected) return { ambiguous: false, stage, observed: 0 };
    try {
      const elements = await page.liveCandidates(request.action, iframeXPath);
      if (elements.length > MAX_LIVE_CANDIDATES) {
        return { ambiguous: true, stage, observed: elements.length };
      }
      const valid: ResolvedTarget[] = [];
      let observed = 0;
      for (const element of elements) {
        const inspection = await element.inspect(
          request.action, tag(expectedTag), iframeXPath.trim().length > 0, false,
        );
        if (!inspection.names.some(candidate => normalizeName(candidate) === expected)) continue;
        observed += 1;
        if (inspection.visible && inspection.frameValidated && inspection.shadowValidated
            && inspection.tagValidated && inspection.actionValidated) {
          valid.push({ element, stage, inspection });
        }
      }
      if (valid.length > 1) return { ambiguous: true, stage, observed };
      const target = valid[0];
      return target ? { target, ambiguous: false, stage, observed } : { ambiguous: false, stage, observed };
    } catch {
      return { ambiguous: false, stage, observed: 0 };
    }
  }

  private async executeTarget(
    page: ActionPagePort,
    request: PhysicalActionRequest,
    target: ResolvedTarget,
    observed: number,
  ): Promise<PhysicalActionResult> {
    let currentPageKey: string;
    try {
      currentPageKey = await page.pageKey();
    } catch {
      return this.failed(request, 'PAGE_CONTEXT_CHANGED', 'PAGE', observed);
    }
    if (currentPageKey !== request.pageKey) {
      return this.failed(request, 'PAGE_CONTEXT_CHANGED', 'PAGE', observed);
    }
    try {
      let output: string | undefined;
      if (request.action === 'CLICK') await target.element.click();
      if (request.action === 'INPUT') {
        await target.element.fill(
          request.inputValue ?? '', request.pressEnter === true, request.pressTab === true,
        );
      }
      if (request.action === 'OUTPUT') output = await target.element.read();
      const diagnostic = this.diagnostic(
        request, 'COMPLETED', target.stage, observed, target.inspection, 1,
      );
      return output === undefined ? { ok: true, diagnostic } : { ok: true, output, diagnostic };
    } catch {
      return {
        ok: false,
        diagnostic: this.diagnostic(
          request, 'ACTION_FAILED', target.stage, observed, target.inspection, 1,
        ),
      };
    }
  }

  private failed(
    request: PhysicalActionRequest,
    code: string,
    stage: ResolutionStage,
    observed = 0,
  ): PhysicalActionResult {
    return {
      ok: false,
      diagnostic: this.diagnostic(request, code, stage, observed, undefined, 0),
    };
  }

  private failedWithRecovery(
    request: PhysicalActionRequest,
    code: string,
    stage: ResolutionStage,
    observed: number,
    candidates: readonly RecoveryCandidate[],
  ): PhysicalActionResult {
    return {
      ok: false,
      diagnostic: this.diagnostic(request, code, stage, observed, undefined, 0),
      recovery: { state: 'AWAITING_USER', candidates },
    };
  }

  private diagnostic(
    request: PhysicalActionRequest,
    code: string,
    stage: ResolutionStage,
    observed: number,
    inspection: ActionElementInspection | undefined,
    physicalAttempts: 0 | 1,
  ): ActionDiagnostic {
    return {
      code,
      stage,
      action: request.action,
      instructionId: request.instructionId,
      registryCandidateCount: request.registryCandidates.length,
      liveCandidateCount: observed,
      frameValidated: inspection?.frameValidated ?? false,
      shadowValidated: inspection?.shadowValidated ?? false,
      tagValidated: inspection?.tagValidated ?? false,
      actionValidated: inspection?.actionValidated ?? false,
      physicalAttempts,
    };
  }
}

export const liveSelectorFor = (action: PhysicalAction): string =>
  action === 'OUTPUT' ? LIVE_OUTPUT_SELECTOR : LIVE_ACTION_SELECTOR;
