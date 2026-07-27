# Variable System Redesign — design & roadmap (Phase 0A active)

Captured 2026-07-26 from user. Implementation started 2026-07-27 with a read-only
production audit and backward-compatible integrity guards.

## The problem (today)
- An instruction can carry **many** variables (`variableId` link + others), values cross, and the producer→consumer
  sequence is implicit (order-only). There is **no ordering enforcement** and **no visibility** into variable values.
- A "variable" should capture a value read from a **web element's text/label** (e.g. an account balance "15 chf")
  at a moment in execution, keep it in memory, and be reused later to **validate (CHECK)** or **SET** another input
  — possibly across pages/moments.

## Target model (proposed)
1. **One variable per instruction.** The instruction that "activates" the variable is its single **producer/owner**.
   No many-to-one, no crossing. Simple, sequential meaning.
2. **Activation flag** on an instruction: "capture as variable". When on, the instruction reads the web element's
   text and publishes a named variable into the execution's variable memory.
3. **Auto-naming:** `VAR-<instructionId>-<instruction name>` (stable, unique, traceable back to the step).
4. **Scope:** the bot job **execution** (all variables produced during a run). Later reads (CHECK/SET) reference a
   variable by name/id.
5. **Value tracking:** each variable keeps its **initial value** (the moment first read) and its **current value**
   (operations ahead may change it), so the panel can show initial → current history.

