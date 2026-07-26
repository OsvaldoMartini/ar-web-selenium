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

## Floating Variable Panel (FE, non-modal)
- A **button on GridItem** opens a **floating, non-modal** panel (draggable, dismissible) listing **all variables in
  the bot job / current execution**. Reuse the Memory-List floating pattern where sensible.
- Each row: variable name (`VAR-id-name`), owning instruction (id + step name), **initial value**, **current value**,
  and status. Clicking a row could highlight/scroll to the owning instruction in the grid.
- During a live run it updates as the Engine emits values; when idle it shows the declared variables + last-known
  values.

## Phased roadmap (each phase: no behavior break, tests, user runtime-verify)
- **P0 — Model & migration:** enforce one-variable-per-instruction, add the activation flag + auto-name; DB migration
  (dated class under `db/migrations/`), keep engine DTO compatibility. (Backend + schema.)
- **P1 — Ordering enforcement (the #8 fix):** `validateVariableOrder` in `InstructionMoveValidator` + explanatory
  yes/why-not messages in both grids. (Backend + FE messaging.)
- **P2 — Floating Variable Panel (declared variables):** button on GridItem → floating panel listing the bot job's
  variables + owning instructions (no live values yet). (FE.)
- **P3 — Execution value tracking (initial + current):** the Engine emits variable values during `executeJob`; panel
  shows initial→current. **Gated by Engine repo access** (separate artifact; source not in these repos).

## Open decisions (confirm before P0)
1. **One variable per instruction** — confirm hard rule (drop multi-variable support / migrate existing multi-var rows).
2. **Producer/consumer classification** — is `Extract Field` (E) a **consumer** of a `GET`'s variable (must be after
   GET), a producer, or both? Exact per-action rule for SET/GET/CK/PDF CHECK/CSV CHECK/E.
3. **Panel placement** — a button on GridItem opening the floating panel (recommended) vs a docked side panel.
4. **Live values (P3)** — do we have Engine source / a way for the Engine to emit variable values at runtime? If not,
   P3 is limited to scanner-side execution (`ScannerRuntimeBackend.executeJob`) or deferred.

## Relationship to other work
- Subsumes task #8 (producer→consumer order). GridItemComp drag-not-working (#8) is a SEPARATE concrete bug — fix
  independently first (see investigation: componentTasks `memoryCapabilities` returns empty, likely stale
  `homeBankingId`).
- Related: Memory List central hub (#6), Scan-by-Word/Text (#5).
