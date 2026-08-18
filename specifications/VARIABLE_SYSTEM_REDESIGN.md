# Variable System Redesign — historical baseline and canonical roadmap pointer

Updated 2026-07-29. This document now records implemented facts and historical audit results. It
is no longer the implementation roadmap.

## Canonical authority

All future variable, relationship, drag, reconnect, delete, execution-gate, schema, and rollback
work is governed by:

`migrations/ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`

The roadmap deliberately supersedes the earlier future-design rules that:

- refused every move that temporarily placed a consumer before its GET;
- treated connected or positional rows as an automatic delete cascade;
- required destructive removal of duplicate or ownerless variables at startup;
- made `parent_id` the permanent center of every variable relationship;
- enabled broken drafts before an execution-readiness gate existed.

Do not reintroduce those rules from historical commits or prose below. The working `+` Memory List
selection and fresh-ID copy behavior remain frozen.

## Approved direction

1. A variable is durable workspace memory. It may temporarily have no Web Element owner.
2. A variable has at most one owner Web Element in one exact Bot Job or Component owner.
3. Multiple compatible commands may refer to the same variable.
4. Relationships are explicit typed data and have independently computed health states.
5. React computes the exact visible-graph edit plan. Java validates owner, revision, expected old
   values, and structural integrity, then persists that exact plan atomically.
6. Valid single-row editing is introduced first. Persisted broken drafts are enabled only after
   reconnect exists and the backend execution-readiness gate is mandatory.
7. Delete offers exact selected, direct-explicit, and full-explicit modes. Positional IF/LOOP body
   rows are never inferred as deletions.
8. Current Engine semantics remain unchanged until they are traced and proven:
   GET is the runtime producer; E, CK, PDF CHECK, and CSV CHECK are current consumers; SET remains
   a legacy literal writer.
9. Runtime initial/current values and pause/edit/resume remain a separate Engine project.

## Variables workspace

The dedicated floating Variables page is already delivered. It remains the read-only relationship
and diagnostic surface until the canonical roadmap reaches its gated interactive phase. It must
keep the last valid graph visible on refresh or transport failure.

## Relationship to other work

- Components orchestration independence is tracked separately as BUG-002.
- Detached Memory List drag isolation runtime acceptance is BUG-003.
- Memory Apply to Bot Job Details realtime repaint coverage is BUG-004.
- Production-shaped variable/parent audit and later repair coverage is BUG-005.
- Memory List central hub and Scan-by-Word/Text remain related but do not change this roadmap.

## 2026-07-27 - Component and Memory List integrity addendum

The Components investigation established that variables cannot be copied as isolated instruction
fields. A reusable Component block is one aggregate:

`component_block + component_instruction + component_variable + component_reference + relations`.

The canonical fixture is Components display-order block 18, `Check payment`
(`component_block.id = 36`). It contains 15 instructions, two variables, 25 locator references,
IF/ELSE/ENDIF, LOOP, PAUSE, GET, CK, and E.

### Implemented baseline rules

1. Root instructions may have a null `parent_id`; null must never be unboxed or treated as deletion
   corruption.
2. A whole Component block must enter the Memory List as one typed `BLOCK` item. Row-level
   capability filtering is not allowed to silently remove dependent instructions.
3. The authoritative backend reloads the source aggregate and transactionally remaps block,
   instruction, variable, reference, parent, parent-block, and GOTO IDs.
4. Variable producer/consumer ordering is validated on the final generated graph immediately
   before commit.
5. The currently deployed exact delete/copy contracts remain characterized for compatibility.
   Future deletion follows the canonical selected/direct/full exact plans and never expands
   through positional IF/LOOP body rows.
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

### Former Phase 0B plan — superseded

The concrete repair candidates remain useful audit facts, but this older repair sequence must not
be executed. The canonical roadmap separates durable ownerless variables, uniqueness, explicit
repair, and historical constraints into independently backed-up and feature-gated phases. No
duplicate, stale link, missing `parent_block_id`, or ownerless declaration may be repaired merely
because it appears in this historical document.

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
