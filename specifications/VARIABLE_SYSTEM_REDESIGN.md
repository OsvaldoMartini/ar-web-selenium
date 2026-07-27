# Variable System Redesign — design & roadmap (planning — NOT started)

Captured 2026-07-26 from user. Big, cross-cutting feature. Plan first, build in phases, confirm before code.

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
