import { createHash } from 'node:crypto';

export type PhysicalAction = 'CLICK' | 'INPUT' | 'OUTPUT';

export type ResolutionStage =
  | 'AUTHORED'
  | 'REGISTRY_LOCATOR'
  | 'REGISTRY_CANONICAL'
  | 'REGISTRY_ALIAS'
  | 'LIVE_CANONICAL'
  | 'LIVE_ALIAS'
  | 'PAGE'
  | 'REGISTRY'
  | 'RESOLUTION';

export interface RegistryActionCandidate {
  readonly candidateId: number;
  readonly tier: 'LOCATOR' | 'CANONICAL' | 'ALIAS' | 'REVIEW';
  readonly selectors: readonly string[];
  readonly canonicalName?: string;
  readonly clientName?: string;
  readonly expectedTag?: string;
  readonly iframeXPath?: string;
  readonly shadowHost?: string;
  readonly shadowRoot?: string;
  readonly ocrName?: string;
  readonly previousPageKey?: string;
  readonly xpath?: string;
  readonly customXPath?: string;
  readonly cssSelector?: string;
  readonly stableAttributes?: Readonly<Record<string, string>>;
  readonly expectedType?: string;
  readonly expectedRole?: string;
}

export interface LocatorMatchFacts {
  readonly xpath: boolean | null;
  readonly customXPath: boolean | null;
  readonly css: boolean | null;
  readonly stableAttributes: boolean | null;
  readonly frame: boolean | null;
  readonly shadow: boolean | null;
}

export interface RecoveryCandidate {
  readonly origin: 'PREVIOUS' | 'CURRENT';
  readonly recoveryCandidateId: string;
  readonly registryCandidateId: number;
  readonly savedCanonicalName: string;
  readonly savedClientName: string;
  readonly ocrMappedName: string;
  readonly previousXPath: string;
  readonly previousCustomXPath: string;
  readonly previousCss: string;
  readonly previousStableAttributes: Readonly<Record<string, string>>;
  readonly newXPath: string;
  readonly newCss: string;
  readonly newStableAttributes: Readonly<Record<string, string>>;
  readonly previousPageIdentity: string;
  readonly currentPageIdentity: string;
  readonly tag: string;
  readonly type: string;
  readonly role: string;
  readonly expectedAction: PhysicalAction;
  readonly confidence: number;
  readonly reasons: readonly string[];
  readonly ambiguityWarnings: readonly string[];
  readonly matches: LocatorMatchFacts;
}

export interface ActionRecoveryReview {
  readonly state: 'AWAITING_USER';
  readonly candidates: readonly RecoveryCandidate[];
}

/**
 * Internal immutable action facts. A future Java adapter must build these from one frozen plan and
 * the authoritative owner/page-scoped scanned-element registry; React must never author them.
 */
export interface PhysicalActionRequest {
  readonly instructionId: number;
  readonly sequence: number;
  readonly action: PhysicalAction;
  readonly pageKey: string;
  readonly authoredSelectors: readonly string[];
  readonly registryCandidates: readonly RegistryActionCandidate[];
  readonly canonicalName?: string;
  readonly clientName?: string;
  readonly expectedTag?: string;
  readonly iframeXPath?: string;
  readonly shadowHost?: string;
  readonly shadowRoot?: string;
  readonly inputValue?: string;
  readonly pressEnter?: boolean;
  readonly pressTab?: boolean;
}

export interface ActionDiagnostic {
  readonly code: string;
  readonly stage: ResolutionStage;
  readonly action: PhysicalAction;
  readonly instructionId: number;
  readonly registryCandidateCount: number;
  readonly liveCandidateCount: number;
  readonly frameValidated: boolean;
  readonly shadowValidated: boolean;
  readonly tagValidated: boolean;
  readonly actionValidated: boolean;
  readonly physicalAttempts: 0 | 1;
}

export type PhysicalActionResult =
  | {
      readonly ok: true;
      readonly output?: string;
      readonly diagnostic: ActionDiagnostic;
    }
  | {
      readonly ok: false;
      readonly diagnostic: ActionDiagnostic;
      readonly recovery?: ActionRecoveryReview;
    };

