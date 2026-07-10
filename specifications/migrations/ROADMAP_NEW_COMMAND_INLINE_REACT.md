# ARNewCommandPane Inline React Migration Roadmap

Date: 2026-07-10

## Objective

Remove the JavaFX `ARNewCommandPane` user interface and migrate all **Add/Update Operations** behavior to React TypeScript.

This migration should not create another standalone page. The existing down-arrow on every instruction/step remains the entry point, but its small dropdown is replaced by a **non-modal, draggable floating panel** using the established `CreateNewBlock` and `OCRPanel` design. The same shared panel must be used by both `GridItem` and `GridItemComp`.

The panel unifies the current row operations (Insert Before, Insert After, Split Component, Delete) with every command and function currently inside **New Command**. This removes duplicated dropdown logic and gives command creation enough space for complex parameters without leaving the instruction grid.

`ARElementValuePane` / **New Variables** is part of this critical migration scope. Variables are a core dependency of many commands, so the floating panel must include a React Variables workspace backed by a separated Java API. A legacy JavaFX variable-dialog bridge may exist only as an emergency feature-flag fallback during development, not as the target solution.

## Why This Migration Is High Risk

`ARNewCommandPane` is currently a 3,300-line singleton that combines:

- JavaFX layout and dynamic field visibility
- command catalogue construction
- action-specific parsing and formatting
- web-page element loading and refresh
- bot-job variable loading and creation
- block and GOTO target loading
- IF/loop placement rules
- add versus update behavior
- instruction-memory mutation
- database persistence
- React-grid refresh messages
- bot-job and component-task variants

A direct UI rewrite would duplicate rules in TypeScript and likely create differences between memory and database state. The first work must therefore separate the Java behavior into testable backend classes with typed request/response DTOs.

## Current Owners And Entry Points

Primary JavaFX implementation:

- `src/main/java/com/allinweb/ch/component/pane/ARNewCommandPane.java`
- `src/main/java/com/allinweb/ch/component/scene/ARNewCommandScene.java`

Related backend/state:

- `PerformDataBase`
- `PerformDBEngine`
- `PerformLists`
- `PerformMessage`
- `SplitDTO`
- `InstructionLoad`
- `InstructionOperationDTO`
- `WebElementIcon`
- `ARConstants`
- `ARElementValueScene`
- `SimpleWebSocketServer`
- `WebSocketSessionManager`

Current React instruction UI:

- `/srv/projects/ar-react-ts-grid/src/components/GridItem.tsx`
- `/srv/projects/ar-react-ts-grid/src/components/Griditem.module.scss`
- `/srv/projects/ar-react-ts-grid/src/components/BlockInstructionsDnd.tsx`
- `/srv/projects/ar-react-ts-grid/src/components/CreateNewBlock.tsx`

`GridItem.tsx` and `GridItemComp.tsx` both render a down-arrow menu per instruction with overlapping Insert Before, Insert After, Split Component, Edit Operation, conditional insertion, and Delete behavior. The down-arrow remains the correct entry point, but the duplicated menus must be replaced by one shared floating-panel component and one shared action controller.

## Migration Rules

- Keep `ARNewCommandPane`, `ARNewCommandScene`, `ARElementValuePane`, and `ARElementValueScene` available behind a temporary fallback until parity tests pass.
- Do not move SQL, memory-list mutation, command legality rules, or action serialization into React.
- Do not let React construct the final legacy `operation` string. Java must return and persist the canonical representation.
- Do not optimistically mutate the instruction grid before Java confirms success.
- Every mutation must carry a `requestId` and return one authoritative refreshed instruction/block payload.
- Support both `botJobTasks` and `componentTasks`; do not silently implement only bot jobs.
- Use the same React panel and backend contracts from both `GridItem` and `GridItemComp`; do not maintain two implementations.
- Consolidate Insert Before, Insert After, Split Component, Delete, Add Command, Edit Command, and Variables into this shared workflow.
- Preserve IF/ELSE/ENDIF and LOOP/REFRESH_LOOP structural constraints.
- Do not run Java/Maven/GUI during implementation unless explicitly requested.
- Build React with `npm run build`, then replace `src/main/resources/build` with the resulting build.
- Commit and push each completed roadmap phase in both existing branches.

