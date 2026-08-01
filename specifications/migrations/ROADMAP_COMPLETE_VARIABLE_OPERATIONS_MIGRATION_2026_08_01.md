# Complete Variable Operations Migration Roadmap

Date: 2026-08-01  
Status: planned; investigation complete; implementation not started  
Pilot owner: Home Banking 2, Bot Job 32  
Pilot database: `D:\Projects\ARWebBancaStato\ARWeb\database.db`

## 1. Purpose

Replace the remaining legacy variable-command contract stored in
`instruction.operation` with an explicit, typed, owner-scoped command model.

The migration must preserve two intentionally separate execution flows:

```text
NORMAL INPUT FLOW
Excel cell -> Input instruction -> Web Element
```

```text
VARIABLE COMMAND FLOW
GET        Web Element -> Runtime Variable
SET        Runtime Variable -> Web Element
ExcelWrite Runtime Variable -> Excel
CheckValue Runtime Variable + typed operand -> Validation result
CSV/PDF    Runtime Variable + external validation source -> Validation result
```

The normal Input flow must not be converted into a variable operation. Input continues reading
the current Excel row and typing directly into its selected Web Element. Variable commands use
only the durable Bot Job variable definition and runtime-value model.

## 2. Relationship to existing roadmaps

This roadmap extends:

- `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`
- `BOT_JOB_VARIABLE_RUNTIME_CUTOVER_2026_07_30.md`
- `P1_ENGINE_EXECUTION_SEMANTICS_2026_07_29.md`
- `INSTRUCTION_COMMAND_RULES_AUDIT.md`
- `ROADMAP_VARIABLES_COMMAND_EDITOR_MODAL_2026_08_01.md`

The Command Editor roadmap owns command-specific authoring, independent target-Block placement,
same-ID UPDATE, and relationship-free COPY NEW. This Variable Operations roadmap remains
authoritative for GET/SET/ExcelWrite/Check runtime semantics and typed variable persistence.

The durable definition/runtime cutover remains authoritative:

- `bot_job_variable_definition` owns Bot Job variable definitions;
- `bot_job_runtime_variable_value` owns the latest exact runtime value;
- `bot_job_runtime_memory` owns Bot Job-wide runtime revision/reset state;
- the legacy `variable` table is not an active runtime source;
- `VALUE("")` and `VOID(reason)` remain distinct;
- variable health never blocks Test Run or Launch.

This roadmap supersedes only the previously deferred variable-operation semantics:

- SET becomes a runtime-variable consumer that writes the variable value to a Web Element;
- ExcelWrite and CheckValue no longer require a Web Element parent;
- variable identity and comparison operands stop being parsed from `instruction.operation`;
- a single `producer_instruction_id` is not treated as the complete producer graph.

Older documents remain historical evidence and must not be rewritten to pretend this decision was
already implemented.

### 2.1 Relationship-contract correction recorded on 2026-08-01

The migration must remove the legacy assumption that every variable command needs a Web Element
parent. Relationship requirements are determined by the command's direction of data movement:

| Command | Web Element relationship | Variable relationship | Data movement |
|---|---|---|---|
| GET | required readable parent | required destination | Web Element -> variable |
| SET | required writable parent | required source | variable -> Web Element |
| ExcelWrite (`E`) | forbidden/not applicable | required source | variable -> Excel/CSV output |

For ExcelWrite, `variable_id` becomes the only runtime data-source relationship. Legacy
`parent_id`/`parent_block_id` values are migration evidence, not execution authority, and must be
cleared only by an audited migration transaction. The UI must therefore stop rendering or
requesting **Reconnect Parent** for ExcelWrite while continuing to render **Reconnect Variable**.

The correction applies consistently to:

- TypeScript command policy and graph diagnostics;
- Variables and GridItem relationship chips;
- Resolve Connections candidate generation;
- mutation construction and preflight validation;
- Java structural persistence validation;
- production execution and the planned smoke simulator.

CheckValue and external check commands remain variable-only under the target V2 contract in
Section 5. Commands not listed here retain their existing typed policies until separately audited.

### 2.2 Resolver auto-selection rule

The current TypeScript resolver has the correct fundamental selection rule:

```text
zero compatible targets   -> NO COMPATIBLE TARGET
one compatible target     -> AUTO SELECTED
multiple compatible       -> REVIEW REQUIRED / SELECT TARGET
```

