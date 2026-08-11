package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.util.InputFlags;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed Playwright executor used only by production Test Run and Smoke Integration.
 *
 * <p>Every locator is resolved and validated before an action is attempted. Once a physical
 * click/input/read begins, failure is terminal: this executor never retries with force, JavaScript,
 * an ancestor, another candidate, or coordinates.
 */
public final class PlaywrightRuntimeHealingExecutor {

    private static final int MAX_CANDIDATES = 2_000;
    private static final int ACTION_TIMEOUT_MS = 5_000;
    private static final String LIVE_ACTION_SELECTOR =
            "input,textarea,select,button,a,label,summary,[role],[tabindex],"
                    + "[id],[name],[aria-label],[data-testid],[data-test-id],[test-id],[data-cy],[data-qa]";
    private static final String LIVE_OUTPUT_SELECTOR = LIVE_ACTION_SELECTOR
            + ",output,span,p,div,h1,h2,h3,h4,h5,h6,td,th,li,dt,dd,blockquote,pre,code";

    public enum Action {
        CLICK,
        INPUT,
        OUTPUT
    }

    public Result execute(
            Page page,
            InstructionLoad instruction,
            FieldData data,
            Action action,
            Preparation preparation) {
        if (page == null || page.isClosed() || instruction == null || action == null) {
            return failed("INVALID_REQUEST", "REQUEST", action, instruction, preparation, 0);
        }
        if (preparation == null) {
            return failed("OWNER_CONTEXT_MISSING", "AUTHORITY", action, instruction, null, 0);
        }
        switch (preparation.status()) {
            case INVALID_REQUEST, BOT_JOB_MISMATCH, OWNER_NOT_FOUND, OWNER_MISMATCH -> {
                return failed(
                        "OWNER_CONTEXT_REJECTED",
                        "AUTHORITY",
                        action,
                        instruction,
                        preparation,
                        0);
            }
            case READY, REGISTRY_UNAVAILABLE -> {
                // A registry outage may still use an authored locator; it never enables healing.
            }
        }

        final String livePageKey;
        try {
            livePageKey = ScannedPageIdentity.fromLiveUrl(page.url()).pageKey();
        } catch (RuntimeException invalidPage) {
            return failed(
                    "PAGE_CONTEXT_UNAVAILABLE",
                    "PAGE",
                    action,
                    instruction,
                    preparation,
                    0);
        }
        if (!hasText(preparation.pageKey()) || !preparation.pageKey().equals(livePageKey)) {
            return failed(
                    "PAGE_CONTEXT_CHANGED",
                    "PAGE",
                    action,
                    instruction,
                    preparation,
                    0);
        }
        if (hasShadowScope(instruction.getShadowHost())
                || hasShadowScope(instruction.getShadowRoot())) {
            return failed(
                    "SHADOW_SCOPE_UNSUPPORTED",
                    "AUTHORED",
                    action,
                    instruction,
                    preparation,
                    0);
        }
        boolean selectMetadataPresent = isCompositeSelect(instruction);
        boolean customSelectScope = false;
        if (selectMetadataPresent) {
            NativeSelect nativeSelect = nativeSelect(instruction);
            if (nativeSelect != null) {
                return executeNativeSelect(
                        page, instruction, data, action, preparation, nativeSelect);
            }
            if (hasNativeSelectReference(instruction)) {
                return failed(
                        "COMPOSITE_SELECT_UNSUPPORTED",
                        "SELECT",
                        action,
                        instruction,
                        preparation,
                        0);
            }
            // Custom controls may expose one already-open option. Resolve and act on that exact
            // option once; never open a trigger and then choose an option in the same instruction.
            customSelectScope = true;
        }

        Action effectiveAction = effectiveAction(instruction, action);

        Probe authored = probeSelectors(
                page,
                PlaywrightActionExecutor.selectorsFor(instruction),
                instruction.getIFrameXPath(),
                instruction.getShadowHost(),
                instruction.getShadowRoot(),
                physicalTag(instruction),
                effectiveAction,
                "AUTHORED");
        Probe deferredAmbiguity = authored.ambiguous() ? authored : null;
        if (!authored.ambiguous() && authored.target() != null) {
            return executeTarget(
                    page, instruction, data, action, effectiveAction, preparation, authored);
        }
        if (preparation.status() == RuntimeElementHealingService.Status.REGISTRY_UNAVAILABLE) {
            return failed(
                    "REGISTRY_UNAVAILABLE",
                    "REGISTRY",
                    action,
                    instruction,
                    preparation,
                    authored.liveCandidateCount());
        }
        if (hasUnsupportedShadowCandidate(preparation)) {
            return failed(
                    "SHADOW_SCOPE_UNSUPPORTED",
                    "REGISTRY",
                    action,
                    instruction,
                    preparation,
                    authored.liveCandidateCount());
        }

        int observed = authored.liveCandidateCount();
        if (preparation != null && preparation.ready()) {
            String scannedText = firstReferenceValue(
                    instruction, "scanned-text", "AttrData:scanned-text");
            for (Tier tier : List.of(
                    new Tier("REGISTRY_LOCATOR", preparation.locatorCandidates()),
                    new Tier("REGISTRY_CANONICAL", preparation.canonicalCandidates()),
                    new Tier("REGISTRY_ALIAS", preparation.aliasCandidates()))) {
                Probe registry = probeRegistryTier(
                        page,
                        tier,
                        effectiveAction,
                        physicalTag(instruction),
                        instruction.getIFrameXPath(),
                        scannedText);
                observed += registry.liveCandidateCount();
                if (registry.ambiguous()) {
                    if (deferredAmbiguity == null) deferredAmbiguity = registry;
                    continue;
                }
                if (registry.target() != null) {
                    Probe combined = registry.withLiveCandidateCount(observed);
                    return executeTarget(
                            page,
                            instruction,
                            data,
                            action,
                            effectiveAction,
                            preparation,
                            combined);
                }
            }
        }

        Probe canonical = probeLiveName(
                page,
                effectiveAction,
                instruction.getName(),
                "LIVE_CANONICAL",
                instruction.getIFrameXPath(),
                physicalTag(instruction));
        observed += canonical.liveCandidateCount();
        if (canonical.ambiguous()) {
            if (deferredAmbiguity == null) deferredAmbiguity = canonical;
        } else if (canonical.target() != null) {
            return executeTarget(
                    page,
                    instruction,
                    data,
                    action,
                    effectiveAction,
                    preparation,
                    canonical.withLiveCandidateCount(observed));
        }

        Probe alias = Probe.empty();
        if (preparation.ready() && preparation.aliasCandidates().size() > 1) {
            alias = Probe.ambiguous(
                    "LIVE_ALIAS", preparation.aliasCandidates().size());
        }
        if (preparation.ready() && preparation.aliasCandidates().size() == 1) {
            RegistryCandidate aliasOwner = preparation.aliasCandidates().get(0);
            String authoredTag = physicalTag(instruction);
            String candidateTag = physicalTag(aliasOwner);
            boolean tagCompatible = !hasText(authoredTag) || sameTag(authoredTag, candidateTag);
            boolean frameCompatible = !hasText(instruction.getIFrameXPath())
                    || sameBoundary(instruction.getIFrameXPath(), aliasOwner.iframeXpath());
            if (tagCompatible && frameCompatible) {
                alias = probeLiveName(
                        page,
                        effectiveAction,
                        instruction.getClientNamed(),
                        "LIVE_ALIAS",
                        firstText(instruction.getIFrameXPath(), aliasOwner.iframeXpath()),
                        firstText(authoredTag, candidateTag));
            }
        }
        observed += alias.liveCandidateCount();
        if (alias.ambiguous()) {
            if (deferredAmbiguity == null) deferredAmbiguity = alias;
        } else if (alias.target() != null) {
            return executeTarget(
                    page,
                    instruction,
                    data,
                    action,
                    effectiveAction,
                    preparation,
                    alias.withLiveCandidateCount(observed));
        }

        if (customSelectScope) {
            return failed(
                    "CUSTOM_SELECT_OPTION_NOT_VISIBLE",
                    "SELECT",
                    action,
                    instruction,
                    preparation,
                    observed);
        }

        Result terminal = terminalProbe(
                deferredAmbiguity, action, instruction, preparation);
        if (terminal != null) return terminal;

        CoordinateTarget coordinates = coordinateTarget(instruction, preparation);
        if (coordinates != null) {
            return executeCoordinates(page, instruction, data, action, preparation, coordinates);
        }
        return failed("TARGET_NOT_FOUND", "RESOLUTION", action, instruction, preparation, observed);
    }