## Target Interaction

Clicking the instruction down-arrow opens one responsive **Instruction Command Panel**, not a small dropdown. It follows the floating, draggable, non-modal behavior and visual language already used by `CreateNewBlock` and `OCRPanel`. It must stay within the React/JCEF viewport, clamp its position after resize/maximize, and use internal scrolling for long forms.

The panel starts with a compact action rail:

- `Add Command Before`
- `Add Command After`
- `Edit Command` when editable
- `Split Component` when legal
- conditional structure actions such as Insert ElseIf when legal
- `Variables`
- `Delete` with confirmation

Only actions valid for the selected instruction and session are enabled. Java returns these capabilities; React does not independently guess structural legality.

Choosing Add/Edit changes the same floating panel into the command editor. Choosing Variables changes it into the Variables workspace. Back returns to the action rail without closing the panel. This avoids nested dialogs and preserves the selected instruction context.

Editor sections:

1. **Command** dropdown, grouped by category.
2. **Target** selector for web element, variable, block, or no target as dictated by command metadata.
3. **Parameters** rendered from backend field definitions: operator, value, repetitions, loop count, GOTO row, block target, hold state, and command-specific options.
4. **Preview** showing a human-readable operation generated by Java validation.
5. `Apply` and `Cancel` commands.

Variables workspace:

1. Search and select variables available to the current bot job/component context.
2. Create, edit, and delete a variable without leaving the command panel.
3. Show variable name, value/source, scope/type, and usage information required by the existing backend.
4. Prevent deletion when referenced by instructions and return the referencing blocks/instructions in the warning.
5. After create/update, select the variable in the command draft without losing unsaved command fields.

The menu itself starts the workflow with explicit placement context. The user should not have to reopen a separate Add/Update Operations window.

## Proposed Java Separation

Create a dedicated package such as:

`src/main/java/com/allinweb/ch/facade/command/`

Suggested classes:

| Class | Responsibility |
|---|---|
| `CommandEditorService` | Orchestrates bootstrap, validation, save, refresh, and legacy fallback |
| `CommandCatalogService` | Supplies supported commands and action-specific field metadata |
| `CommandContextService` | Loads blocks, elements, variables, current instruction, and placement context |
| `CommandValidationService` | Validates required targets, values, limits, duplicate names, and graph placement |
| `CommandOperationCodec` | Parses legacy action/operation strings into typed fields and serializes canonical values |
| `CommandPersistenceService` | Performs one transactional add/update and coordinates memory refresh |
| `CommandPlacementService` | Resolves before/after/order/parent rules for IF and loop families |
| `CommandRefreshPublisher` | Publishes the authoritative updated block/instruction payload to React sessions |
| `VariableEditorService` | Orchestrates variable bootstrap, validation, create, update, delete, and usage checks |
| `VariableValidationService` | Enforces variable naming, values, uniqueness, scope, and command compatibility |
| `VariablePersistenceService` | Performs transactional variable mutations and refreshes affected command choices |
| `InstructionActionService` | Centralizes Insert Before/After, Split, conditional actions, and Delete for both grids |

Suggested DTO package:

`src/main/java/com/allinweb/ch/model/command/`

- `CommandEditorContextDTO`
- `CommandDefinitionDTO`
- `CommandFieldDefinitionDTO`
- `CommandChoiceDTO`
- `CommandDraftDTO`
- `CommandPlacementDTO`
- `CommandValidationResultDTO`
- `CommandMutationResultDTO`
- `VariableEditorContextDTO`
- `VariableDraftDTO`
- `VariableUsageDTO`
- `VariableMutationResultDTO`
- `InstructionActionCapabilitiesDTO`

The services may initially delegate to extracted methods preserving current behavior. Only after parity is proven should dead JavaFX-specific code be removed.

