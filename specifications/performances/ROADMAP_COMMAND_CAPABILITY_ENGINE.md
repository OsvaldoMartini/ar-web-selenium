# Command Capability Engine Roadmap

Date: 2026-07-10

## Objective

Finish the JavaFX removal by replacing generic command availability with a Java-owned capability engine and action-specific React forms.

The detailed positive/negative UI rules are maintained in `INSTRUCTION_ACTION_CAPABILITY_MATRIX.md`.

## Immediate Correction

Remove **Insert empty step before/after** from the floating panel or route it to React command mode. Those actions currently trigger `ARNewCommandScene.showModal()`.

No normal command-panel action may send legacy `INSERT_BEFORE`, `INSERT_AFTER`, `INSERT_NEW`, or `EDIT_OPERATION` to the scene route.

## Java Separation

Create:

- `CommandRegistry`: canonical action codes, aliases, labels, target kind, edit family, fields.
- `CommandCapabilityService`: allowed commands/actions for selected row and placement.
- `CommandDraftCodec`: typed fields to canonical legacy operation string and reverse.
- `CommandRuleValidator`: element/tag, variable, block, graph, uniqueness, and placement rules.
- `CommandMutationService`: idempotent transactional create/edit.
- `VariableReferenceService`: variable usage and parent-operation rewrite.

All classes must be JavaFX-free.

## Capability Response

For each selected instruction return:

- row kind and element kind;
- allowed row actions with disabled reasons;
- allowed command definitions;
- allowed placements;
- compatible elements;
- compatible variables;
- compatible destination blocks;
- edit restrictions;
- delete impact summary;
- graph revision.

## Typed Command Schemas

Do not use one generic `operation` text field.

| Command family | Required controls |
|---|---|
| SET | compatible writable element, variable/value |
| GET | compatible readable element, destination variable |
| CK/PDF/CSV | variable, operator, expected value/source |
| Extract Field | readable element, destination variable |
| GOTO | destination block, repetitions |
| EXCEL GOTO | destination block; singleton status |
| LOOP/REFRESH_LOOP | eligible parent element, repetitions/count |
| IF | placement only; Java creates complete family |
| Swipe | repetition count |
| Wait | seconds |
| Independent | no irrelevant fields |

React renders fields from Java metadata and sends typed values. Java builds the operation string.

## Phases

### Phase 1 - Canonical Registry

Resolve aliases (`H/HOLD`, `P/SCREEN`, `Q/QUIT`) and build round-trip fixtures from existing database rows.

### Phase 2 - Capability Matrix

Implement row-kind, element-kind, graph, session, target, and placement rules. Unknown combinations must default to denied, not allowed.

### Phase 3 - Codec Parity

Port all `ARNewCommandPane` serialization and `setSelectedIndexByValue` parsing into `CommandDraftCodec` tests.

### Phase 4 - Variable Parity

Port rename/type parent-operation rewrites, usage lookup, refresh publication, and context scoping from `ARElementValuePane`.

### Phase 5 - Direct Mutation

Replace scene forwarding with `commandEditor.apply`. Add `requestId` idempotency, graph revision checks, and structured errors.

### Phase 6 - Metadata-Driven React Forms

Replace the static React command array and free-form operation field. Filter commands and fields from backend capabilities.

### Phase 7 - Remove Legacy Routes

Remove normal UI callers of the legacy scene messages. Keep a temporary feature flag only until end-to-end parity passes.

### Phase 8 - Delete JavaFX Panes

Remove `ARNewCommandPane`, `ARNewCommandScene`, `ARElementValuePane`, and `ARElementValueScene` plus cleanup references.

## Required Tests

- Every command round trips legacy row -> typed draft -> canonical row.
- Every row kind receives only compatible commands.
- IF creation always produces a valid family.
- Loop commands cannot detach from their parent.
- EXCEL GOTO singleton behavior is deterministic.
- Variable rename/retype updates every dependent operation.
- Direct legacy scene messages are no longer emitted by React.
- Bot-job and component sessions have parity.

## Acceptance Criteria

- Clicking any panel action never opens JavaFX.
- Unsupported commands are absent or disabled with a backend reason.
- React never constructs canonical operation strings.
- All mutations are validated against current database graph revision.
- Variables and dependent operation strings remain consistent.