    private static Probe probeRegistryTier(
            Page page,
            Tier tier,
            Action action,
            String authoredTag,
            String authoredIframe,
            String scannedText) {
        if (tier.candidates() == null || tier.candidates().isEmpty()) return Probe.empty();
        Map<String, ResolvedTarget> unique = new LinkedHashMap<>();
        int observed = 0;
        for (RegistryCandidate candidate : tier.candidates()) {
            if (hasText(authoredIframe)
                    && !sameBoundary(authoredIframe, candidate.iframeXpath())) {
                continue;
            }
            String candidateTag = physicalTag(candidate);
            // Current-page registry metadata is allowed to heal a stale authored tag. The
            // candidate still has to satisfy its observed tag/action/boundary contract, and the
            // complete tier must resolve to exactly one DOM element before execution.
            String expectedTag = firstText(candidateTag, authoredTag);
            Probe probe = probeSelectors(
                    page,
                    registrySelectors(candidate),
                    candidate.iframeXpath(),
                    candidate.shadowHost(),
                    candidate.shadowRoot(),
                    expectedTag,
                    action,
                    tier.stage());
            if (probe.ambiguous()
                    && tier.candidates().size() == 1
                    && hasText(scannedText)) {
                Probe narrowed = probeSelectors(
                        page,
                        registrySelectors(candidate),
                        candidate.iframeXpath(),
                        candidate.shadowHost(),
                        candidate.shadowRoot(),
                        expectedTag,
                        action,
                        tier.stage(),
                        normalizeName(scannedText));
                if (narrowed.target() != null || narrowed.ambiguous()) {
                    probe = narrowed;
                }
            }
            observed += probe.liveCandidateCount();
            if (probe.ambiguous()) {
                disposeTargets(unique.values());
                return Probe.ambiguous(tier.stage(), observed);
            }
            if (probe.target() == null) continue;
            String identity = elementIdentity(probe.target().element(), candidate.iframeXpath());
            ResolvedTarget existing = unique.putIfAbsent(identity, probe.target());
            if (existing != null) dispose(probe.target().element());
            if (unique.size() > 1) {
                disposeTargets(unique.values());
                return Probe.ambiguous(tier.stage(), observed);
            }
        }
        return unique.isEmpty()
                ? new Probe(null, false, tier.stage(), observed)
                : new Probe(unique.values().iterator().next(), false, tier.stage(), observed);
    }

    private static Probe probeSelectors(
            Page page,
            List<String> selectors,
            String iframeXpath,
            String shadowHost,
            String shadowRoot,
            String expectedTag,
            Action action,
            String stage) {
        return probeSelectors(
                page,
                selectors,
                iframeXpath,
                shadowHost,
                shadowRoot,
                expectedTag,
                action,
                stage,
                "");
    }

