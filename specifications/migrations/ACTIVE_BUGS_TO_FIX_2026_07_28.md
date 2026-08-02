# Active Bugs to Fix — 2026-07-28

## CURRENT ROADMAP IN PROGRESS — Variables Command Editor Modal (2026-08-01)

Keep this section at the top of the shared CODEX/Claude ledger and update it in place whenever the
active roadmap changes. Do not delete or replace the historical incident and bug records below.

Active roadmap: `ROADMAP_VARIABLES_COMMAND_EDITOR_MODAL_2026_08_01.md`

Progress: **CE-1 through CE-7 complete; 3 steps remain.**

| Step | Status | Current result |
|---|---|---|
| CE-1 | Complete | Green editor eligibility is explicit and isolated from relationship controls. |
| CE-2 | Complete | Target Block and placement are independent from the Variables Block filter. |
| CE-3 | Complete | CANCEL, UPDATE, and COPY NEW action shell exists. |
| CE-4 | Complete | Typed LOOP, REFRESH_LOOP, and Wait editors are hydrated from the selected command. |
| CE-5 | Complete; Claude persistence review requested | UPDATE preserves the instruction ID and supports placement and cross-Block movement. |
| CE-6 | Complete; Claude persistence review requested | COPY NEW creates one fresh, disconnected instruction and preserves the original. |
| CE-7 | Complete; runtime review pending | Typed CheckValue, CSV/PDF Check, and ExcelWrite editors use a new shadow configuration table while legacy execution fields remain unchanged. |
| CE-8 | Pending | GOTO and SWIPE editors. |
| CE-9 | Pending | Typed IF/ELSEIF editors after expression persistence is approved. |
| CE-10 | Pending | Acceptance, deferred tests, build/deployment verification, and realtime checks. |

### Claude review handoff — persistence-sensitive commits

- Frontend UPDATE transport: `2a22eed` (`CODEX: connect Variables command updates`).
- Backend UPDATE persistence: `f273119f` (`CODEX: persist Variables command updates`).
- Frontend COPY NEW transport: `05e6c22` (`CODEX: connect pure Variables command copy`).
- Backend COPY NEW persistence: `1259f18b` (`CODEX: persist pure Variables command copy`).
- Latest deployed bundle: `c9a9395b` (`CODEX: deploy pure Variables command copy`).
- CE-7 frontend: `ed300a0` (`CODEX: add typed Variables check and output editors`).
- CE-7 backend: `88628393` (`CODEX: persist typed Variables command configuration`).
- CE-7 deployed bundle: `f482756a` (`CODEX: deploy Variables CE-7 editors`).

The user is correct that CODEX touched persistence. Claude should review the two backend source
commits, especially transaction boundaries, graph version/revision checks, order normalization,
generated-ID handling, source-row preservation, and relationship clearing. COPY NEW intentionally
sets `variable_id`, `parent_block_id`, and `parent_id` to `NULL`; it does not clone variable
ownership or relationship/reference rows. No Maven or backend tests were run by CODEX at the user's
request. The frontend production build completed with the repository's existing warnings.

Protected scope: the detached legacy Command Editor, existing Memory List copy flow, GridItem drag
rules, and legacy command persistence were not replaced by CE-5/CE-6. Continue only after the user
accepts Claude's review.

### Adjacent requested improvements — implemented; runtime acceptance pending

- **AV-1 Add Variable:** prefill the next free case-insensitive `Variable_X` name as a real input
  value; add a mini `ADD` staging action for multiple names; `CREATE VARIABLE` persists the batch,
  refreshes the snapshot, advances the suggestion, and keeps the modal open until explicit Close.
- **AV-2 Release Connections:** add the bidirectionally synchronized Block filter used by Resolve
  and Review; empty selection releases all, one Block releases only that Block's source-instruction
  connections, and the modal must open even when the initial scope contains zero connections.

Implementation commits: frontend `f92c3d5`; scoped backend case-insensitive independent-name guard
`771a79d8`. The frontend production build passed and was deployed. No Maven or test suite was run.

