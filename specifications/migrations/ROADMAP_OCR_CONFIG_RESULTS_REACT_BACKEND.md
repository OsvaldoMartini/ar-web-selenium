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
- [x] Extract pane-free OCR profile bootstrap/hydration service; mutation and test execution remain next.
- [ ] Build `OCRConfigPanel.tsx`, `OCRTestResultsPanel.tsx`, and separate SCSS modules.
- [ ] Render backend parameter metadata with typed controls and category sections.
- [ ] Add Save, Save As New, Delete confirmation, Clean Orphans, and Test Current Page.
- [ ] Show result table, approval controls, XPath, summary counts, and annotated image securely.
- [ ] Integrate accepted suggestions with existing `OCRPanel`/`GridItemScann` state.
- [ ] Add profile CRUD, validation, scope, test-result, approval, and image-path security tests.
- [ ] Route Bot Job and Pre-Scan OCR Config actions to React.
- [ ] Remove OCR JavaFX panes/scenes after parity and runtime verification.

## Acceptance

- No visible OCR configuration or results workflow opens JavaFX.
- Existing profiles round-trip without parameter loss.
- Test execution never blocks the React UI and reports structured progress/errors.
- Accepted OCR names reach the scanner grid by XPath.