It does not intentionally choose the first target when several compatible variables exist. If a
bulk resolve connects many commands to the same variable, the migration audit must verify whether
that variable was the only eligible candidate for every command. Candidate ordering is
presentation only and must never authorize a silent first-item assignment.

This is especially important for Bot Job 32, where legacy labels `$ORDER NUMBER` and `$SALDO`
currently share variable ID 3. The migration must propose a split or require manual confirmation;
it must not preserve that collision merely because variable 3 appears first.

## 3. Verified production evidence

The read-only SQLite audit of Bot Job 32, Block 223 (`#1 NEW TEST`) found:

| Order | ID | Action | Legacy operation | Parent ID | Variable ID |
|---:|---:|---|---|---:|---:|
| 1 | 1646 | C | null | null | null |
| 2 | 1640 | GET | `Order Number:$ORDER NUMBER` | 1646 | 3 |
| 4 | 1633 | `O:CHF 216.05` | null | null | null |
| 6 | 1654 | LOOP | `10:5` | 1646 | null |
| 7 | 1641 | CK | `$order number:!=:Condivise (SHA)` | 1646 | 3 |
| 9 | 1638 | `O:Condivise (SHA)` | null | null | null |
| 10 | 1642 | E | `$ORDER NUMBER` | 1646 | 3 |
| 12 | 1634 | GET | `Saldo:$SALDO` | 1646 | 3 |
| 13 | 1636 | E | `$SALDO` | 1646 | 3 |
| 14 | 1637 | CSV CHECK | `$SALDO:=:$EMPTY` | 1646 | 3 |
| 17 | 1648 | LOOP | `200:100` | 1646 | null |

Variable definition 3 is:

```text
type                    = $String
name                    = asdaadasd
configured_value        = null
producer_instruction_id = null
```

Its durable runtime value is:

```text
state       = VOID
raw_value   = null
void_reason = NO_PRODUCER_YET
source      = SYSTEM
revision    = 0
```

There are no Bot Job 32 rows in the legacy `variable` table and no Bot Job 32 migration notes.

### 3.1 Proven inconsistencies

One variable ID currently represents at least three different labels:

```text
definition name: asdaadasd
legacy label:    $ORDER NUMBER
legacy label:    $SALDO
```

Both GET instructions target Web Element instruction 1646, even though Block #1 separately
contains Web Elements named `Order Number` (1638) and `Saldo` (1633).

The current row presentation displays `$ORDER NUMBER` because React splits the persisted operation
string and renders its second segment. It does not obtain that name from variable definition 3 or
runtime memory.

This evidence prohibits a blind automatic migration. The migration must generate a proposal,
identify conflicts, and require explicit confirmation for ambiguous splits or Web Element targets.

## 4. Authoritative decisions

### D-001 - Input and SET remain different commands

Input reads the Excel execution row and writes that value directly to its Web Element. It does not
read or write runtime-variable memory unless a future, separately approved command explicitly
requests that behavior.

SET reads the selected runtime variable and writes its exact current raw value to the selected Web
Element. SET no longer obtains its value from a literal embedded in `instruction.operation`.

### D-002 - IDs are authoritative; labels are presentation

- `instruction.variable_id` selects the variable used by a variable command.
- `instruction.parent_id` selects a Web Element only for commands that require one.
- variable names come from `bot_job_variable_definition.name`.
- current values come from `bot_job_runtime_variable_value`.
- labels embedded in `instruction.operation` are migration hints only.

Renaming a variable must not require rewriting every command operation string.

### D-003 - Canonical runtime values are exact raw text

GET stores exactly what the Web Element returned. Canonical storage performs no trimming, locale
conversion, currency conversion, punctuation removal, or numeric coercion.

```text
VALUE("")                         legitimate empty page value
VALUE("CHF 1'234.50")             exact non-empty page value
VOID(NO_PRODUCER_YET)             no value has been produced
VOID(MISSING_PARENT)              producer has no usable Web Element
VOID(PRODUCER_FAILED)             producer attempt failed
```

### D-004 - Variable diagnostics never block execution

A VOID or disconnected variable produces a bounded diagnostic, bypasses only the dependent
variable operation, and lets the Bot Job continue. It never refuses Test Run or Launch.

VOID is never typed into a Web Element, written to Excel/CSV/PDF, or treated as user data.

### D-005 - Multiple ordered producers are valid