Full acceptance details are recorded under “Adjacent Variables improvements” in
`ROADMAP_VARIABLES_COMMAND_EDITOR_MODAL_2026_08_01.md`. These tasks remain outside CE-7–CE-10 and
are now awaiting user runtime acceptance; they remain outside CE-7–CE-10.

This is the shared CODEX/Claude coordination list for the current Bot Job Details,
Components, and Memory List stabilization work.

## INCIDENT RESOLVED 2026-07-31 — Variables "total blockage" (CLAUDE root-cause + restore)

**Symptom:** free instruction drag & drop and the "Reconnect Variable" / "Reconnect Web Element"
buttons appeared completely blocked, with no notice, starting at Java `d2a3f517` (FE `17648f7`).
Three CODEX enablement patches (`7925c34`, `cb31c67`, `5395f92`, `10f156e`) did not recover it.

**Root cause (evidence-based):** the backend NEVER refused anything. The constant 517-byte
`graphMutationV3Response` messages are the SUCCESS shape (byte-length reconstruction proves it), and
`instruction_graph_state` advanced ~50 versions in step with the user's clicks — every gesture was
COMMITTING invisibly. The regression is the FE gating layer of `17648f7` (+4,303 lines in one
commit): silent `submit()` null-returns, all-or-nothing capability normalization, and per-action
enablement rules swallow both the gestures and their outcomes. Database integrity was verified clean
(orders, parents, variable owners, GOTO targets — zero corruption).

**Fix applied (minimal):** FE variables surface restored exactly to `49f1ee0` (the last
user-validated working state) in FE commit `0795468`; bundle `main.55a6c715.js` deployed
(`resources/build`). Java stays at HEAD — `d2a3f517`'s `variableFacts` exposure is additive and the
restored FE ignores it. `RulesCard` keeps its HEAD additive API. Nothing outside the variables
surface changed.

**Known inherited test debt at the restored state (NOT runtime regressions):** 4
`variablesWorkspace.contract` fixture tests + 1 stale `variablesGraph` expectation fail identically
with unchanged dependencies; runtime was user-validated.

**Re-landing rule for the `17648f7` features (repair modal, execution-flow review, command badges):**
one small commit at a time, each with user runtime acceptance, and NO silent gating — every refused
gesture must display its reason (now codified in CLAUDE.md "UI gating rules").

**Data note:** because the "blocked" clicks actually committed, the active job's layout may differ
from what the user remembers; that is surfaced history, not new corruption.

## DELIVERED 2026-07-31 (afternoon) — Relationship chip design system (CLAUDE, user-accepted)

Runtime-accepted by the user on both surfaces (GridItem grid rows AND the Variables command
board). FE commits `c12fa69` → `69f26e0` → `0f52397` → `52ee6c6`; latest deployed bundle
`main.ad3bf0eb.js` (Java deploy `182bb6a2`).

**The chip contract (standing design — apply to any future relationship UI):**

1. **Broken = RED clickable chip, everywhere.** Every relationship a command carries via
   `parent_id` / `parent_block_id` / `variable_id` shows a red RulesCard chip when broken, with a
   kind-aware label: `Reconnect Parent`, `Reconnect Loop`, `Repair Conditional`,
   `Reconnect Block`, `Reconnect Variable`. No orange/pale broken states.
2. **Connected = styled chip that KEEPS its color and carries the target name:**
   `Parent connected (id: 1646) <name>`, `Loop connected (id: 917) Pagina iniziale`,
   `Block connected (id: N) <block name>`. Previously connected LOOP/Conditional/Block showed no
   chip at all.
3. **The chip is the SINGLE parent display.** The legacy operation-text fragments
   (`Jump To Parent (N/A)Unknown`, GET/SET parent prefixes) were removed from GridItem — the
   `(N/A)Unknown` text can never render again. Operation values (Time/Loop counts, checks) stay.
4. **Every chip opens the shared reconnect dialog in BOTH states** (connect when broken;
   change target / disconnect when connected). The GridItem mutation builder
   (`instructionRelationshipMutation.ts`) now builds exact v3 patches for ALL kinds:
   LOOP_ANCHOR / CONDITIONAL_ROOT (instruction target) and BLOCK_TARGET (parentBlockId only) —
   previously only ELEMENT_TARGET / VARIABLE_BINDING, which caused
   "LOOP_ANCHOR cannot be changed from this instruction badge".
