# Excel File Workflow React Migration Roadmap

Date: 2026-07-11
Last updated: 2026-07-20

## Objective

Replace `ARExcelFilePane` and `ARExcelFileScene` with a React floating panel opened from the existing Excel action in both `GridItem` and `GridItemComp`. Java retains filesystem validation and block persistence; React owns fields, validation feedback, and navigation.

## Current Contract

- React sends `BLOCK_EXCEL_FILE` with block/session context.
- `SimpleWebSocketServer` opens `ARExcelFileScene`.
- JavaFX edits export directory, filename, file type, CSV delimiter, and CSV columns.
- Save encodes `exportFile` and calls `updateBlockExportFile` for `block` or `component_block`, refreshes memory, publishes `updateInstructions` or `componentsUpdate`, then opens a Java dialog.

## Target Contract

- `excelExport.bootstrap`: block identity, current parsed settings, supported file types/delimiters/columns, capabilities.
- `excelExport.save`: revision/request ID, block/session ownership, directory, filename, file type, delimiter, selected columns.
- `excelExport.clear`: confirmation-aware removal of the block export setting.
- Responses contain `ok`, `error`, `requestId`, normalized `exportFile`, and authoritative refreshed instructions.

## Implementation

- [x] Extract pane-free `ExcelExportService` with structured parsing/encoding.
- [x] Validate block ownership, path confinement, filename, extension, and delimiter before persistence.
- [x] Persist and refresh only after successful validation.
- [x] Return structured errors and remove Java dialog ownership from the active route.
- [x] Build `ExcelExportPanel.tsx` and `ExcelExportPanel.module.scss` (`7e0c72f`).
- [x] Open the floating panel from both grids without JavaFX (`7e0c72f`).
- [x] Preserve bot-job/component ownership and authoritative refresh contracts.
- [x] Add parser, validation, and React panel payload tests.
- [x] Remove `ARExcelFilePane`, `ARExcelFileScene`, and server references.

## Acceptance

- Excel configuration never opens JavaFX.
- Existing encoded export settings round-trip without data loss.
- Invalid paths/files never mutate a block.
- Save/Clear update the correct grid in real time.

## 2026-07-20 Excel toolbar and execution-export delivery

- [x] Replace the Generate browser confirmation with the shared React `QuestionsCard`.
- [x] Generate and atomically replace only the selected Bot Job workbook, then open it with the operating system's associated application.
- [x] Make Excel open only the authoritative workbook for the selected Bot Job.
- [x] Let Report select and open one supported file inside the configured report directory; reject traversal, symlink escape, and executable files.
- [x] Add `excelExport.chooseDirectory` with request/session/job/block correlation and keep the React panel open after selection or cancellation.
- [x] Let the Excel Export panel select a destination folder, filename, `.xlsx`/`.csv` type, and delimiter.
- [x] Persist the canonical execution-export target and use that exact target during Bot Job execution.
- [x] Parse Windows drive letters without splitting their colon, prevent stale target inheritance between blocks, and publish CSV/XLSX output atomically.
- [x] Close execution-export workbooks after each write so repeated Windows writes do not lock their own destination.
- [x] Deploy the optimized React build into `src/main/resources/build` and verify all 45 files by SHA-256 (zero differences).
- [x] Verify focused React suites (7/7 panel/confirmation tests and 13/13 controller tests), focused Java regression tests (47/47), and the complete Bot Job Details toolbar Playwright test (1/1).