A variable may be written by multiple GET commands during one Bot Job. The value visible to a
consumer is the latest successful write preceding that consumer in execution order.

`bot_job_variable_definition.producer_instruction_id` is retained as nullable compatibility data
during migration but is not execution authority and must not be repurposed silently. Producers are
derived from variable-writing instructions and their order. The column can be retired only after
all consumers stop using it.

### D-006 - React owns authoring and migration intent

React/TypeScript owns:

- candidate grouping and conflict detection;
- migration preview;
- Web Element and variable selection;
- command-specific form validation;
- typed mutation construction;
- impact messages and confirmation;
- presentation and diagnostics.

Java owns:

- authenticated owner/session verification;
- structural contract validation;
- database transactions and compare-and-set revision checks;
- Playwright execution;
- durable runtime-memory writes;
- committed realtime publication.

Java must not infer a variable, Web Element, comparison operand, or output key that React did not
submit and the user did not confirm.

### D-007 - Migrated Bot Jobs never mix V1 and V2 execution

Each Bot Job has one active variable-operation model:

```text
LEGACY  operation-string execution
V2      typed variable-command execution
```

One execution cannot resolve some commands through legacy strings and others through V2. A Bot
Job changes to V2 only after its migration transaction and post-commit audit succeed.

## 5. Target command contract

| Command | Runtime role | Variable | Web Element | Typed configuration |
|---|---|---|---|---|
| Input (`I:*`) | Excel direct input | none | required | existing input fields |
| GET | producer | required | required | none |
| SET | consumer to page | required | required writable target | none |
| ExcelWrite (`E`) | consumer to Excel | required | none | output key/column |
| CheckValue (`CK`) | runtime validation | required | none | operator and operand |
| CSV CHECK | external validation | required | none | source/key/operator/operand |
| PDF CHECK | external validation | required | none | source/key/operator/operand |

LOOP, IF-family, GOTO, EXCEL GOTO, Pause, Wait, Refresh, Click, Output, and other actions retain
their separate typed relationship rules. They are not migrated into variable operations merely
because they appear near a variable command.

## 6. Typed persistence model

### 6.1 Bot Job model-state table

Add an owner-scoped state table instead of hiding activation in an operation string:

```sql
CREATE TABLE bot_job_variable_operation_state (
    home_banking_id       INTEGER NOT NULL,
    bot_job_id            INTEGER NOT NULL,
    model_version         TEXT NOT NULL,
    migration_status      TEXT NOT NULL,
    base_graph_revision   TEXT,
    migration_revision    INTEGER NOT NULL DEFAULT 0,
    audited_at            TEXT,
    migrated_at           TEXT,
    PRIMARY KEY (home_banking_id, bot_job_id)
);
```

Allowed status values:

```text
LEGACY
AUDITED
REVIEW_REQUIRED
READY
MIGRATING
V2
FAILED
ROLLED_BACK
```

### 6.2 Typed instruction configuration

Add one optional configuration row per variable command:

```sql
CREATE TABLE instruction_variable_command_config (
    home_banking_id       INTEGER NOT NULL,
    bot_job_id            INTEGER NOT NULL,
    instruction_id        INTEGER NOT NULL,
    command_type          TEXT NOT NULL,

    operand_kind          TEXT,
    comparison_operator   TEXT,
    operand_raw_value     TEXT,
    operand_variable_id   INTEGER,

    output_key            TEXT,
    external_source_key   TEXT,
    format_policy         TEXT,

    config_revision       INTEGER NOT NULL DEFAULT 1,
    created_at            TEXT NOT NULL,
    updated_at            TEXT NOT NULL,

    PRIMARY KEY (home_banking_id, bot_job_id, instruction_id)
);
```

Do not add restrictive foreign keys until the read-only audit proves existing production data can
satisfy them across every supported database dialect. Owner/session validation and transaction
verification remain mandatory from the first release.

### 6.3 Operand kinds

```text
LITERAL       compare with exact configured raw text
VARIABLE      compare with another runtime variable
EMPTY         compare with legitimate VALUE("")
VOID          explicitly inspect runtime VOID state
```

An operand is never encoded as `$EMPTY`, `#EMPTY`, `VOID`, or a colon-delimited token in canonical
V2 storage.

## 7. V2 execution semantics

### 7.1 Input