## Producer→consumer ordering (subsumes the #8 fix)
With one clear producer per variable, the rule is exact: **the producer instruction must have a lower execution
order than every consumer that reads its variable.** A move (drag or Memory List) that would place a reader before
its producer is **refused with an explanatory message** naming both steps ("'Get Value' (#7) must run before
'Extract Field' (#6) that reads its variable"). Enforced in `InstructionMoveValidator` as a new `validateVariableOrder`
pass. (Producers/consumers among SET/GET/CK/PDF CHECK/CSV CHECK/E — exact classification to confirm with user.)

## Variable Page (FE — its own page, like the Memory List page)
- A **new dedicated, floating, drag & drop page** — modeled on the existing **"memory list" page**, NOT a modal and
  NOT inside the command editor (the command editor should be broken down; variables are becoming too important to
  bury there). Opened from GridItem (button/link) the same way the memory list is.
- Lists **all variables in the bot job / current execution**. Each row: variable name (`VAR-id-name`), owning
  instruction (id + step name), **initial value**, **current value**, status. Clicking a row highlights/scrolls to the
  owning instruction in the grid.
- During a live run it updates as the Engine emits values; when **paused**, the user can **edit a variable's value**
  inline and **Resume** to continue `executeJob` exactly where it stopped. When idle it shows declared variables +
  last-known values.

## Phased roadmap (each phase: no behavior break, tests, user runtime-verify)
- **P0 — Model & migration:** enforce one-variable-per-instruction, add the activation flag + auto-name; DB migration
  (dated class under `db/migrations/`), keep engine DTO compatibility. (Backend + schema.)
- **P1 — Ordering enforcement (the #8 fix):** `validateVariableOrder` in `InstructionMoveValidator` + explanatory
  yes/why-not messages in both grids. (Backend + FE messaging.)
- **P2 — Variable Page (declared variables):** new floating drag & drop page (like the memory list page) listing the
  bot job's variables + owning instructions (no live values yet). (FE — break the command editor down.)
- **P3 — Execution value tracking + pause/edit/resume:** the Engine emits variable values during `executeJob`; the
  page shows initial→current, and on a **pause** the user edits a value + **Resume** continues exactly where it
  stopped. **Gated by Engine repo access** (separate artifact; source not in these repos — needs Engine pause +
  variable get/set hooks).

## Decisions — CONFIRMED (2026-07-26)
1. **One variable per instruction — YES, hard rule.** Migrate/drop existing multi-variable rows.
2. **Per-action semantics (rule to be MODIFIED):**
   - **GET** = PRODUCER. Purely reads a value from a web element → into a variable.
   - **SET** = CONSUMER. Puts a value into a web element that can receive it; the value may be a **literal** OR a
     **variable picked from the variable list**. (SET may also assign a variable directly.) When execution reaches
     the SET step it applies the value.
   - **Extract (E)** = rule changes: E may **connect to a variable, exclusively one produced by a GET** (consumer of
     a GET's variable). CK/PDF CHECK/CSV CHECK = consumers (read a variable to compare).
   - **Ordering rule:** the GET that produces a variable must run BEFORE any SET / E / CK / check that reads it.
3. **Variable UI = a NEW dedicated PAGE** (floating, drag & drop), built like the existing **"memory list" page** — NOT
   a modal, NOT crammed into the command editor. Rationale: variables are becoming a first-class "flow indication of
   values read across pages"; the command editor should be broken down and variables get their own page. It lists the
   bot job's variables and their owning instructions.
4. **Live values — YES, with pause/edit/resume:** during a run the panel shows values live; if execution is **paused**
   at a moment, the user can **edit a variable's value** in the floating panel and click **Resume** to continue
   `executeJob` exactly where it stopped. (P3 needs the Engine to expose pause + variable get/set at runtime.)

## Relationship to other work
- Subsumes task #8 (producer→consumer order). GridItemComp drag-not-working (#8) is a SEPARATE concrete bug — fix
  independently first (see investigation: componentTasks `memoryCapabilities` returns empty, likely stale
  `homeBankingId`).
- Related: Memory List central hub (#6), Scan-by-Word/Text (#5).

## 2026-07-27 - Component and Memory List integrity addendum

The Components investigation established that variables cannot be copied as isolated instruction
fields. A reusable Component block is one aggregate:

`component_block + component_instruction + component_variable + component_reference + relations`.

The canonical fixture is Components display-order block 18, `Check payment`
(`component_block.id = 36`). It contains 15 instructions, two variables, 25 locator references,
IF/ELSE/ENDIF, LOOP, PAUSE, GET, CK, and E.

### New rules

1. Root instructions may have a null `parent_id`; null must never be unboxed or treated as deletion
   corruption.
2. A whole Component block must enter the Memory List as one typed `BLOCK` item. Row-level
   capability filtering is not allowed to silently remove dependent instructions.
3. The authoritative backend reloads the source aggregate and transactionally remaps block,
   instruction, variable, reference, parent, parent-block, and GOTO IDs.
4. Variable producer/consumer ordering is validated on the final generated graph immediately
   before commit.
5. Deleting a producer or parent uses a confirmed transitive cascade and atomically removes
   dependent instructions, variables, and references.
6. Future component revisions must include variables, references, and block metadata so a stale
   source cannot pass an instruction-only revision check.

Detailed implementation phases and acceptance criteria are in
`migrations/ROADMAP_COMPONENT_MEMORY_VARIABLE_AND_MULTI_EXECUTION_2026_07_27.md`.

## 2026-07-27 - Phase 0A implementation checkpoint

Phase 0 has started with a read-only production audit and backward-compatible write guards. No
production database row was modified.

### Production baseline

| Metric | Bot Jobs | Components |
|---|---:|---:|
| Instructions | 665 | 201 |
| Variables | 22 | 5 |
| Variable-linked instructions | 32 | 8 |
| Variables with no consumers | 6 | 2 |
| Variables with multiple consumers | 14 | 2 |
| Missing/cross-owner variable links | 0 | 0 |
| Missing variable owner instructions | 0 | 0 |

Only one declaration violates the future one-variable-per-owner rule:

- Component instruction 44 owns variable 1 (`Order Number`) and variable 2 (`check value`).
- Variable 1 is used; variable 2 is unused.

Other repair candidates discovered:

- Component instruction 45 (`H`) has a stale `variable_id=1`, although Wait is not a variable
  command.
- Bot Job 18 instructions 190 GET, 191 E, and 192 CSV CHECK use variable 12 but lost their
  `parent_id`; their Component source instructions 196-198 correctly reference Web Field 195.
- 28 of 41 Bot Job dependent rows have no `parent_block_id`; their non-null `parent_id` targets
  remain valid.

No current parent-order or GET-before-E violation was found.

### Runtime compatibility decision

The current engine does **not** yet execute SET as a prior-GET consumer. SET writes its configured
literal value and then stores that literal in the runtime map. Therefore:

- GET is the runtime producer.
- E, CK, PDF CHECK, and CSV CHECK are current GET consumers.
- SET remains a variable-capable legacy literal writer during Phase 0.
- GET-before-SET enforcement is deferred until SET has an explicit `LITERAL` versus
  `VARIABLE_SOURCE` mode and the engine actually reads the selected variable.

This transitional rule prevents the new authoring validator from promising behavior that the
execution engine does not provide.

### Implemented in Phase 0A

- Added one Java-owned `VariableDefinitionPolicy` for producer, consumer, variable-command, and
  generated-name semantics.
- New declarations receive the stable default name
  `VAR-<instructionId>-<normalized instruction name>`.
- The Variable Editor/create-variable application path now refuses a second declaration for the
  same owner instruction in the same Bot Job/organization transaction.
- Existing-variable updates are bound to both the workspace owner and the selected Web Field, so
  a stale or forged variable ID cannot update another instruction's declaration.
- Dependent command loads and atomic rewrites are additionally bound to `variable_id`; editing one
  legacy duplicate cannot rewrite commands belonging to its sibling declaration.
- Existing duplicate declarations are left untouched; no destructive startup migration runs.
- This is an application-path guard, not yet a global database invariant: component copy/import
  paths remain audit targets until Phase 0B repairs legacy data and adds unique indexes.
- Row move validation now protects GET ordering before E/CK/PDF CHECK/CSV CHECK, including
  cross-block comparisons when authoritative block order is available.
- Memory List dependency normalization uses the same policy.
- Component Memory apply refuses E/CK/PDF CHECK/CSV CHECK selections that omit their matching GET
  producer, and rolls back without partial Bot Job rows.
- SET literal compatibility is explicitly covered.
- Focused result: 45 tests passed with zero failures/errors; the complete Maven suite was not run.

### Phase 0B - repair before constraints

1. Add a mutation-time `VariableGraphIntegrityService` for Bot Job and Component graphs.
2. Produce an explicit repair report and database backup.
3. Retain used variable 1 and remove unused duplicate variable 2 from Component instruction 44.
4. Clear the stale variable link from Wait instruction 45.
5. Repair Bot Job 18 command parents only where variable owner and Component provenance identify
   one unambiguous Web Field.
6. Backfill `parent_block_id` from each valid parent instruction.
7. Re-run the audit; ambiguous rows must be reported, never guessed.
8. Only after a clean audit, add unique indexes for `(bot_job_id, instruction_id)` and
   `(home_banking_id, instruction_id)`.

## 2026-07-27 - Phase P2 detached Variables workspace completed

Phase P2 now provides one independent `variablesManager` window scoped to the authoritative active
Bot Job. It opens from **Bot Job Details -> Variables**, participates in Pages Open, can be moved
between displays, and closes without closing the application or the Bot Job page.

### Relationship model delivered

- The backend loads declarations directly from the selected Bot Job and emits the declaration
  owner, all variable-linked commands, Blocks, typed edges, active/inactive state, and diagnostics.
- Canonical edges are `DECLARES`, `WRITES`, `READS`, `ASSIGNS_LITERAL`, and `INVALID_LINK`.
- GET is presented as the runtime writer/producer.
- E, CK, PDF CHECK, and CSV CHECK are presented as readers/consumers.
- Current SET compatibility is shown in a separate `SET -> declaration Web Field` lane. It is not
  presented as reading the variable because the current Engine writes a literal.
- The React page uses a searchable, collapsible variable tree and a selected-variable flow:
  declaration Web Field -> GET -> variable memory -> readers/checks.
- Runtime initial/current values are deliberately labelled unavailable. P3 remains responsible for
  execution value streaming, pause-time editing, and Resume.

### Integrity and lifecycle behavior

- Effective execution diagnostics use only active instructions in active Blocks, while inactive
  links remain visible for authoring.
- Diagnostics cover missing owners/producers, duplicate or multiple producers, consumers before
  GET, dangling variable links, invalid action links, and Block/owner mismatches.
- Persisted variable/instruction mutations queue an exact-Bot-Job realtime refresh; ordinary
  execution color/status traffic does not rebuild the graph.
- A fixed singleton page is retargeted instead of duplicated. Reload uses a binding generation and
  grace period; retirement sends a tombstone and has a forced-close fallback.
- Graph SQL, registry access, and WebSocket delivery do not run while holding the Variables state
  monitor, preventing the registry/Variables lock inversion identified during review.
- React correlates request IDs, rejects older workspace epochs and incomplete canonical graphs,
  accepts a same-workspace binding rotation, times out lost requests after 10 seconds, and keeps
  the last valid graph visible on failure.

### Verification and deployment

- Backend focused result: 22 tests passed, zero failures/errors
  (`VariableRelationshipServiceTest`, `VariablesWorkspaceServiceTest`,
  `VariablesWorkspaceAuthorizationTest`, `BotJobWorkspaceActionTest`, and
  `VariableDefinitionPolicyTest`).
- Frontend focused result: 29 tests passed across four suites, zero failures.
- `npm run build` completed successfully with pre-existing warnings and produced
  `main.f69dd91d.js` and `main.3451644a.css`.
- Frontend build deployment parity: 45 source files, 45 backend-resource files, zero relative-path
  or SHA-256 differences.
- Frontend source commit: `e05503e`.
- No production database row was modified by this phase.