5. **State scoping:** structural kinds join the chip pathway only when CONNECTED or broken;
   FIX_ORDER / SAVING / REFUSED keep their own dedicated chips (FIX_ORDER stays amber).
6. **Removed as redundant:** the frontend-only `SOURCE DEFINED / VOID` badge on grid rows
   (`InstructionVariableStateBadge` deleted; zero Java references). The Variables page runtime
   panel wording is unchanged.

**Housekeeping:** CODEX's in-progress, unwired ExecutionFlowReview files are preserved in
`d1c4cf6` (nothing imports them; zero build impact).

**Known pre-existing test baseline (NOT regressions, verified via stash runs):**
`variablesGraph`, `variablesWorkspace.contract` (4 fixtures), `GridItemComp.memoryParity`
(5 delete-plan fixtures), `instructionMove` fixtures, plus the older OCR/Activation/App suites.

## File ownership rule

Before changing a bug, claim its files in this table. Work on a separate branch or
worktree when another terminal owns overlapping files. Do not build/deploy shared
frontend artifacts until the owning change has been reviewed and committed.

| ID | Priority | Area | Status | Owner |
|---|---|---|---|---|
| BUG-001 | Critical | Instruction deletion selects positional IF/LOOP body rows | Automated/deployment verification passed; manual disposable-copy acceptance pending | CODEX |
| BUG-002 | Critical | GridItemComp still shares branching Bot Job orchestration instead of independent typed Component calls | Pending | Unclaimed |
| BUG-003 | Critical | Memory List drag/drop must remain isolated from GridItem and GridItemComp drag/drop controllers | Implemented; automated regression passed; three-window manual acceptance pending | CODEX verification only |
| BUG-004 | High | Memory Apply and Bot Job Details refresh/realtime synchronization need end-to-end regression coverage | Partially covered; complete end-to-end regression pending | Unclaimed |
| BUG-005 | High | Variable producer/consumer and parent-reference repair need production-data audit coverage | P0 sanitized audit captured; repair coverage remains pending | CODEX P0 documentation only |
| BUG-006 | High | Separate deletion-modal correction in another terminal | In progress | Claude terminal |
| BUG-007 | Critical | Configured external Engine PDF/CSV CHECK reads an uninitialized validation context | P1 verified; runtime correction pending | Unclaimed after CODEX audit |
| BUG-008 | Critical | GET/SET bypass the mandatory Playwright-first/one-attempt path in Scanner and Engine | P1 verified; runtime correction pending | Unclaimed after CODEX audit |
| BUG-009 | Critical | Scanner and configured Engine implement forward GOTO differently | P1 verified; parity correction pending | Unclaimed after CODEX audit |
| BUG-010 | High | Classic Pre-Launch can continue after definition-load failure | P1 verified; P4 execution-gate correction pending | Variable roadmap P4 |

## P0 file ownership claim — 2026-07-29

This claim covers baseline documentation and deterministic test fixtures only. It does not claim
runtime implementation for BUG-002, BUG-004, or BUG-006.

| Work | Owner | Files | Runtime behavior |
|---|---|---|---|
| Variable-graph P0 baseline | CODEX | `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`, `../VARIABLE_SYSTEM_REDESIGN.md`, `P0_VARIABLE_GRAPH_BASELINE_2026_07_29.md` | None |
| Sanitized audit and fixture | CODEX | `../../scripts/p0/ReadOnlyInstructionGraphAudit.java`, `../../src/test/resources/fixtures/instruction-graph/*`, `../../src/test/java/com/allinweb/ch/testsupport/GoldenInstructionGraphFixtureTest.java` | None |
| BUG-003 acceptance record | CODEX verification only | This document and the P0 baseline | None unless acceptance exposes a separately claimed defect |

## P1 file ownership claim — 2026-07-29

P1 is a no-runtime-change execution audit. It claims only its specification and focused
characterization test; it does not claim implementation of BUG-007, BUG-008, or BUG-009 in the
external Engine repository.