const PAGE_KEY_PATTERN = /^url-v1:[0-9a-f]{64}$/;
const SAFE_TAG_PATTERN = /^[a-z][a-z0-9-]{0,31}$/;
const ACTION_KEYS = new Set([
  'instructionId', 'sequence', 'action', 'pageKey', 'authoredSelectors', 'registryCandidates',
  'canonicalName', 'clientName', 'expectedTag', 'iframeXPath', 'shadowHost', 'shadowRoot',
  'inputValue', 'pressEnter', 'pressTab',
]);
const CANDIDATE_KEYS = new Set([
  'candidateId', 'tier', 'selectors', 'canonicalName', 'clientName', 'expectedTag',
  'iframeXPath', 'shadowHost', 'shadowRoot',
  'ocrName', 'previousPageKey', 'xpath', 'customXPath', 'cssSelector',
  'stableAttributes', 'expectedType', 'expectedRole',
]);

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const hasOnlyKeys = (value: Record<string, unknown>, keys: ReadonlySet<string>): boolean =>
  Object.keys(value).every(key => keys.has(key));

const requireBoundedText = (
  value: unknown,
  maximum: number,
  code: string,
): void => {
  if (value !== undefined && (typeof value !== 'string' || value.length > maximum)) {
    throw new Error(code);
  }
};

const validateSelectors = (selectors: readonly string[], code: string): void => {
  if (!Array.isArray(selectors) || selectors.length > 32
      || selectors.some(selector => typeof selector !== 'string'
        || selector.trim().length === 0 || selector.length > 2_048)) {
    throw new Error(code);
  }
};

export const validatePhysicalActionRequest = (
  request: PhysicalActionRequest,
): PhysicalActionRequest => {
  if (!Number.isSafeInteger(request.instructionId) || request.instructionId <= 0
      || !Number.isSafeInteger(request.sequence) || request.sequence <= 0) {
    throw new Error('ACTION_IDENTITY_INVALID');
  }
  if (!['CLICK', 'INPUT', 'OUTPUT'].includes(request.action)) {
    throw new Error('ACTION_TYPE_INVALID');
  }
  if (!PAGE_KEY_PATTERN.test(request.pageKey)) throw new Error('ACTION_PAGE_KEY_INVALID');
  validateSelectors(request.authoredSelectors, 'AUTHORED_SELECTORS_INVALID');
  if (!Array.isArray(request.registryCandidates) || request.registryCandidates.length > 100) {
    throw new Error('REGISTRY_CANDIDATES_INVALID');
  }
  for (const candidate of request.registryCandidates) {
    if (!Number.isSafeInteger(candidate.candidateId) || candidate.candidateId <= 0
        || !['LOCATOR', 'CANONICAL', 'ALIAS', 'REVIEW'].includes(candidate.tier)) {
      throw new Error('REGISTRY_CANDIDATE_INVALID');
    }
    validateSelectors(candidate.selectors, 'REGISTRY_SELECTORS_INVALID');
    requireBoundedText(candidate.canonicalName, 512, 'REGISTRY_NAME_INVALID');
    requireBoundedText(candidate.clientName, 512, 'REGISTRY_NAME_INVALID');
    requireBoundedText(candidate.iframeXPath, 2_048, 'REGISTRY_FRAME_INVALID');
    requireBoundedText(candidate.shadowHost, 2_048, 'REGISTRY_SHADOW_INVALID');
    requireBoundedText(candidate.shadowRoot, 2_048, 'REGISTRY_SHADOW_INVALID');
    requireBoundedText(candidate.ocrName, 512, 'REGISTRY_NAME_INVALID');
    requireBoundedText(candidate.previousPageKey, 71, 'REGISTRY_PAGE_INVALID');
    requireBoundedText(candidate.xpath, 2_048, 'REGISTRY_SELECTORS_INVALID');
    requireBoundedText(candidate.customXPath, 2_048, 'REGISTRY_SELECTORS_INVALID');
    requireBoundedText(candidate.cssSelector, 2_048, 'REGISTRY_SELECTORS_INVALID');
    requireBoundedText(candidate.expectedType, 80, 'REGISTRY_TYPE_INVALID');
    requireBoundedText(candidate.expectedRole, 80, 'REGISTRY_ROLE_INVALID');
    if (candidate.stableAttributes !== undefined) {
      if (!isRecord(candidate.stableAttributes)
          || Object.keys(candidate.stableAttributes).length > 16
          || Object.entries(candidate.stableAttributes).some(([key, value]) =>
            key.length === 0 || key.length > 80 || typeof value !== 'string' || value.length > 512)) {
        throw new Error('REGISTRY_ATTRIBUTES_INVALID');
      }
    }
    if (candidate.expectedTag !== undefined
        && typeof candidate.expectedTag !== 'string') {
      throw new Error('REGISTRY_TAG_INVALID');
    }
    if (candidate.expectedTag !== undefined
        && !SAFE_TAG_PATTERN.test(candidate.expectedTag.trim().toLowerCase())) {
      throw new Error('REGISTRY_TAG_INVALID');
    }
  }
  for (const value of [request.canonicalName, request.clientName]) {
    requireBoundedText(value, 512, 'ACTION_NAME_INVALID');
  }
  for (const value of [request.iframeXPath, request.shadowHost, request.shadowRoot]) {
    requireBoundedText(value, 2_048, 'ACTION_SCOPE_INVALID');
  }
  if (request.expectedTag !== undefined && typeof request.expectedTag !== 'string') {
    throw new Error('ACTION_TAG_INVALID');
  }
  if (request.expectedTag !== undefined
      && !SAFE_TAG_PATTERN.test(request.expectedTag.trim().toLowerCase())) {
    throw new Error('ACTION_TAG_INVALID');
  }
  if (request.action === 'INPUT') {
    if (typeof request.inputValue !== 'string' || request.inputValue.length > 1_048_576) {
      throw new Error('ACTION_INPUT_INVALID');
    }
  } else if (request.inputValue !== undefined) {
    throw new Error('ACTION_INPUT_UNEXPECTED');
  }
  if ((request.pressEnter !== undefined && typeof request.pressEnter !== 'boolean')
      || (request.pressTab !== undefined && typeof request.pressTab !== 'boolean')) {
    throw new Error('ACTION_KEYBOARD_OPTION_INVALID');
  }
  return request;
};