```text
Excel current row
  -> resolve configured column
  -> locate Input Web Element
  -> type value
```

No runtime variable is read or written.

### 7.2 GET

```text
instruction.parent_id
  -> locate Web Element with Playwright
  -> read exact text/value
  -> persist VALUE(raw text) for instruction.variable_id
  -> commit revision
  -> update Java cache
  -> publish realtime snapshot
```

Failure writes a typed VOID reason when a variable binding exists, publishes the diagnostic, and
continues execution.

### 7.3 SET

```text
instruction.variable_id
  -> read durable/current runtime value
  -> instruction.parent_id
  -> locate writable Web Element with Playwright
  -> type exact raw value
```

Rules:

- `VALUE("ABC")` types `ABC`;
- `VALUE("")` intentionally clears/types an empty value;
- VOID does not touch the page and emits a diagnostic;
- a missing/dangling/non-writable target emits a diagnostic;
- one primary Playwright attempt is used, with fallback only after an actual error.

SET does not write a configured literal back into runtime memory. A separate future literal
assignment command may be introduced if required; it must not overload SET.

### 7.4 ExcelWrite

ExcelWrite reads `instruction.variable_id`. Its destination label comes from typed `output_key` or,
when explicitly configured to default, the current variable-definition name. It never parses a
`$NAME` token from `instruction.operation`.

VOID bypasses only the write and records a diagnostic. `VALUE("")` writes a legitimate empty cell.

### 7.5 CheckValue

CheckValue reads:

```text
actual   = runtime value selected by instruction.variable_id
operator = typed comparison_operator
expected = typed operand
```

Initial operators:

```text
=  !=  >  <  >=  <=
contains
startsWith
endsWith
isEmpty
isNotEmpty
```

General arithmetic, Boolean expression trees, regular expressions, and date calculations are
separate capabilities and must not be hidden inside the first migration.

### 7.6 Numeric, currency, and date comparison

Canonical memory remains raw text. A consumer may request a comparison policy:

```text
RAW_TEXT
DECIMAL_US
DECIMAL_EU
CURRENCY_CH
CURRENCY_EU
CURRENCY_UK
DATE_ISO
DATE_LOCAL
AUTO_DETECT
```

Conversion failure returns an explicit diagnostic. V2 must remove the current behavior that strips
every period and comma before parsing, because that converts both `12.50` and `12,50` into `1250`.

### 7.7 CSV/PDF checks

CSV/PDF checks must have a typed external source/key and typed comparison configuration. They read
the runtime variable through `variable_id`; they do not depend on a Web Element `parent_id`.

The current `fieldsToValidate` and OUTPUT-key paths require an independent audit before claiming V2
parity. Until accepted, affected migrations remain `REVIEW_REQUIRED` rather than guessing a source.

## 8. React components and TypeScript separation

Create small components/modules instead of adding another large branch to `VariablesPage.tsx`:

```text
components/variables/migration/
  VariablesOperationMigrationPage.tsx
  VariablesOperationMigrationPage.module.scss
  VariablesOperationAuditSummary.tsx
  VariablesOperationConflictCard.tsx
  VariablesOperationGroupEditor.tsx
  VariablesOperationMigrationConfirmModal.tsx

components/variables/operations/
  VariableCommandEditor.tsx
  VariableCommandEditor.module.scss
  VariableOperandEditor.tsx
  VariableComparisonEditor.tsx
  VariableOutputTargetEditor.tsx

components/variables/domain/
  variableOperationAudit.ts
  variableOperationCandidates.ts
  variableOperationMigrationPlan.ts
  variableOperationV2.contract.ts
  variableComparisonPolicy.ts
```

Pure TypeScript modules must accept snapshots and return immutable plans. They must not open
WebSockets, persist data, mutate global state, or query the database.

The migration UI shows:

- legacy label and operation;
- authoritative instruction/variable IDs;
- current Web Element target;
- candidate targets with confidence reasons;
- proposed logical variable groups;
- exact instructions to be remapped;
- typed configuration to be created;
- unresolved conflicts that prevent activation.

## 9. Java separation of concerns

Create dedicated classes rather than extending legacy `PerformDataBase`/scanner branches:

