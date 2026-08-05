# OCR Configuration and Results React Migration Roadmap

Date: 2026-07-11

## Objective

Complete React parity for `AROcrConfigPane`, `AROcrTestResultsPane`, and their scenes while reusing the existing per-block `OCRPanel` review experience.

## Existing React Parity

- `OCRPanel` already reviews scanned DOM text versus OCR-resolved names with Agree/Defer.
- `GridItemScann` applies OCR suggestions by XPath and exposes the OCR review panel.
- Missing parity is profile administration, typed parameter editing, current-page test execution, approvals, annotated image/results, orphan cleanup, and profile deletion.

## Target Contract

- `ocrConfig.bootstrap/list/get`: scope, profiles, active profile, categorized typed parameter schema.
- `ocrConfig.save/delete/cleanOrphans`: revision/request ID, structured impact/results.
- `ocrTest.run`: ephemeral draft profile plus current page/diagnostic source.
- `ocrTest.result`: summary counts, result rows, approved XPath state, and secure annotated-image reference.
- `ocrTest.approve`: all/none/selected approvals and accepted OCR-name payload.

## Implementation

- [x] Inventory every `OcrConfigProfile`, parameter type/range/enum, scope rule, and persistence call.
- [x] Extract pane-free OCR profile, mutation, cleanup, and test-execution services.
- [x] Build `OCRConfigPanel.tsx`, `OCRTestResultsPanel.tsx`, and separate SCSS modules.
- [x] Render backend parameter metadata with typed controls and category sections.
- [x] Add Save, Save As New, Delete confirmation, Clean Orphans, and Test Current Page.
- [x] Show result table, approval controls, XPath, summary counts, and annotated image securely.
- [x] Integrate accepted suggestions with existing `OCRPanel`/`GridItemScann` state.
- [x] Render OCR Config and OCR Results exactly once as separate body-level, non-modal floating
      workspaces with the shared AR Web blue template, independent drag, stacking, and close state.
      This was the first separation step and is superseded by the native-window boundary below.
- [x] Detach OCR Config and OCR Results from the Bot Job/Page Scanner DOM into separate Chromium
      application windows. Each window owns a unique WebSocket session and may be moved through the
      operating-system title bar to another monitor while Bot Job Details remains open independently.
- [x] Keep launch context in Java rather than in the URL. The URL carries only the OCR page kind and
      an unguessable workspace session; Java binds that session to the originating `scannerGrid` or
      `preScannerGrid`, organization, job, URL scope, parameters, and a four-hour reload grace period.
- [x] Add `ocrWorkspace.open`, `ocrWorkspace.bootstrap`, and
      `ocrWorkspace.applySuggestions`. These commands use the registered WebSocket transport identity,
      never an envelope-supplied target, and accepted Results names can return only to the scanner that
      opened the workspace.
- [x] Use strict Chromium `--app` launching for OCR windows with no default-browser fallback, address
      bar, or tabs. OCR Config may open an additional independent Results window without nesting it.
- [x] Version the detached implementation as frontend commit `86256ab` on `VERSION-4.6` and
      backend implementation commit `4360f037` on `refactor/perform-actions-decomposition`.
- [ ] Add profile CRUD, validation, scope, test-result, approval, and image-path security tests.
- [x] Route Bot Job, scanned-element, and Pre-Scan OCR Config actions to React.
- [x] Remove OCR JavaFX panes/scenes and obsolete command routing after static parity verification.

## Acceptance

- No visible OCR configuration or results workflow opens JavaFX.
- Existing profiles round-trip without parameter loss.
- Test execution never blocks the React UI and reports structured progress/errors.
- Accepted OCR names reach the scanner grid by XPath.
- Neither OCR page is clipped by or mounted inside a Page Scanner block/container or Bot Job Details.
- Bot Job Details, OCR Config, and OCR Results can remain open as three independent operating-system
  windows and can be placed on three monitors.

## Verification (2026-07-18)

- 21 focused Jest tests passed across OCR panels and scanner entry controls.
- 4 React Playwright navigation tests passed, including three concurrent page/window controllers.
- 23 Java coordinator, launcher, and WebSocket lifecycle tests passed.
- Packaged `BotJobDetailsToolbarPlaywrightTest` passed against the clean-deployed production bundle.
- The optimized React build passed and all 45 deployed files matched by relative path and SHA-256.
- Physical movement across displays 1/2/3 is intentionally a manual smoke check because headless
  Playwright cannot validate the Windows compositor or monitor topology.
