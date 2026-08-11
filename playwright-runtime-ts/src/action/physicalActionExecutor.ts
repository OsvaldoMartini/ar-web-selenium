import {
  ActionDiagnostic,
  hasUnsupportedShadowScope,
  PhysicalAction,
  PhysicalActionRequest,
  PhysicalActionResult,
  RegistryActionCandidate,
  ResolutionStage,
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

const MAX_LIVE_CANDIDATES = 100;
const LIVE_ACTION_SELECTOR = 'a,button,input,textarea,select,option,label,summary,[role],[onclick],[tabindex]';
const LIVE_OUTPUT_SELECTOR = 'input,textarea,select,output,[role],p,span,div,td,th,label';

const normalizeName = (value: string | undefined): string =>
  value?.trim().replace(/\s+/g, ' ').toLowerCase() ?? '';

const tag = (value: string | undefined): string => value?.trim().toLowerCase() ?? '';

export class PhysicalActionExecutor {
  async execute(page: ActionPagePort, unsafeRequest: PhysicalActionRequest): Promise<PhysicalActionResult> {
    const request = validatePhysicalActionRequest(unsafeRequest);
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

    let observed = 0;
    let deferredAmbiguity: Probe | undefined;
    const authored = await this.probeSelectors(
      page, request.authoredSelectors, request.iframeXPath ?? '', request.expectedTag ?? '',
      request.action, 'AUTHORED', '', true,
    );
    observed += authored.observed;
    if (authored.target) return this.executeTarget(page, request, authored.target, observed);
    if (authored.ambiguous) deferredAmbiguity = authored;

    if (request.registryCandidates.some(candidate => hasUnsupportedShadowScope(candidate.shadowHost)
        || hasUnsupportedShadowScope(candidate.shadowRoot))) {
      return this.failed(request, 'SHADOW_SCOPE_UNSUPPORTED', 'REGISTRY', observed);
    }

    for (const tier of ['LOCATOR', 'CANONICAL', 'ALIAS'] as const) {
      const stage = `REGISTRY_${tier}` as ResolutionStage;
      const candidates = request.registryCandidates.filter(candidate => candidate.tier === tier);
      const probe = await this.probeRegistryTier(page, request, candidates, stage);
      observed += probe.observed;
      if (probe.target) return this.executeTarget(page, request, probe.target, observed);
      if (probe.ambiguous && !deferredAmbiguity) deferredAmbiguity = probe;
    }

    const canonical = await this.probeLiveName(
      page, request, request.canonicalName, 'LIVE_CANONICAL', request.iframeXPath ?? '',
      request.expectedTag ?? '',
    );
    observed += canonical.observed;
    if (canonical.target) return this.executeTarget(page, request, canonical.target, observed);
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
        if (alias.target) return this.executeTarget(page, request, alias.target, observed);
        if (alias.ambiguous && !deferredAmbiguity) deferredAmbiguity = alias;
      }
    }

    return deferredAmbiguity
      ? this.failed(request, 'AMBIGUOUS_TARGET', deferredAmbiguity.stage, observed)
      : this.failed(request, 'TARGET_NOT_FOUND', 'RESOLUTION', observed);
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