```text
db/
  BotJobVariableOperationStateRepository.java
  InstructionVariableCommandConfigRepository.java

facade/variables/operations/
  VariableOperationAuditService.java
  VariableOperationMigrationService.java
  VariableOperationMigrationTransaction.java
  VariableOperationV2SnapshotService.java

facade/actions/variables/
  VariableGetExecutor.java
  VariableSetExecutor.java
  VariableExcelWriteExecutor.java
  VariableCheckExecutor.java
  VariableExternalCheckExecutor.java
  VariableComparisonService.java
```

Repositories receive caller-owned transactions. Executors receive already loaded typed command
facts and do not parse legacy operations.

## 10. Migration algorithm

### 10.1 Read-only audit

For every variable-capable instruction:

1. read action, owner, order, `variable_id`, `parent_id`, `parent_block_id`, and operation;
2. read the current variable definition and runtime state;
3. parse the legacy operation only as an audit hint;
4. identify every distinct normalized legacy label using the same variable ID;
5. identify GET writers and consumers in execution order;
6. identify candidate Web Elements in the same owner;
7. detect missing, dangling, ambiguous, incompatible, or out-of-order links;
8. produce a deterministic immutable proposal;
9. persist nothing.

### 10.2 Candidate grouping

Candidate groups may use:

- current `variable_id`;
- normalized legacy variable label;
- GET-to-consumer execution interval;
- output key;
- current and candidate Web Element names;
- explicit user selections.

Label similarity alone never authorizes a database change.

### 10.3 User-reviewed plan

For every ambiguous group, the client chooses:

```text
KEEP ONE VARIABLE
SPLIT INTO VARIABLES
SELECT WEB ELEMENT
SELECT VARIABLE
SET OUTPUT KEY
SET COMPARISON OPERAND
SKIP GROUP
CANCEL MIGRATION
```

### 10.4 Atomic migration

One owner-scoped transaction:

1. verifies session, owner, workspace epoch, graph revision, and migration revision;
2. locks/checks the Bot Job model-state row;
3. creates or updates variable definitions;
4. remaps exact submitted instruction `variable_id` values;
5. remaps exact submitted GET/SET `parent_id` values;
6. inserts typed command configurations;
7. initializes new runtime rows as `VOID(NO_PRODUCER_YET)`;
8. records a migration audit/note for every retained, split, and remapped identity;
9. verifies the final graph and typed configuration;
10. changes the model to V2 only after verification;
11. commits once;
12. publishes the committed authoritative snapshot.

Any failure rolls back every step. No automatic retry is allowed after an uncertain commit result.

## 11. Bot Job 32 pilot proposal

The first migration preview should propose, but not automatically apply:

### ORDER_NUMBER candidate

```text
GET         1640
CheckValue  1641
ExcelWrite  1642
```

### SALDO candidate

```text
GET         1634
ExcelWrite  1636
CSV CHECK   1637
```

Conflicts requiring client review:

1. both groups currently use variable definition 3 (`asdaadasd`);
2. both GET instructions currently target Web Element 1646 (`Test 1234`);
3. candidate Web Elements 1638 (`Order Number`) and 1633 (`Saldo`) exist, but label similarity is
   not sufficient authority;
4. CheckValue 1641 carries a legacy Web Element parent even though V2 CheckValue does not need one;
5. ExcelWrite and CSV CHECK carry legacy parent projections that V2 does not need;
6. CSV CHECK external-source semantics require review before V2 activation.

Expected preview:

```text
Variable-operation migration: Bot Job 32

Definitions:                 1
Proposed logical variables:  2
Variable commands:           6
Conflicting labels:          3
Ambiguous Web Elements:      2
External checks requiring review: 1
```

## 12. Small implementation phases

Every phase is a separate `CODEX:` commit. Do not combine schema, UI, execution, and activation in
one commit.

### P0 - Freeze evidence and contracts

- [ ] Add Bot Job 32 read-only audit fixture/export with sensitive locator values redacted.
- [ ] Freeze V1 behavior for Input, GET, SET, ExcelWrite, CK, CSV CHECK, and PDF CHECK.
- [ ] Freeze V2 command matrix and VOID behavior.
- [ ] Confirm supported database dialects and backup/restore implications.

Rollback: documentation/fixtures only.

### P1 - Pure TypeScript audit

- [ ] Implement typed audit inputs/results.
- [ ] Detect label/ID/parent/producer/consumer conflicts.
- [ ] Generate deterministic candidate groups without persistence.
- [ ] Keep current Variables and GridItem behavior unchanged.

Rollback: remove the unused audit module.

