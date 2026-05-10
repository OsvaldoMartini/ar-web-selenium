# ROADMAP 9 — Flow Authoring v1

**Status:** 📋 approved for build (authoring scope only)
**Owner:** Osvaldo Martini
**Relationship to other roadmaps:** This is the **first concrete sliver** of ROADMAP_8 (Use Case Orchestrator). v1 ships authoring + persistence; the executor and backend value-substitution into Excel / extracted data are explicitly **deferred** pending separate planning by Osvaldo.
**Kickoff:** TBD (next session)
**Target v1 delivery:** ~6 working days once started

---

## 1. The locked-in mental model

The bridge between API and bot-job worlds is two layers:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  MAPPING        │     │  FLOW           │     │  EXECUTION      │
│  (the alphabet) │ ──> │  (the sentence) │ ──> │  (the speech)   │
│                 │     │                 │     │                 │
│  "API field X   │     │  "Step 1 ▶ API  │     │  Run the flow,  │
│   ↔ bot field Y"│     │   Step 2 ▶ UI   │     │   substitute    │
│                 │     │   Step 3 ▶ ..."  │     │   captured vars,│
│                 │     │                 │     │   write into    │
│                 │     │                 │     │   Excel / DOM"  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
   built today              build in v1            DEFERRED
   (FunctionalTestTab)      (this roadmap)         (next planning)
