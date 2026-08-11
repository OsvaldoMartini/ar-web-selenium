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

## 2026-08-11 Instruction-owned ExcelWrite supersession

This checkpoint supersedes the Block-owned execution model above for Bot Job ExcelWrite commands. The historical `excelExport.*` service and Block column remain compatibility/rollback seams, but the active Bot Job grid no longer exposes that editor and new execution authority is the typed instruction configuration.

- [x] React full-page Command Editor owns an isolated ExcelWrite file modal per instruction: directory, filename/type, delimiter, output key, destination column, and searchable reuse of other typed Bot Job targets (`74a345d`, `319a2cd`).
- [x] File-system selection/validation stays in Java. Three logical `excelWrite.*` operations reuse the already authoritative detached Command Editor WebSocket and exact transport/binding/owner contract; no second physical socket or duplicate persistence path was introduced.
- [x] Command Editor UPDATE persists the typed configuration and clears obsolete ExcelWrite element-parent fields. The independent READ variable slot is preserved and is the only runtime value source.
- [x] Execution loads all ExcelWrite instruction targets once, groups instructions that intentionally share one file, preserves instruction/column order, writes once at job completion, and serializes same-file writers without a lock-removal race.
- [x] Missing file means intentional bypass. A configured file requires a destination column and a valid absolute `.xlsx`/`.csv` target. Multiple Blocks may share one file; conflicting delimiters for that file fail closed.
- [x] Registered migration `2026-08-11__excelwrite_instruction_targets` safely backfills legacy file/column data only where no typed row exists, then clears obsolete E parents without deleting definitions or READ slots. Explicit typed blank files stay blank.
- [x] Focused JVM checks passed 17/17; production React build passed; exact deployment mirror is backend `03ce2179` with 58 files / 19 images and `main.86864372.js` / `main.cd1e36ee.css`.
- [x] BancaStato-only migration activation completed from an exact 5,373,952-byte backup (SHA-256 `5756E73B80ED5467E4107D3018EF3FC22C533155C848F632EE90B0D5D20B9147`), with one transaction, migration count 25, 25/25 typed E rows, zero E parents, unchanged variables/slots/Block export settings, clean integrity/FK checks, and no SQLite sidecars.
- [ ] Restart and live same-file multi-Block write acceptance remain separate gates. Eleven historical database-wide E rows lack READ slots (five active outside Jobs 5/32); bind or disable them explicitly. No live output workbook was modified by this checkpoint.