## Command Metadata Model

Java should describe the form instead of React hard-coding a large switch statement.

Minimum command definition:

```ts
type CommandDefinition = {
  code: string;
  label: string;
  category: "value" | "validation" | "navigation" | "flow" | "device" | "utility";
  target: "element" | "variable" | "block" | "none";
  fields: CommandFieldDefinition[];
  supportsCreate: boolean;
  supportsEdit: boolean;
  allowedSessions: Array<"botJobTasks" | "componentTasks">;
};
```

Minimum field definition:

```ts
type CommandFieldDefinition = {
  key: string;
  label: string;
  control: "text" | "number" | "select" | "toggle";
  required: boolean;
  min?: number;
  max?: number;
  options?: Array<{ value: string; label: string }>;
};
```

Catalogue parity must include at least all commands currently exposed by `ARNewCommandPane`, including Set Value, Get Value, Check Value, PDF Check, CSV Check, NEXT/ENTER, SWIPE UP, SWIPE DOWN, IF, GOTO, EXCEL GOTO, EXTRACT FIELD, Refresh, Loop, Refresh Loop, pause/wait presets, close action, and screenshot.

## WebSocket Contract

Use dedicated message types rather than the current scene-local WebSocket client.

React to Java:

| Type | Payload | Purpose |
|---|---|---|
| `commandEditor.bootstrap` | context and placement | Load catalogue, choices, selected instruction, and capabilities |
| `commandEditor.refreshElements` | context | Reload scanned/page elements |
| `commandEditor.validate` | typed draft | Return field errors and canonical preview without saving |
| `commandEditor.apply` | typed draft plus `requestId` | Atomically add or update one command |
| `commandEditor.performRowAction` | context, action, `requestId` | Insert placeholder/conditional, split, or delete through one backend path |
| `commandEditor.cancel` | context | Clear editor state; no database mutation |
| `variableEditor.bootstrap` | command context | Load variable definitions, choices, and usage metadata |
| `variableEditor.validate` | typed variable draft | Validate without saving |
| `variableEditor.save` | typed draft plus `requestId` | Create or update a variable atomically |
| `variableEditor.delete` | variable id plus `requestId` | Delete only after backend usage check and confirmation |

Java to React:

| Type | Payload |
|---|---|
| `commandEditor.bootstrapResponse` | definitions, choices, draft, placement, capabilities |
| `commandEditor.elementsResponse` | refreshed element choices |
| `commandEditor.validationResponse` | valid, errors, warnings, canonical preview |
| `commandEditor.applyResponse` | ok, message, instruction id, updated blocks/instructions, requestId |
| `commandEditor.rowActionResponse` | ok, updated blocks/instructions, capabilities, requestId |
| `commandEditor.error` | code, message, field errors, requestId |
| `variableEditor.bootstrapResponse` | variables, definitions, selected variable, capabilities |
| `variableEditor.validationResponse` | valid, errors, warnings |
| `variableEditor.saveResponse` | ok, saved variable, refreshed choices, usage, requestId |
| `variableEditor.deleteResponse` | ok, blocked, usages, refreshed choices, requestId |

Context must contain stable IDs, not display names:

```ts
type CommandEditorContext = {
  sessionId: "botJobTasks" | "componentTasks";
  botJobId?: number;
  homeBankingId?: number;
  blockId: number;
  anchorInstructionId: number;
  editInstructionId?: number;
  placement: "before" | "after" | "edit";
};
```

## React Files

Create focused components under `/srv/projects/ar-react-ts-grid/src/components`:

- `InstructionCommandPanel.tsx`
- `InstructionCommandPanel.module.scss`
- `CommandEditor.tsx`
- `VariableEditor.tsx`
- `commandEditorTypes.ts`
- `useCommandEditor.ts`
- `useVariableEditor.ts`
- `useInstructionActions.ts`

