# Save Component React Migration Roadmap

Date: 2026-07-11

## Objective

Replace `ARSaveComponentPane` and `ARSaveComponentScene` with a React floating panel and a transaction-safe Java service.

## Current Contract

- JavaFX receives `BlockDetailsDTO`, requires name and description, checks case-insensitive duplicate names, then performs separate component block/instruction/variable/reference writes.
- It normalizes component block order, reloads components, publishes `componentsUpdate`, and uses Java dialogs for duplicate/error/success feedback.

## Target Contract

- `componentSave.bootstrap`: source block summary, default name/description, ownership, duplicate-name capability.
- `componentSave.preview`: authoritative counts for instructions, variables, references, and dependent rewrites.
- `componentSave.apply`: request ID/revision plus validated name and description.
- One transaction owns component block, instructions, variables, rewrites, references, order normalization, and rollback.

## Implementation

- [x] Extract `SaveComponentService` from JavaFX controls.
- [x] Enforce source ownership, nonblank bounded names/descriptions, and case-insensitive uniqueness.
- [ ] Add authoritative preview and one atomic persistence transaction.
- [x] Return structured results and refreshed component instructions without dialogs.
- [ ] Build `SaveComponentPanel.tsx` and separated SCSS using the existing floating-panel design.
- [ ] Route all Save Component actions to React.
- [ ] Add validation, duplicate, rollback, idempotency, and bot-job/component ownership tests.
- [ ] Remove `ARSaveComponentPane`, `ARSaveComponentScene`, and server references after parity.

## Acceptance

- Save Component never opens JavaFX.
- Partial component copies cannot persist.
- Duplicate names and backend failures are inline and retryable.
- Component grids refresh from authoritative backend data.