```

### Worked example (acceptance test for v1 authoring)

The user must be able to author and save this exact flow in the Flow tab:

```
┌─────────────────────────────────────────────────────────────────┐
│  Flow: "Transfer EUR 100"                                       │
│                                                                 │
│  Step 1   ▶ API   POST /login                                   │
│              captures →  $.token              into  ${sessionToken}
│                                                                 │
│  Step 2   ▶ API   GET  /accounts                                │
│              header  ←  ${sessionToken}                         │
│              captures →  $.data[0].id         into  ${accountId}
│                                                                 │
│  Step 3   ▶ UI    Bot block "Transfer Form"                    │
│              field "I:IBAN"      ← ${accountId}    (from MAPPING)
│              field "I:Importo"   ← synth(100, "EUR")            │
│                                                                 │
│  Step 4   ▶ UI    Bot block "Verify Confirmation"              │
│              assert dom contains ${accountId}                   │
└─────────────────────────────────────────────────────────────────┘
```

In v1: the user can **author** this. Save → reload → identical reproduction. **It does not execute yet.**

### Variable pool — direction matters

- `captures` (forward producer): after the step completes, write `<jsonpath or selector result>` into the named pool variable.
- `substitutes` (backward consumer): before the step runs, read named pool variables and inject them into headers / body / instruction defaults.
- A step can do both. The pool is the shared memory across one flow run.

### How mappings auto-suggest substitutions in the Flow editor

When the user adds a UI step that references a bot block, the Flow editor:
1. Looks up `use_case_field_mapping` rows for that bot job.
2. For each `I:*` instruction in the chosen block that has a mapping, pre-populates a substitution row: `<bot field> ← ${apiField:<mapped api field name>}`.
3. The user can override the source (e.g. point at a synth value instead) or remove the suggestion.

Mappings are NOT silently applied at execution time. They are **author-time suggestions only.** The Flow's persisted `flow_step` rows hold the explicit substitution list — what executes is what the user saw and saved.

## 2. Decisions locked in for v1

| Decision | Choice | Rationale |
|---|---|---|
| Mapping sets get **named groups** | A new `use_case` table parents `use_case_field_mapping`. Existing rows migrate into a default `"Default"` use case per bot job. | One bot job → many named mapping sets ("Login", "Transfer", "Verify"). FunctionalTestTab gains a use-case dropdown at the top. |
| Flow lives at bot-job scope | A `flow` row has FK to `bot_job(id)` (CASCADE). One bot job → many flows. | Mirrors how the user already organises work. |
| One flow → ordered linear steps | `flow_step` has `step_order INTEGER`. No branching, no parallel, no fan-out in v1. | Smallest viable shape. ROADMAP_8 v2 introduces a `flow_edge` table when branching is needed. |
| Step types in v1 | `API` \| `UI` \| `WAIT` \| `ASSERT`. | Matches the worked example. |
| Step payload storage | One `payload_json TEXT` column per `flow_step`. Stores the type-specific shape (URL template, headers, captures, substitutions, refs, etc). | Schema-flexible. We don't add columns every time a step type gains a knob. |
| Persistence model | Same scanner DB. New migration `M20260511_FlowAuthoring.java`. Same `MigrationRunner` pattern. | Consistent with M20260510_FieldMapping. |
| Authoring UI | Vertical card stack, top-to-bottom, with `▲ ▼ ✕` per step and a `+ Add step` row. NO graph editor / SVG. NO drag-and-drop. | Smallest UI that lets the user prove the model works. Graph view goes in v2 only if it's worth it. |
| Editor side panel | Click a step card → right-side inspector opens with: type-specific payload editor (method/URL for API; block picker for UI; seconds for Wait; expression for Assert), captures table, substitutions table. | Same shape as the existing FunctionalTestTab card-and-detail pattern. |
| Backend execution | **OUT OF SCOPE.** No Java executor, no JDK HttpClient calls, no PerformActions wiring, no Excel substitution, no DOM injection. | Explicit user decision: needs separate planning before any wiring. |
| What "Save" does | Persists the flow + steps + payload JSON to DB. Reloading the same bot job + flow shows identical state. Pure CRUD. | Enough to validate the model end-to-end at the data level. |

## 3. What v1 delivers (concrete deliverable list)

### Java side

1. **Migration `M20260511_FlowAuthoring.java`** — creates three tables, all FK-cascading, dialect-aware (Postgres / SQLServer / SQLite / Access):
   ```
   use_case               (id, bot_job_id, name, description, created_at, updated_at)
   flow                   (id, bot_job_id, name, description, created_at, updated_at)
   flow_step              (id, flow_id, step_order, name, step_type, payload_json, created_at, updated_at)
   ```
   Plus one column added: `use_case_field_mapping.use_case_id` (NULLABLE, FK to `use_case(id)` SET NULL on delete — so deleting a use case un-groups its mappings rather than nuking them). Existing rows get bulk-migrated into a default `"Default"` use case per bot job in the same migration.

2. **DTOs** — `UseCaseDTO`, `FlowDTO`, `FlowStepDTO` (Lombok `@Data`).

3. **`PerformDataBase` methods** —
   - `loadUseCases(botJobId)`, `saveUseCase(dto)`, `deleteUseCase(id)`
   - `loadFlows(botJobId)`, `saveFlow(dto)`, `deleteFlow(id)`
   - `loadFlowSteps(flowId)`, `saveFlowSteps(flowId, list)` (delete-then-insert in one tx, same pattern as `saveFieldMappings`)

4. **`SimpleWebSocketServer` early-switch verbs** —
   - `useCase.list`, `useCase.save`, `useCase.delete`
   - `flow.list`, `flow.save`, `flow.delete`
   - `flow.steps.load`, `flow.steps.save`
   Same lightweight pattern as `botJob.getInputInstructions` / `funcTest.*`.

### React side

5. **`FunctionalTestTab.tsx` updates** — add a use-case dropdown at the top: "Default" / + New / Rename / Delete. Mapping save/load now keyed by `(bot_job_id, use_case_id)`.

6. **NEW `MultiTest/FlowTab.tsx`** — the new tab. Three regions:
   - **Left rail:** list of flows for the active bot job + ➕ New / 🗑 Delete.
   - **Centre:** vertical stack of step cards for the selected flow. Each card shows step number, type icon, name, one-line summary. `▲ ▼ ✕` controls.
   - **Right inspector:** opens when a card is clicked. Type-specific payload editor + captures table + substitutions table. Substitution suggestions pulled from the mapping table for the active use case.

7. **NEW `MultiTest/flow/` folder** — `types.ts`, `useFlowSocket.ts`, plus per-step-type editor components (`ApiStepEditor`, `UiStepEditor`, `WaitStepEditor`, `AssertStepEditor`).

8. **`MultiTest/App.tsx`** — insert `{ id: "flow", l: "🔀 Flow", hidden: false }` between `functest` and `ready`. Update `AppState.tab` union.

9. **Locales** — add `nav.flow` and the `flow.*` namespace to `public/locales/en/mt.json` and `MultiTest/locales/en.mt.json`.

## 4. Phasing inside v1

| Phase | Days | Visible result |
|---|---|---|
| 1a. Migration + use_case parent + name dropdown in FunctionalTestTab | 1.5 | User can create named mapping sets ("Default", "Transfer", "Login") and the existing mapping UI works under each. |
| 1b. Flow / flow_step migration + DTOs + DB methods | 1 | Schema in place; no UI yet. |
| 2a. Flow tab skeleton — list, create, rename, delete | 1.5 | User sees the new tab between FuncTest and Ready, can create empty named flows. |
| 2b. Step cards + reorder + add/delete | 1 | User can build a 4-step linear flow visually; persists. |
| 2c. Step inspector — type-specific editors + captures + substitutions | 2 | User can author the **Transfer EUR 100** worked example end-to-end and reload it identically. |

**Total: ~7 working days.** (Slightly above the ~6 day estimate I gave you — the use_case naming layer adds ~1 day that I underestimated earlier.)

Each phase can ship independently — if we stop after 1a + 1b, you have named mappings and the Flow schema is ready for whenever you decide to resume.

## 5. What is explicitly DEFERRED (next planning conversation)

These are NOT in v1. Listed here so they don't get forgotten:

- **The executor.** Java sequential walker that reads a Flow, resolves variables, dispatches API calls (JDK `HttpClient`), and writes resolved values into bot-job instruction `default_value` before invoking `PerformActions`.
- **Live UI feedback during execution.** Per-step status pills, captured-variable display, error stack on failure.
- **Backend value substitution into Excel / extracted data.** When a Flow's UI step needs a value that should be written into an existing Excel export or into the scanner's extraction-data buffers, where does that write happen? What's the file lifecycle? The user has flagged this needs separate design — do not start without that conversation.
- **Ready-for-Test integration.** "Pick a flow, run it, watch the timeline." Logically the next step after the executor exists.
- **Mock data sources for variables.** A way for the user to define `${randomIban}`, `${todayDate}` etc. that aren't sourced from API responses. Today's `synth(...)` placeholder in the worked example is just notation — there is no implementation.
- **Cross-flow variable export.** "Flow A captures `${customerId}`; Flow B starts with `${customerId}` already populated." Out of scope for v1.

## 6. Open questions to answer BEFORE the executor is built (Phase 3+)

The user has parked these explicitly. Capturing them so the conversation is easy to restart:

1. **Excel substitution lifecycle.** When a flow needs to write a captured variable into an Excel file the bot job uses as input, do we (a) edit the existing file in place, (b) clone it per run, (c) generate a new file just for the run? What about concurrent runs?
2. **Extracted-data scope.** What other "extracted data" buffers exist beyond Excel? OCR results? Screenshots metadata? Need a list.
3. **Failure semantics per step.** On API step failure, does the flow stop, retry, or skip? Per-step config or flow-level default?
4. **Authentication propagation.** Most flows will start with a login step that produces a token. Should there be a built-in "Auth" step type, or is the variable-pool capture pattern enough?
5. **Re-entrancy.** Can the same flow be running twice in parallel against different bot jobs? If yes, the variable pool needs scoping per run-id (it does anyway).
6. **What happens if a referenced bot block / API spec was deleted between authoring and execution?** Hard fail at flow load, or a "broken steps" indicator?
7. **Logging and audit.** Per-run log of every variable's value at the moment of substitution — for compliance / debugging? Where does it persist?

## 7. Stop point for v1

When the user can do this without help:

1. Open a bot job in the scanner.
2. Open the API Tool → Functional Test tab → see the "Default" use case → create a new "Transfer" use case → switch to it → map IBAN ↔ I:IBAN → save.
3. Switch to the new Flow tab → create a flow named "Transfer EUR 100" → add 4 steps matching the worked example → save.
4. Refresh the WebView → reopen the same flow → see the identical 4 steps with the identical substitutions and captures.
5. Hand wave at the right inspector saying "now I just need to push Run."

That's it. Nothing executes. Everything that would let it execute lives behind §5 + §6 and is the next planning conversation.
