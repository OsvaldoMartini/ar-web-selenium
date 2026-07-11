# Excel File Workflow React Migration Roadmap

Date: 2026-07-11

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

- [ ] Extract pane-free `ExcelExportService` with structured parsing/encoding.
- [ ] Validate block ownership, path confinement, filename, extension, delimiter, and CSV column selections.
- [ ] Persist and refresh only after successful validation.
- [ ] Add request idempotency and structured errors; remove Java dialogs.
- [ ] Build `ExcelExportPanel.tsx` and `ExcelExportPanel.module.scss`.
- [ ] Open the floating panel from both grids without JavaFX.
- [ ] Preserve bot-job/component parity and authoritative refresh.
- [ ] Add parser, validation, persistence-response, and React payload tests.
- [ ] Remove `ARExcelFilePane`, `ARExcelFileScene`, and server references after parity.

## Acceptance

- Excel configuration never opens JavaFX.
- Existing encoded export settings round-trip without data loss.
- Invalid paths/files never mutate a block.
- Save/Clear update the correct grid in real time.