Integrate the same components into both `GridItem.tsx` and `GridItemComp.tsx`; do not add a new root/session page. Both grid files are already large and duplicate important row operations, so panel state, message parsing, capabilities, and action handlers must move into the shared hooks/components rather than expanding either grid.

The SCSS should reuse the layout behavior of `CreateNewBlock.module.scss` and `OCRPanel.module.scss`, but the new panel must own its styles in `InstructionCommandPanel.module.scss`. Do not couple it to either grid's CSS module.

## Phased Implementation

### Phase 0 - Behavior Inventory And Safety Baseline

- Record every command shown by `itemsInstructions` and every quick-action button.
- Record create/edit serialization examples for each command.
- Map validation branches from `initUIBehaviour`, `recallMessages`, `setSelectedIndexByValue`, and `insertNewInstruction`.
- Map database and in-memory calls made by `insertNewInstruction`.
- Record differences between bot-job and component-task tables.
- Add fixture-based codec tests before extraction where practical.

Exit criteria: a command parity matrix identifies target type, fields, validation, serialized action/operation, and supported context for every command.

### Phase 1 - Extract Pure Command Rules

- Implement `CommandOperationCodec` without JavaFX imports.
- Implement command catalogue metadata.
- Extract validation into `CommandValidationService`.
- Extract placement/graph rules into `CommandPlacementService`.
- Keep the JavaFX pane calling these services so behavior remains unchanged.

Exit criteria: JavaFX still works, and command parsing/validation no longer depends on JavaFX controls.

### Phase 2 - Extract Persistence And Refresh

- Move add/update database logic out of `ARNewCommandPane`.
- Make mutation transactional where supported.
- Update `PerformLists` only after database success.
- Publish one authoritative grid refresh after mutation.
- Add request-id idempotency to prevent double insertion from repeated clicks/reconnects.

Exit criteria: the JavaFX pane delegates mutations to `CommandPersistenceService` and contains no direct command SQL/database mutation.

### Phase 3 - Consolidate Existing Row Actions

- Extract Insert Before, Insert After, Split Component, conditional insertion, and Delete from duplicated grid handlers into `InstructionActionService` and a shared React hook.
- Return action capabilities from Java for the selected instruction.
- Keep existing behavior during extraction and add confirmation/blocked responses for destructive actions.
- Ensure both `GridItem` and `GridItemComp` consume the same contract.

Exit criteria: the two grids no longer maintain independent row-action business logic.

### Phase 4 - Extract Variables Backend

- Inventory all `ARElementValuePane` fields, validation, table differences, and callers.
- Implement variable DTOs, validation, usage lookup, and transactional persistence without JavaFX imports.
- Make `ARElementValuePane` delegate to the extracted services during parity development.
- Prevent deletion of referenced variables and return useful usage details.

Exit criteria: variable CRUD and usage validation can run through Java services without opening JavaFX.

### Phase 5 - Add Backend APIs

- Add `CommandEditorService` routing in `SimpleWebSocketServer`.
- Implement command bootstrap, refresh, validate, apply, row-action, and cancel handlers.
- Implement variable bootstrap, validate, save, and delete handlers.
- Return structured errors; never HTML-formatted JavaFX messages.
- Verify stale instruction/block IDs are rejected with a refresh-required response.

Exit criteria: a WebSocket client can create and edit every command without opening `ARNewCommandScene`.

### Phase 6 - Build Shared React Floating Panel

- Add the shared floating panel, command editor, variable editor, hooks, and isolated SCSS module.
- Keep the existing down-arrow in both grids, but make it open the panel instead of rendering the old dropdown.
- Move Insert Before/After, Split, conditional actions, Delete, Add/Edit Command, and Variables into the panel action rail.
- Reuse the draggable non-modal behavior of Create New Block/OCR while clamping within the JCEF viewport.
- Render fields from Java command metadata.
- Add loading, empty, validation, reconnect, conflict, and saving states.
- Disable Apply while saving and correlate the response by `requestId`.
- Preserve focus and scroll position after a successful refresh.