| Work | Owner | Files | Runtime behavior |
|---|---|---|---|
| Execution semantics and entry-point audit | CODEX | `ROADMAP_VARIABLE_CENTRIC_INSTRUCTION_GRAPH_2026_07_29.md`, `P1_ENGINE_EXECUTION_SEMANTICS_2026_07_29.md` | None |
| Persisted command characterization | CODEX | `../../src/test/java/com/allinweb/ch/facade/ExecutionCommandSemanticsCharacterizationTest.java` | None |

## BUG-001 — Exact React-owned instruction deletion

### Required behavior

- React/TypeScript calculates the complete delete plan from the graph currently
  rendered to the client.
- An instruction is never selected merely because its order lies between IF/ELSE/
  ENDIF or a LOOP anchor/boundary.
- Parent relationships are block-local.
- Variable producer-to-consumer relationships may cross Blocks inside the same
  owner.
- GOTO and EXCEL GOTO references never become ownership cascades.
- The modal rows, WebSocket `deleteInstructionIds`, and database-deleted IDs are
  exactly the same ordered set.
- React explicitly sends surviving `parentId` repairs.
- Java validates request/revision/owner integrity and persists the exact submitted
  IDs and repairs. Java does not infer or expand the semantic group.
- No hidden EXCEL GOTO instruction deletion is permitted.

### Verification

- [x] React pure planner tests cover conditional boundaries, LOOP relationships,
      ordinary descendants, navigation isolation, cross-Block variable consumers,
      and parent repairs.
- [x] React integration test proves modal IDs equal WebSocket IDs.
- [x] Java contract tests cover exact ordering, owner isolation, duplicates,
      selected-ID inclusion, version enforcement, and explicit repairs.
- [x] Instruction-delete confirmations show detailed rows only up to five;
      larger exact plans show a count while preserving the complete v2 request.
- [x] Shared confirmation content is viewport-bounded and scrollable while its
      footer and Confirm/Cancel controls remain outside the scrolling region.
- [x] Java no longer emits instruction-level `canDelete`, `deleteCount`, or
      `deleteRows` UI planning fields. React derives delete readiness from
      synchronized graph coverage; Java retains v2 validation and persistence.
- [x] Dead legacy single-row deletion entry points were removed so the strict
      exact-plan transaction is the only active instruction-deletion path.
- [ ] Manual production verification on a disposable Bot Job copy.

## BUG-003 — Three private drag boundaries

The implementation has three separately named boundaries:

1. Bot Job transport: `useInstructionDrag` -> `ROW_MOVE` / `botJobTasks`.
2. Component transport: `useComponentInstructionDrag` -> `COMPONENT_ROW_MOVE` /
   `componentTasks`.
3. Detached Memory List controller: `useMemoryListDrag` -> one
   `memoryList.command` / `REORDER`.

The first two are private transport submitters. Their native row sensors, optimistic state, and
confirmation state live in separately mounted `useGridData` instances. The Memory List hook owns
its complete native drag lifecycle.

### Manual acceptance still required

- [ ] Open Bot Job Details, Components, and Memory List against a disposable owner.
- [ ] Leave a Bot Job move confirmation pending, reorder Memory List, and prove neither Components
      nor Memory List completes or resets the Bot Job move.
- [ ] Repeat with a Component move confirmation and prove only one
      `COMPONENT_ROW_MOVE` / `componentTasks` payload is emitted.
- [ ] Reorder Memory List and prove exactly one `memoryList.command` / `REORDER` payload contains
      the current owner epoch and complete stable item-key order.
- [ ] Prove each window exposes only its own diagnostic (`__gridReorder`,
      `__componentGridReorder`, or `__mlReorder`).
- [ ] Record all three before/after orders and outbound payloads without customer row data.

## Next execution order

1. Manually verify BUG-001 on a disposable Bot Job copy.
2. Complete the BUG-003 three-window manual isolation acceptance.
3. Claim and separate GridItemComp orchestration and contracts (BUG-002).
4. Claim and add the missing full Memory Apply/realtime repaint regression (BUG-004).
5. Use the P0 audit as the baseline for BUG-005 repair coverage; do not repair production rows
   before the variable-centric roadmap reaches its gated data phases.