export const parsePhysicalActionRequest = (value: unknown): PhysicalActionRequest => {
  if (!isRecord(value) || !hasOnlyKeys(value, ACTION_KEYS)
      || !Array.isArray(value.registryCandidates)
      || value.registryCandidates.some(candidate => !isRecord(candidate)
        || !hasOnlyKeys(candidate, CANDIDATE_KEYS))) {
    throw new Error('ACTION_CONTRACT_INVALID');
  }
  return validatePhysicalActionRequest(value as unknown as PhysicalActionRequest);
};

export const physicalActionRequestFingerprint = (request: PhysicalActionRequest): string => {
  const canonical = JSON.stringify({
    instructionId: request.instructionId,
    sequence: request.sequence,
    action: request.action,
    pageKey: request.pageKey,
    authoredSelectors: request.authoredSelectors,
    registryCandidates: request.registryCandidates.map(candidate => ({
      candidateId: candidate.candidateId,
      tier: candidate.tier,
      selectors: candidate.selectors,
      canonicalName: candidate.canonicalName ?? '',
      clientName: candidate.clientName ?? '',
      expectedTag: candidate.expectedTag ?? '',
      iframeXPath: candidate.iframeXPath ?? '',
      shadowHost: candidate.shadowHost ?? '',
      shadowRoot: candidate.shadowRoot ?? '',
      ocrName: candidate.ocrName ?? '',
      previousPageKey: candidate.previousPageKey ?? '',
      xpath: candidate.xpath ?? '',
      customXPath: candidate.customXPath ?? '',
      cssSelector: candidate.cssSelector ?? '',
      stableAttributes: candidate.stableAttributes ?? {},
      expectedType: candidate.expectedType ?? '',
      expectedRole: candidate.expectedRole ?? '',
    })),
    canonicalName: request.canonicalName ?? '',
    clientName: request.clientName ?? '',
    expectedTag: request.expectedTag ?? '',
    iframeXPath: request.iframeXPath ?? '',
    shadowHost: request.shadowHost ?? '',
    shadowRoot: request.shadowRoot ?? '',
    inputValue: request.inputValue ?? '',
    pressEnter: request.pressEnter === true,
    pressTab: request.pressTab === true,
  });
  return createHash('sha256').update(canonical, 'utf8').digest('hex');
};

export const hasUnsupportedShadowScope = (value: string | undefined): boolean => {
  if (!value?.trim()) return false;
  return !['false', 'null', 'none', '0'].includes(value.trim().toLowerCase());
};