Exit criteria: both grids use the same panel and all row/command workflows are usable without a JavaFX command window.

### Phase 7 - Build React Variables Workspace

- Add the Variables view inside the same floating panel.
- Support search, select, create, edit, delete, validation, and usage-blocked warnings.
- Preserve the current command draft while switching between Command and Variables views.
- Select a newly created variable automatically when returning to the command editor.
- Keep the legacy `ARElementValueScene` only behind an emergency feature flag during stabilization.

Exit criteria: normal variable workflows are React-only and integrated with command creation.

### Phase 8 - Parity And Regression Testing

- Test add before and after at first, middle, and last positions.
- Test editing without changing order or parent relationships.
- Test every command family and quick action.
- Test IF/ELSEIF/ELSE/ENDIF and loop placement rejection/acceptance.
- Test bot-job and component-task sessions.
- Run every row action from both `GridItem` and `GridItemComp` and compare results.
- Test variable create/edit/delete, duplicate names, references, and automatic selection in a command draft.
- Test panel drag, viewport clamping, maximize/restore, internal scroll, and narrow JCEF dimensions.
- Test element refresh and stale/deleted targets.
- Test reconnect and duplicate Apply messages.
- Confirm database rows and in-memory grid payloads agree after each operation.

Exit criteria: the parity matrix passes and no duplicate or partially persisted instruction can be produced.

### Phase 9 - Switch Default And Remove JavaFX UI

- Route all instruction command entry points to React.
- Keep a temporary feature flag for the legacy pane during one stabilization release.
- Remove `ARNewCommandPane`, `ARNewCommandScene`, `ARElementValuePane`, and `ARElementValueScene` only after production verification.
- Remove scene cleanup references from `ARConfigurationPane` and `ConfigService` when the scene is deleted.
- Retain reusable backend services and DTOs.

Exit criteria: no active workflow instantiates `ARNewCommandPane`; command creation/editing is React-only.

## Test Matrix

Minimum automated coverage:

| Area | Required checks |
|---|---|
| Codec | Legacy operation to draft and draft to canonical operation round trips |
| Validation | Required targets, numeric limits 1-9999, duplicate names, invalid block targets |
| Placement | before/after/edit, IF family, loop family, first/last instruction |
| Persistence | create, update, rollback on failure, memory/database consistency |
| Idempotency | repeated `requestId` creates only one instruction |
| WebSocket | bootstrap, validate, apply, structured errors, reconnect |
| React | metadata field rendering, menu behavior, disabled Apply, errors, responsive panel |
| Shared actions | identical behavior from GridItem and GridItemComp; no duplicated action decisions |
| Variables | CRUD, uniqueness, usage blocking, command compatibility, selection retention |
| Floating UI | drag, bounds clamping, maximize/restore, scroll, focus, no clipped controls |
| End to end | SQLite bot job and component command add/edit reflected in grid and database |

The SQLite end-to-end suite should use a copied temporary database, never the user's working database. It should assert both UI state and direct database results.

## Acceptance Criteria

- No separate Add/Update Operations or New Variables page is needed for normal use.
- Every instruction row in both grids opens the shared floating panel from its down-arrow.
- The old compact dropdown and duplicated row-action handlers are removed.
- All current `ARNewCommandPane` commands can be created and edited.
- React contains no SQL and does not encode legacy operation strings independently.
- Java command services have no JavaFX control dependencies.
- Apply is atomic, idempotent, and returns an authoritative refreshed grid payload.
- Bot-job and component-task behavior remains supported.
- The editor resizes correctly with the JCEF container and does not clip or wrap row controls.
- Variables can be created, edited, selected, and safely deleted from the React panel.
- Referenced variables cannot be deleted and the user receives a useful dependency warning.
- React build succeeds and the bundled resource build is updated.

## Critical Sequencing Note

Variables must be extracted before the React command panel becomes the default because command compatibility and persisted operation strings depend on stable variable identities. The legacy variable pane may remain as a fallback during development, but the migration is not complete while normal command creation still requires JavaFX.