### P2 - Read-only Java snapshot

- [ ] Add separated repositories and audit snapshot service.
- [ ] Expose owner-scoped raw facts only.
- [ ] Add correlated WebSocket request/response.
- [ ] Preserve the last valid Variables page on errors.

Rollback: remove/disable the new read-only route.

### P3 - Migration preview UI

- [ ] Create the independent migration page/modal components.
- [ ] Show candidate groups and conflicts.
- [ ] Allow selections and edits in React drafts only.
- [ ] Do not expose an Apply button until the persistence contract is accepted.

Rollback: hide the migration entry action.

### P4 - Database schema

- [ ] Add dated idempotent migrations for model state and command configuration.
- [ ] Implement every production dialect.
- [ ] Update backup/restore/export/import.
- [ ] Do not change any Bot Job to V2.

Rollback: restore the database backup or remove only empty new tables.

### P5 - Atomic migration transaction

- [ ] Accept an exact React-authored plan.
- [ ] Validate expected-old values and owner scope.
- [ ] Persist remaps/configuration/runtime initialization atomically.
- [ ] Record audit notes.
- [ ] Keep activation disabled initially.

Rollback: transaction rollback; no partial state.

### P6 - V2 comparison service

- [ ] Implement one comparison service.
- [ ] Preserve exact raw values.
- [ ] Add explicit format policies.
- [ ] Remove punctuation-stripping numeric behavior from the V2 path.
- [ ] Keep V1 unchanged.

Rollback: V2 capability remains disabled.

### P7 - GET V2 executor

- [ ] Read only through `parent_id`.
- [ ] Write only through `variable_id` and durable runtime service.
- [ ] Publish committed values/revisions.
- [ ] Preserve non-blocking VOID diagnostics.

Rollback: keep target Bot Job in LEGACY.

### P8 - SET V2 executor

- [ ] Read runtime variable.
- [ ] Write exact value to the selected writable Web Element.
- [ ] Distinguish VALUE("") from VOID.
- [ ] Guarantee one primary Playwright attempt.
- [ ] Do not read a literal from legacy operation.

Rollback: keep target Bot Job in LEGACY.

### P9 - ExcelWrite and CheckValue V2 executors

- [ ] Remove Web Element requirement.
- [ ] Use typed output/comparison configuration.
- [ ] Consume runtime variables by ID.
- [ ] Report VOID without blocking execution.

Rollback: keep target Bot Job in LEGACY.

### P10 - CSV/PDF V2 executors

- [ ] Complete external-source audit.
- [ ] Add typed source and operand contracts.
- [ ] Remove OUTPUT-key/operation-string inference.
- [ ] Do not activate ambiguous migrated checks.

Rollback: keep affected Bot Job/group in LEGACY until whole-owner activation is possible.

### P11 - Bot Job 32 dry run and migration

- [ ] Back up `database.db`.
- [ ] Run read-only audit and review every conflict.
- [ ] Apply the accepted transaction.
- [ ] Re-run audit.
- [ ] Verify runtime reset/KEEP behavior.
- [ ] Activate V2 only after all Block #1 variable commands are typed.

Rollback: restore the pre-migration backup and return the model-state row to LEGACY.

### P12 - Production rollout

- [ ] Audit every Bot Job.
- [ ] Migrate unambiguous jobs in reviewed batches.
- [ ] Leave ambiguous jobs in LEGACY.
- [ ] Record per-owner acceptance and bundle/database versions.

Rollback: per-owner model version and database backup.

### P13 - Retire legacy operation authority

- [ ] Stop generating variable labels/operands inside `instruction.operation`.
- [ ] Stop rendering variable names from operation strings.
- [ ] Remove V2 fallback to V1 parsing.
- [ ] Retire duplicate CheckValue evaluators.
- [ ] Retire `producer_instruction_id` only after all consumers are removed.
- [ ] Retain legacy columns/tables through the agreed rollback window.

Rollback: requires the tagged pre-retirement release and verified database backup.

## 13. Realtime synchronization

Every committed variable operation update follows:

```text
React draft/selection
  -> correlated WebSocket command
  -> Java owner/revision validation
  -> database transaction
  -> commit
  -> Java cache update
  -> authoritative publication
  -> Variables page + Bot Job Details reconciliation
```

Messages include:

- request ID;
- owner identity;
- workspace epoch;
- graph revision;
- migration/config revision;
- runtime revision where relevant;
- exact affected instruction/variable IDs;
- committed/refused result.

Closing/reopening the Variables page or Bot Job Details reloads durable committed values. It never
resets them.

## 14. Acceptance criteria

### Input separation

- [ ] Input continues reading Excel and typing directly into its Web Element.
- [ ] Input does not require a runtime variable.
- [ ] Variable migration does not change Input execution.

### GET

- [ ] GET reads the selected Web Element and stores exact raw text.
- [ ] GET empty text becomes VALUE("").
- [ ] Failed/missing producer becomes typed VOID and execution continues.
- [ ] GET never derives identity from an operation label.

### SET

- [ ] SET reads the selected runtime variable.
- [ ] SET writes VALUE("") as a legitimate empty value.
- [ ] SET never writes VOID to the page.
- [ ] SET does not use a legacy literal.
- [ ] Playwright receives one primary attempt.

### ExcelWrite and checks

- [ ] ExcelWrite uses variable ID and typed output key.
- [ ] CheckValue uses one typed comparison service.
- [ ] Numeric/currency/date conversion is explicit and does not modify canonical memory.
- [ ] CK/E/CSV/PDF no longer require irrelevant Web Element parents in V2.
- [ ] VOID bypasses only the dependent operation.

### Migration

- [ ] Bot Job 32 shows the ORDER_NUMBER/SALDO conflict before mutation.
- [ ] No ambiguous Web Element or variable is selected silently.
- [ ] Migration is atomic and idempotent.
- [ ] The post-migration audit matches the accepted plan.
- [ ] LEGACY and V2 are never mixed in one execution.
- [ ] Backup/restore returns the exact pre-migration state.

### UI reliability

- [ ] The grid and Variables page remain visible during audit, migration, refusal, and refresh.
- [ ] Reconnect and variable badges use typed relationships.
- [ ] No client-facing message mentions React, Java, DTOs, or database implementation details.

## 15. Verification strategy

Tests are implemented alongside each activated phase, not as one giant final rewrite.

Focused verification must cover:

- pure TypeScript audit/grouping and ambiguity rules;
- owner/epoch/revision rejection;
- SQLite/PostgreSQL/SQL Server/Access migrations where supported;
- exact raw-value preservation;
- VALUE("") versus VOID;
- multiple ordered GET producers;
- SET VALUE/empty/VOID behavior;
- locale-aware comparisons;
- colon-containing literal operands;
- migration transaction rollback injection;
- realtime stale-response rejection;
- Input-flow non-regression;
- Test Run/Launch non-blocking variable diagnostics;
- Bot Job 32 accepted migration fixture.

Manual acceptance uses a disposable Bot Job/database copy before the production Bot Job is
migrated.

## 16. Observability

Audit/migration logs include IDs and revisions, never runtime values or locator secrets:

```text
operation
request_id
home_banking_id
bot_job_id
workspace_epoch
base/committed revision
affected instruction IDs
created/remapped variable IDs
conflict/result codes
elapsed time
```

Runtime diagnostics may identify instruction and variable IDs but must redact user values by
default.

## 17. Definition of done

The migration is complete only when:

- [ ] Input and variable-command execution are demonstrably independent;
- [ ] GET, SET, ExcelWrite, CK, CSV CHECK, and PDF CHECK use typed V2 contracts;
- [ ] variable names and identities are no longer parsed from `instruction.operation`;
- [ ] SET consumes runtime memory and writes to a Web Element;
- [ ] exact runtime values survive stop, refresh, reconnect, and backend restart;
- [ ] multiple ordered producers behave deterministically;
- [ ] international numeric/currency/date checks use explicit policies;
- [ ] variable health never blocks Test Run or Launch;
- [ ] all migrated Bot Jobs pass post-migration audit;
- [ ] ambiguous Bot Jobs remain safely in LEGACY until reviewed;
- [ ] backup, restore, rollback, and realtime convergence are proven;
- [ ] the legacy operation-variable path is removed after the rollback window.

## 18. Immediate next step

Implement P0 and P1 only:

1. freeze a redacted Bot Job 32 audit fixture;
2. define the V2 TypeScript contracts;
3. implement the pure read-only conflict/candidate analyzer;
4. produce the expected ORDER_NUMBER/SALDO proposal;
5. stop for review before creating schema, persistence, or execution changes.