    private static Probe probeSelectors(
            Page page,
            List<String> selectors,
            String iframeXpath,
            String shadowHost,
            String shadowRoot,
            String expectedTag,
            Action action,
            String stage,
            String expectedName) {
        if (hasShadowScope(shadowHost) || hasShadowScope(shadowRoot)) {
            return new Probe(null, false, stage, 0);
        }
        int observed = 0;
        boolean ambiguous = false;
        for (String selector : selectors == null ? List.<String>of() : selectors) {
            if (!hasText(selector)) continue;
            try {
                Locator locator = scopedLocator(page, iframeXpath, selector);
                int count = locator.count();
                if (count > MAX_CANDIDATES) {
                    observed += count;
                    ambiguous = true;
                    continue;
                }
                List<ResolvedTarget> valid = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    ElementHandle element = null;
                    try {
                        element = locator.nth(index).elementHandle();
                        if (element == null) continue;
                        if (hasText(expectedName) && !matchesLiveName(element, expectedName)) {
                            dispose(element);
                            continue;
                        }
                        Validation validation = validate(
                                element,
                                expectedTag,
                                action,
                                hasText(iframeXpath),
                                allowsExplicitClickOverride(stage, action));
                        if (!validation.visible()) {
                            dispose(element);
                            continue;
                        }
                        observed++;
                        if (!validation.valid()) {
                            dispose(element);
                            continue;
                        }
                        valid.add(new ResolvedTarget(
                                element,
                                stage,
                                validation.frameValidated(),
                                validation.shadowValidated(),
                                validation.tagValidated(),
                                validation.actionValidated()));
                    } catch (RuntimeException unavailable) {
                        dispose(element);
                    }
                }
                if (valid.size() > 1) {
                    disposeTargets(valid);
                    ambiguous = true;
                    continue;
                }
                if (valid.size() == 1) {
                    // Selector order is authoritative. A unique strong locator wins immediately;
                    // a later broad CSS fallback must not convert it into a false ambiguity.
                    return new Probe(valid.get(0), false, stage, observed);
                }
            } catch (RuntimeException ignored) {
                // A broken selector is not authority to act. Continue resolving before any action.
            }
        }
        return ambiguous
                ? Probe.ambiguous(stage, observed)
                : new Probe(null, false, stage, observed);
    }

    private static Probe probeLiveName(
            Page page,
            Action action,
            String name,
            String stage,
            String iframeXpath,
            String expectedTag) {
        String expectedName = normalizeName(name);
        if (expectedName.isEmpty()) {
            return Probe.empty();
        }
        try {
            Locator candidates = scopedLocator(
                    page,
                    iframeXpath,
                    action == Action.OUTPUT ? LIVE_OUTPUT_SELECTOR : LIVE_ACTION_SELECTOR);
            int count = candidates.count();
            if (count > MAX_CANDIDATES) return Probe.ambiguous(stage, count);
            List<ResolvedTarget> valid = new ArrayList<>();
            int observed = 0;
            for (int index = 0; index < count; index++) {
                ElementHandle candidate = null;
                try {
                    candidate = candidates.nth(index).elementHandle();
                    if (candidate == null) continue;
                    if (!matchesLiveName(candidate, expectedName)) {
                        dispose(candidate);
                        continue;
                    }
                    observed++;
                    Validation validation = validate(
                            candidate,
                            expectedTag,
                            action,
                            hasText(iframeXpath),
                            false);
                    if (validation.valid()) {
                        valid.add(new ResolvedTarget(
                                candidate,
                                stage,
                                validation.frameValidated(),
                                validation.shadowValidated(),
                                validation.tagValidated(),
                                validation.actionValidated()));
                    } else {
                        dispose(candidate);
                    }
                } catch (RuntimeException unavailable) {
                    dispose(candidate);
                }
            }
            if (valid.size() > 1) {
                disposeTargets(valid);
                return Probe.ambiguous(stage, observed);
            }
            return valid.isEmpty()
                    ? new Probe(null, false, stage, observed)
                    : new Probe(valid.get(0), false, stage, observed);
        } catch (RuntimeException unavailable) {
            return Probe.empty();
        }
    }

    private static Result terminalProbe(
            Probe probe, Action action, InstructionLoad instruction, Preparation preparation) {
        if (probe != null && probe.ambiguous()) {
            return failed(
                    "AMBIGUOUS_TARGET",
                    probe.stage(),
                    action,
                    instruction,
                    preparation,
                    probe.liveCandidateCount());
        }
        return null;
    }

    private static Result executeTarget(
            Page page,
            InstructionLoad instruction,
            FieldData data,
            Action requestedAction,
            Action effectiveAction,
            Preparation preparation,
            Probe probe) {
        ResolvedTarget target = probe.target();
        try {
            InputFlags flags = InputFlags.of(instruction.getForceCoordinates());
            if (flags.hasScroll()) {
                try {
                    target.element().scrollIntoViewIfNeeded(
                            new ElementHandle.ScrollIntoViewIfNeededOptions()
                                    .setTimeout(ACTION_TIMEOUT_MS));
                } catch (RuntimeException scrollFailure) {
                    return failed(
                            "PREACTION_SCROLL_FAILED",
                            probe.stage(),
                            requestedAction,
                            instruction,
                            preparation,
                            probe.liveCandidateCount());
                }
            }
            if (!pageMatches(page, preparation)) {
                return failed(
                        "PAGE_CONTEXT_CHANGED",
                        "PAGE",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            final String input = data == null || data.getValue() == null ? "" : data.getValue();
            final String targetTag;
            try {
                targetTag = domTag(target.element());
            } catch (RuntimeException unavailable) {
                return failed(
                        "TARGET_VALIDATION_FAILED",
                        probe.stage(),
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            if (effectiveAction == Action.INPUT
                    && "select".equals(targetTag)
                    && optionValueCount(target.element(), input) != 1) {
                return failed(
                        "SELECT_OPTION_AMBIGUOUS",
                        probe.stage(),
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            if (!pageMatches(page, preparation)) {
                return failed(
                        "PAGE_CONTEXT_CHANGED",
                        "PAGE",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            try {
                String value = null;
                switch (effectiveAction) {
                    case CLICK -> target.element().click(
                            new ElementHandle.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
                    case INPUT -> {
                        if ("select".equals(targetTag)) {
                            target.element().selectOption(
                                    new SelectOption().setValue(input),
                                    new ElementHandle.SelectOptionOptions()
                                            .setTimeout(ACTION_TIMEOUT_MS));
                        } else {
                            target.element().fill(
                                    input,
                                    new ElementHandle.FillOptions().setTimeout(ACTION_TIMEOUT_MS));
                        }
                        pressPostInputKeys(page, flags);
                    }
                    case OUTPUT -> value = readValue(target.element());
                }
                return succeeded(
                        value,
                        probe.stage(),
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount(),
                        target);
            } catch (RuntimeException actionFailure) {
                return failedAfterAttempt(
                        "ACTION_FAILED",
                        probe.stage(),
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount(),
                        target);
            }
        } finally {
            dispose(target.element());
        }
    }

    private static Result executeCoordinates(
            Page page,
            InstructionLoad instruction,
            FieldData data,
            Action action,
            Preparation preparation,
            CoordinateTarget target) {
        if (hasText(target.iframeXpath())
                || hasShadowScope(target.shadowHost())
                || hasShadowScope(target.shadowRoot())) {
            return failed("COORDINATE_SCOPE_UNSAFE", "COORDINATES", action, instruction, preparation, 0);
        }
        Action effectiveAction = effectiveAction(instruction, action);
        Validation validation = validateCoordinates(page, target, effectiveAction);
        if (!validation.valid()) {
            return failed("COORDINATE_TARGET_INVALID", "COORDINATES", action, instruction, preparation, 1);
        }
        if (!pageMatches(page, preparation)) {
            return failed(
                    "PAGE_CONTEXT_CHANGED",
                    "PAGE",
                    action,
                    instruction,
                    preparation,
                    1);
        }
        ResolvedTarget diagnosticTarget = new ResolvedTarget(
                null,
                "COORDINATES",
                true,
                validation.shadowValidated(),
                validation.tagValidated(),
                validation.actionValidated());
        if (effectiveAction == Action.INPUT
                && "select".equals(normalizeExpectedTag(target.expectedTag()))) {
            return failed(
                    "COORDINATE_SELECT_UNSUPPORTED",
                    "COORDINATES",
                    action,
                    instruction,
                    preparation,
                    1);
        }
        try {
            String value = null;
            switch (effectiveAction) {
                case CLICK -> page.mouse().click(target.x(), target.y());
                case INPUT -> {
                    page.mouse().click(target.x(), target.y());
                    page.keyboard().press("Control+A");
                    page.keyboard().type(data == null || data.getValue() == null ? "" : data.getValue());
                    pressPostInputKeys(page, InputFlags.of(instruction.getForceCoordinates()));
                }
                case OUTPUT -> value = coordinateValue(page, target);
            }
            return succeeded(value, "COORDINATES", action, instruction, preparation, 1, diagnosticTarget);
        } catch (RuntimeException actionFailure) {
            return failedAfterAttempt(
                    "ACTION_FAILED",
                    "COORDINATES",
                    action,
                    instruction,
                    preparation,
                    1,
                    diagnosticTarget);
        }
    }

    private static Result executeNativeSelect(
            Page page,
            InstructionLoad instruction,
            FieldData data,
            Action requestedAction,
            Preparation preparation,
            NativeSelect nativeSelect) {
        Probe probe = probeSelectors(
                page,
                List.of(nativeSelect.selector()),
                instruction.getIFrameXPath(),
                instruction.getShadowHost(),
                instruction.getShadowRoot(),
                "select",
                requestedAction == Action.OUTPUT ? Action.OUTPUT : Action.INPUT,
                "NATIVE_SELECT");
        Result terminal = terminalProbe(probe, requestedAction, instruction, preparation);
        if (terminal != null) return terminal;
        if (probe.target() == null) {
            return failed(
                    "NATIVE_SELECT_NOT_FOUND",
                    "NATIVE_SELECT",
                    requestedAction,
                    instruction,
                    preparation,
                    probe.liveCandidateCount());
        }
        ResolvedTarget target = probe.target();
        try {
            if (!pageMatches(page, preparation)) {
                return failed(
                        "PAGE_CONTEXT_CHANGED",
                        "PAGE",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            if (requestedAction == Action.INPUT && (data == null || data.getValue() == null)) {
                return failed(
                        "SELECT_INPUT_VALUE_MISSING",
                        "NATIVE_SELECT",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            String desiredValue = requestedAction == Action.INPUT
                    ? data.getValue()
                    : nativeSelect.value();
            if (requestedAction == Action.OUTPUT) {
                if (!pageMatches(page, preparation)) {
                    return failed(
                            "PAGE_CONTEXT_CHANGED",
                            "PAGE",
                            requestedAction,
                            instruction,
                            preparation,
                            probe.liveCandidateCount());
                }
                OptionRead read = readUniqueOption(target.element(), desiredValue);
                if (read.count() != 1) {
                    return failed(
                            "SELECT_OPTION_AMBIGUOUS",
                            "NATIVE_SELECT",
                            requestedAction,
                            instruction,
                            preparation,
                            probe.liveCandidateCount());
                }
                return succeeded(
                        read.text(),
                        "NATIVE_SELECT",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount(),
                        target);
            }
            if (optionValueCount(target.element(), desiredValue) != 1) {
                return failed(
                        "SELECT_OPTION_AMBIGUOUS",
                        "NATIVE_SELECT",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            if (!pageMatches(page, preparation)) {
                return failed(
                        "PAGE_CONTEXT_CHANGED",
                        "PAGE",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount());
            }
            try {
                target.element().selectOption(
                        new SelectOption().setValue(desiredValue),
                        new ElementHandle.SelectOptionOptions().setTimeout(ACTION_TIMEOUT_MS));
                return succeeded(
                        null,
                        "NATIVE_SELECT",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount(),
                        target);
            } catch (RuntimeException selectionFailure) {
                return failedAfterAttempt(
                        "ACTION_FAILED",
                        "NATIVE_SELECT",
                        requestedAction,
                        instruction,
                        preparation,
                        probe.liveCandidateCount(),
                        target);
            }
        } finally {
            dispose(target.element());
        }
    }

    private static Validation validate(
            ElementHandle element,
            String expectedTag,
            Action action,
            boolean requireSameOriginFrame,
            boolean allowExplicitClickOverride) {
        try {
            Object raw = element.evaluate(
                    """
                    (el, args) => {
                      const action = args[0];
                      const expected = args[1];
                      const requireSameOriginFrame = args[2];
                      const allowExplicitClickOverride = args[3];
                      const tag = (el.tagName || '').toLowerCase();
                      const type = String(el.type || el.getAttribute('type') || '').toLowerCase();
                      const role = (el.getAttribute('role') || '').toLowerCase();
                      const style = window.getComputedStyle(el);
                      const rect = el.getBoundingClientRect();
                      const visible = style.visibility !== 'hidden' && style.display !== 'none'
                        && Number(style.opacity || 1) !== 0 && rect.width > 0 && rect.height > 0;
                      const disabled = Boolean(el.disabled) || el.getAttribute('aria-disabled') === 'true';
                      const readonly = Boolean(el.readOnly) || el.getAttribute('aria-readonly') === 'true';
                      const tagOk = !expected || tag === expected;
                      let actionOk = false;
                      if (action === 'OUTPUT') {
                        actionOk = visible;
                      } else if (action === 'INPUT') {
                        const inputOk = tag === 'textarea' || tag === 'select' || el.isContentEditable
                          || role === 'textbox'
                          || (tag === 'input' && !['button','submit','reset','file','checkbox','radio','hidden','image'].includes(type));
                        actionOk = visible && !disabled && !readonly && inputOk;
                      } else {
                        const clickTag = ['a','button','label','summary','select','option'].includes(tag)
                          || (tag === 'input' && type !== 'hidden');
                        const clickRole = ['button','link','menuitem','tab','checkbox','radio','option','switch'].includes(role);
                        actionOk = visible && !disabled && (allowExplicitClickOverride
                          || clickTag || clickRole || el.hasAttribute('onclick') || el.tabIndex >= 0);
                      }
                      let frameOk = true;
                      if (requireSameOriginFrame) {
                        try { frameOk = window.top.location.origin === window.location.origin; }
                        catch (_) { frameOk = false; }
                      }
                      const root = el.getRootNode ? el.getRootNode() : null;
                      const shadowOk = !(typeof ShadowRoot !== 'undefined' && root instanceof ShadowRoot);
                      return { tag, visible, tagOk, actionOk, frameOk, shadowOk };
                    }
                    """,
                    List.of(
                            action.name(),
                            normalizeExpectedTag(expectedTag),
                            requireSameOriginFrame,
                            allowExplicitClickOverride));
            if (!(raw instanceof Map<?, ?> values)) return Validation.invalid();
            boolean visible = booleanValue(values.get("visible"));
            boolean tagOk = booleanValue(values.get("tagOk"));
            boolean actionOk = booleanValue(values.get("actionOk"));
            boolean frameOk = booleanValue(values.get("frameOk"));
            boolean shadowOk = booleanValue(values.get("shadowOk"));
            return new Validation(
                    visible && tagOk && actionOk && frameOk && shadowOk,
                    visible,
                    tagOk,
                    actionOk,
                    frameOk,
                    shadowOk);
        } catch (RuntimeException unavailable) {
            return Validation.invalid();
        }
    }

    private static Validation validateCoordinates(
            Page page, CoordinateTarget target, Action action) {
        try {
            Object raw = page.evaluate(
                    """
                    args => {
                      const [x, y, action, expected] = args;
                      const el = document.elementFromPoint(x, y);
                      if (!el) return { visible:false, tagOk:false, actionOk:false, shadowOk:false };
                      const tag = (el.tagName || '').toLowerCase();
                      const type = String(el.type || el.getAttribute('type') || '').toLowerCase();
                      const role = (el.getAttribute('role') || '').toLowerCase();
                      const style = window.getComputedStyle(el);
                      const rect = el.getBoundingClientRect();
                      const visible = style.visibility !== 'hidden' && style.display !== 'none'
                        && Number(style.opacity || 1) !== 0 && rect.width > 0 && rect.height > 0;
                      const disabled = Boolean(el.disabled) || el.getAttribute('aria-disabled') === 'true';
                      const readonly = Boolean(el.readOnly) || el.getAttribute('aria-readonly') === 'true';
                      const tagOk = !expected || tag === expected;
                      let actionOk = false;
                      if (action === 'OUTPUT') actionOk = visible;
                      else if (action === 'INPUT') {
                        actionOk = visible && !disabled && !readonly && (tag === 'textarea' || tag === 'select'
                          || el.isContentEditable || role === 'textbox'
                          || (tag === 'input' && !['button','submit','reset','file','checkbox','radio','hidden','image'].includes(type)));
                      } else {
                        const clickTag = ['a','button','label','summary','select','option'].includes(tag)
                          || (tag === 'input' && type !== 'hidden');
                        const clickRole = ['button','link','menuitem','tab','checkbox','radio','option','switch'].includes(role);
                        actionOk = visible && !disabled && (clickTag || clickRole || el.hasAttribute('onclick') || el.tabIndex >= 0);
                      }
                      const root = el.getRootNode ? el.getRootNode() : null;
                      const shadowOk = !(typeof ShadowRoot !== 'undefined' && root instanceof ShadowRoot)
                        && !el.shadowRoot && !el.assignedSlot;
                      return { visible, tagOk, actionOk, shadowOk };
                    }
                    """,
                    List.of(target.x(), target.y(), action.name(), normalizeExpectedTag(target.expectedTag())));
            if (!(raw instanceof Map<?, ?> values)) return Validation.invalid();
            boolean visible = booleanValue(values.get("visible"));
            boolean tagOk = booleanValue(values.get("tagOk"));
            boolean actionOk = booleanValue(values.get("actionOk"));
            boolean shadowOk = booleanValue(values.get("shadowOk"));
            return new Validation(
                    visible && tagOk && actionOk && shadowOk,
                    visible,
                    tagOk,
                    actionOk,
                    true,
                    shadowOk);
        } catch (RuntimeException unavailable) {
            return Validation.invalid();
        }
    }

    private static boolean matchesLiveName(ElementHandle candidate, String expected) {
        try {
            Object value = candidate.evaluate(
                    """
                    (el, expected) => {
                      const normalize = value => String(value || '').trim().replace(/\s+/g, ' ').toLowerCase();
                      const values = [
                        el.id,
                        el.getAttribute('name'),
                        el.getAttribute('aria-label'),
                        el.getAttribute('data-testid'),
                        el.getAttribute('data-test-id'),
                        el.getAttribute('test-id'),
                        el.getAttribute('data-cy'),
                        el.getAttribute('data-qa'),
                        el.textContent
                      ];
                      return values.some(value => normalize(value) === expected);
                    }
                    """,
                    expected);
            return Boolean.TRUE.equals(value);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static String readValue(ElementHandle element) {
        Object raw = element.evaluate(
                """
                el => {
                  if ('value' in el && el.value !== undefined && el.value !== null) {
                    return String(el.value);
                  }
                  const text = el.innerText;
                  return text === undefined || text === null ? String(el.textContent || '') : String(text);
                }
                """);
        return raw == null ? "" : String.valueOf(raw);
    }

    private static int optionValueCount(ElementHandle select, String value) {
        try {
            Object raw = select.evaluate(
                    "(el, expected) => Array.from(el.options || [])"
                            + ".filter(option => String(option.value) === String(expected)).length",
                    Objects.toString(value, ""));
            return raw instanceof Number number ? number.intValue() : -1;
        } catch (RuntimeException unavailable) {
            return -1;
        }
    }

    private static OptionRead readUniqueOption(ElementHandle select, String value) {
        try {
            Object raw = select.evaluate(
                    "(el, expected) => {"
                            + " const matches = Array.from(el.options || [])"
                            + ".filter(option => String(option.value) === String(expected));"
                            + " return { count: matches.length, text: matches.length === 1"
                            + " ? String(matches[0].text ?? matches[0].textContent ?? matches[0].label ?? '')"
                            + " : '' };"
                            + "}",
                    Objects.toString(value, ""));
            if (!(raw instanceof Map<?, ?> values)) return OptionRead.unavailable();
            Object countValue = values.get("count");
            int count = countValue instanceof Number number ? number.intValue() : -1;
            return new OptionRead(count, Objects.toString(values.get("text"), ""));
        } catch (RuntimeException unavailable) {
            return OptionRead.unavailable();
        }
    }

    private static String coordinateValue(Page page, CoordinateTarget target) {
        Object raw = page.evaluate(
                """
                args => {
                  const el = document.elementFromPoint(args[0], args[1]);
                  if (!el) return null;
                  if ('value' in el && el.value !== undefined && el.value !== null) return String(el.value);
                  const text = el.innerText;
                  return text === undefined || text === null ? String(el.textContent || '') : String(text);
                }
                """,
                List.of(target.x(), target.y()));
        if (raw == null) throw new IllegalStateException("Coordinate output target disappeared");
        return String.valueOf(raw);
    }

    private static Locator scopedLocator(Page page, String iframeXpath, String selector) {
        if (hasText(iframeXpath)) {
            FrameLocator frame = page.frameLocator("xpath=" + iframeXpath.trim());
            return frame.locator(selector);
        }
        return page.locator(selector);
    }

    private static List<String> registrySelectors(RegistryCandidate candidate) {
        Set<String> selectors = new LinkedHashSet<>();
        addXpath(selectors, candidate.customXPath());
        addXpath(selectors, candidate.xpath());
        if (hasText(candidate.cssSelector())) selectors.add(candidate.cssSelector().trim());
        addAttributeSelector(selectors, "id", candidate.attribId());
        addAttributeSelector(selectors, "name", candidate.attribName());
        for (String name : List.of(
                "data-testid", "data-test-id", "test-id", "data-cy", "data-qa", "aria-label")) {
            addAttributeSelector(selectors, name, candidate.attributes().get(name));
        }
        return List.copyOf(selectors);
    }

    private static void addXpath(Set<String> selectors, String xpath) {
        if (!hasText(xpath)) return;
        String value = xpath.trim();
        selectors.add(value.startsWith("xpath=") ? value : "xpath=" + value);
    }

    private static void addAttributeSelector(Set<String> selectors, String name, String value) {
        if (!hasText(value)) return;
        selectors.add("[" + name + "=\"" + cssString(value.trim()) + "\"]");
    }

    private static String cssString(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\d ")
                .replace("\n", "\\a ");
    }

    private static String elementIdentity(ElementHandle element, String iframeXpath) {
        try {
            Object raw = element.evaluate(
                    """
                    el => {
                      const parts = [];
                      let current = el;
                      while (current && current.nodeType === 1) {
                        let index = 1;
                        let sibling = current.previousElementSibling;
                        while (sibling) { if (sibling.tagName === current.tagName) index++; sibling = sibling.previousElementSibling; }
                        parts.unshift((current.tagName || '').toLowerCase() + ':' + index);
                        current = current.parentElement;
                      }
                      return parts.join('/');
                    }
                    """);
            String path = raw == null ? "" : String.valueOf(raw);
            return Objects.toString(iframeXpath, "") + '\u0000' + path;
        } catch (RuntimeException unavailable) {
            return Objects.toString(iframeXpath, "")
                    + "\u0000unavailable-"
                    + System.identityHashCode(element);
        }
    }

    private static CoordinateTarget coordinateTarget(
            InstructionLoad instruction, Preparation preparation) {
        double[] authored = parseCoordinates(instruction.getCoordinates());
        if (authored != null) {
            return new CoordinateTarget(
                    authored[0],
                    authored[1],
                    physicalTag(instruction),
                    instruction.getIFrameXPath(),
                    instruction.getShadowHost(),
                    instruction.getShadowRoot());
        }
        if (preparation == null || !preparation.ready()) return null;
        for (List<RegistryCandidate> tier : List.of(
                preparation.locatorCandidates(),
                preparation.canonicalCandidates(),
                preparation.aliasCandidates())) {
            if (tier == null || tier.isEmpty()) continue;
            if (tier.size() != 1) return null;
            RegistryCandidate candidate = tier.get(0);
            double[] coordinates = parseCoordinates(candidate.coordinates());
            if (coordinates == null) return null;
            return new CoordinateTarget(
                    coordinates[0],
                    coordinates[1],
                    physicalTag(candidate),
                    candidate.iframeXpath(),
                    candidate.shadowHost(),
                    candidate.shadowRoot());
        }
        return null;
    }

    private static double[] parseCoordinates(String raw) {
        if (!hasText(raw)) return null;
        String[] parts = raw.trim().split("[,;\\s]+");
        if (parts.length < 2) return null;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0) return null;
            return new double[] {x, y};
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static void pressPostInputKeys(Page page, InputFlags flags) {
        if (flags.hasEnter()) page.keyboard().press("Enter");
        if (flags.hasTab()) page.keyboard().press("Tab");
    }

    private static boolean isCompositeSelect(InstructionLoad instruction) {
        if (instruction.getReferenceLoadDTOList() == null) return false;
        for (ReferenceLoadDTO reference : instruction.getReferenceLoadDTOList()) {
            String type = reference == null || reference.getReferenceType() == null
                    ? ""
                    : reference.getReferenceType().trim().toLowerCase(Locale.ROOT);
            if (type.contains("select.option")
                    || type.contains("select.trigger")
                    || type.contains("select.panel")
                    || type.contains("option-value")
                    || type.contains("option-text")) {
                return true;
            }
        }
        return false;
    }

    private static NativeSelect nativeSelect(InstructionLoad instruction) {
        if (instruction == null || instruction.getReferenceLoadDTOList() == null) return null;
        String xpath = "";
        String value = "";
        for (ReferenceLoadDTO reference : instruction.getReferenceLoadDTOList()) {
            if (reference == null
                    || reference.getReferenceType() == null
                    || reference.getValue() == null
                    || reference.getValue().isBlank()) {
                continue;
            }
            String type = reference.getReferenceType().trim().toLowerCase(Locale.ROOT);
            if (type.equals("select.native.xpath")
                    || type.equals("select-xpath")
                    || type.equals("attrdata:select-xpath")) {
                xpath = reference.getValue().trim();
            } else if (type.equals("select.option.value")
                    || type.equals("option-value")
                    || type.equals("attrdata:option-value")) {
                value = reference.getValue();
            }
        }
        if (!hasText(xpath) || !hasText(value)) return null;
        return new NativeSelect(xpath.startsWith("xpath=") ? xpath : "xpath=" + xpath, value);
    }

    private static boolean hasNativeSelectReference(InstructionLoad instruction) {
        return hasText(firstReferenceValue(
                instruction,
                "select.native.xpath",
                "select-xpath",
                "AttrData:select-xpath"));
    }

    private static Action effectiveAction(InstructionLoad instruction, Action requestedAction) {
        return requestedAction == Action.INPUT && isClickOnlyInput(instruction)
                ? Action.CLICK
                : requestedAction;
    }

    private static boolean allowsExplicitClickOverride(String stage, Action action) {
        return action == Action.CLICK
                && ("AUTHORED".equals(stage) || "REGISTRY_LOCATOR".equals(stage));
    }

    private static boolean isClickOnlyInput(InstructionLoad instruction) {
        String kind = firstReferenceValue(
                        instruction, "control.kind", "AttrData:control.kind", "attributeType")
                .toLowerCase(Locale.ROOT);
        String type = firstReferenceValue(instruction, "type", "AttrData:type")
                .toLowerCase(Locale.ROOT);
        String role = firstReferenceValue(
                        instruction,
                        "control.role",
                        "AttrData:control.role",
                        "role",
                        "AttrData:role")
                .toLowerCase(Locale.ROOT);
        if (kind.contains("radio")
                || kind.contains("checkbox")
                || kind.contains("switch")
                || kind.contains("button")
                || kind.contains("option")
                || kind.contains("menu")
                || kind.contains("tree")
                || kind.contains("tab")
                || kind.contains("calendar")
                || kind.contains("upload")) {
            return true;
        }
        if (List.of("radio", "checkbox", "button", "submit", "reset", "file", "hidden")
                .contains(type)) {
            return true;
        }
        return List.of("radio", "checkbox", "button", "switch", "option", "menuitem", "tab", "treeitem")
                .contains(role);
    }

    private static String physicalTag(InstructionLoad instruction) {
        String originalTag = firstReferenceValue(
                instruction, "dom.originalTag", "AttrData:original-tag");
        return firstText(originalTag, instruction == null ? "" : instruction.getTagName());
    }

    private static String physicalTag(RegistryCandidate candidate) {
        if (candidate == null) return "";
        return firstText(candidate.attributes().get("original-tag"), candidate.tagName());
    }

    private static String firstReferenceValue(
            InstructionLoad instruction, String... referenceTypes) {
        if (instruction == null
                || instruction.getReferenceLoadDTOList() == null
                || referenceTypes == null) {
            return "";
        }
        for (String expectedType : referenceTypes) {
            if (!hasText(expectedType)) continue;
            for (ReferenceLoadDTO reference : instruction.getReferenceLoadDTOList()) {
                if (reference == null
                        || reference.getReferenceType() == null
                        || reference.getValue() == null
                        || reference.getValue().isBlank()) {
                    continue;
                }
                if (expectedType.equalsIgnoreCase(reference.getReferenceType().trim())) {
                    return reference.getValue().trim();
                }
            }
        }
        return "";
    }

    private static boolean hasUnsupportedShadowCandidate(Preparation preparation) {
        if (preparation == null || !preparation.ready()) return false;
        for (List<RegistryCandidate> tier : List.of(
                preparation.locatorCandidates(),
                preparation.canonicalCandidates(),
                preparation.aliasCandidates())) {
            for (RegistryCandidate candidate : tier) {
                if (hasShadowScope(candidate.shadowHost())
                        || hasShadowScope(candidate.shadowRoot())) return true;
            }
        }
        return false;
    }

    private static String domTag(ElementHandle element) {
        Object raw = element.evaluate("el => (el.tagName || '').toLowerCase()");
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String normalizeExpectedTag(String value) {
        if (!hasText(value)) return "";
        String tag = value.trim().toLowerCase(Locale.ROOT);
        return tag.matches("[a-z][a-z0-9-]{0,31}") ? tag : "";
    }

    private static String normalizeName(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String firstText(String preferred, String fallback) {
        return hasText(preferred) ? preferred : Objects.toString(fallback, "");
    }

    private static boolean sameBoundary(String authored, String candidate) {
        return hasText(authored)
                && hasText(candidate)
                && authored.trim().equals(candidate.trim());
    }

    private static boolean sameTag(String authored, String candidate) {
        String expected = normalizeExpectedTag(authored);
        String actual = normalizeExpectedTag(candidate);
        return !expected.isEmpty() && expected.equals(actual);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void disposeTargets(Iterable<ResolvedTarget> targets) {
        if (targets == null) return;
        for (ResolvedTarget target : targets) {
            if (target != null) dispose(target.element());
        }
    }

    private static void dispose(ElementHandle element) {
        if (element == null) return;
        try {
            element.dispose();
        } catch (RuntimeException ignored) {
            // Handles are best-effort cleanup only; resolution/action results stay authoritative.
        }
    }

    private static boolean hasShadowScope(String value) {
        if (!hasText(value)) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !List.of("false", "null", "none", "0").contains(normalized);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static boolean pageMatches(Page page, Preparation preparation) {
        if (page == null || preparation == null || !hasText(preparation.pageKey())) return false;
        try {
            return preparation.pageKey()
                    .equals(ScannedPageIdentity.fromLiveUrl(page.url()).pageKey());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static Result succeeded(
            String value,
            String stage,
            Action action,
            InstructionLoad instruction,
            Preparation preparation,
            int liveCandidateCount,
            ResolvedTarget target) {
        String output = action == Action.OUTPUT ? Objects.toString(value, "") : null;
        return new Result(
                true,
                true,
                output,
                diagnostic(
                        "COMPLETED",
                        stage,
                        action,
                        instruction,
                        preparation,
                        liveCandidateCount,
                        target == null || target.frameValidated(),
                        target == null || target.shadowValidated(),
                        target == null || target.tagValidated(),
                        target == null || target.actionValidated(),
                        1));
    }

    private static Result failed(
            String code,
            String stage,
            Action action,
            InstructionLoad instruction,
            Preparation preparation,
            int liveCandidateCount) {
        return new Result(
                false,
                false,
                null,
                diagnostic(
                        code,
                        stage,
                        action,
                        instruction,
                        preparation,
                        liveCandidateCount,
                        false,
                        false,
                        false,
                        false,
                        0));
    }

    private static Result failedAfterAttempt(
            String code,
            String stage,
            Action action,
            InstructionLoad instruction,
            Preparation preparation,
            int liveCandidateCount,
            ResolvedTarget target) {
        return new Result(
                false,
                true,
                null,
                diagnostic(
                        code,
                        stage,
                        action,
                        instruction,
                        preparation,
                        liveCandidateCount,
                        target != null && target.frameValidated(),
                        target != null && target.shadowValidated(),
                        target != null && target.tagValidated(),
                        target != null && target.actionValidated(),
                        1));
    }

    private static Diagnostic diagnostic(
            String code,
            String stage,
            Action action,
            InstructionLoad instruction,
            Preparation preparation,
            int liveCandidateCount,
            boolean frameValidated,
            boolean shadowValidated,
            boolean tagValidated,
            boolean actionValidated,
            int physicalAttempts) {
        return new Diagnostic(
                code,
                stage,
                action == null ? "UNKNOWN" : action.name(),
                instruction == null ? null : instruction.getId(),
                preparation == null ? 0 : preparation.registryCandidateCount(),
                Math.max(0, liveCandidateCount),
                frameValidated,
                shadowValidated,
                tagValidated,
                actionValidated,
                physicalAttempts);
    }

    /** The value is present only for OUTPUT and is deliberately excluded from diagnostics. */
    public record Result(boolean succeeded, boolean found, String value, Diagnostic diagnostic) {
        public Result {
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (!succeeded && !found) value = null;
            if (succeeded && "OUTPUT".equals(diagnostic.action()) && value == null) value = "";
            if (!"OUTPUT".equals(diagnostic.action())) value = null;
        }
    }

    /**
     * Safe operational facts only; never include page URL, locator, banking text, or input data.
     * {@code physicalAttempts} counts one resolved target operation, including that input's own
     * configured Enter/Tab completion, and never a retry or fallback after the operation begins.
     */
    public record Diagnostic(
            String code,
            String stage,
            String action,
            Integer instructionId,
            int registryCandidateCount,
            int liveCandidateCount,
            boolean frameValidated,
            boolean shadowValidated,
            boolean tagValidated,
            boolean actionValidated,
            int physicalAttempts) {
        public Diagnostic {
            code = hasText(code) ? code : "UNKNOWN";
            stage = hasText(stage) ? stage : "UNKNOWN";
            action = hasText(action) ? action : "UNKNOWN";
            registryCandidateCount = Math.max(0, registryCandidateCount);
            liveCandidateCount = Math.max(0, liveCandidateCount);
            if (physicalAttempts < 0 || physicalAttempts > 1) {
                throw new IllegalArgumentException("physicalAttempts must be zero or one");
            }
        }
    }

    private record Tier(String stage, List<RegistryCandidate> candidates) {}

    private record Probe(
            ResolvedTarget target, boolean ambiguous, String stage, int liveCandidateCount) {
        private static Probe empty() {
            return new Probe(null, false, "RESOLUTION", 0);
        }

        private static Probe ambiguous(String stage, int count) {
            return new Probe(null, true, stage, Math.max(0, count));
        }

        private Probe withLiveCandidateCount(int count) {
            return new Probe(target, ambiguous, stage, count);
        }
    }

    private record ResolvedTarget(
            ElementHandle element,
            String stage,
            boolean frameValidated,
            boolean shadowValidated,
            boolean tagValidated,
            boolean actionValidated) {}

    private record CoordinateTarget(
            double x,
            double y,
            String expectedTag,
            String iframeXpath,
            String shadowHost,
            String shadowRoot) {}

    private record NativeSelect(String selector, String value) {}

    private record OptionRead(int count, String text) {
        private OptionRead {
            text = Objects.toString(text, "");
        }

        private static OptionRead unavailable() {
            return new OptionRead(-1, "");
        }
    }

    private record Validation(
            boolean valid,
            boolean visible,
            boolean tagValidated,
            boolean actionValidated,
            boolean frameValidated,
            boolean shadowValidated) {
        private static Validation invalid() {
            return new Validation(false, false, false, false, false, false);
        }
    }
}
