# ROADMAP 8 — Use Case Orchestrator (API ⨯ Bot Job)

**Status:** 📋 proposed
**Owner:** Osvaldo Martini
**Dependencies:** none functionally; integrates with MultiTest (`abr-react-ts-grid`) and the scanner DB (`bot_job`/`block`/`instruction` tables)
**Feeds into:** end-to-end business scenario testing, regression suites that mix UI + API
**Kickoff (proposed):** 2026-05-18
**Target v1 delivery:** 2026-07-20

---

## 1. Why

Today the platform owns two completely separate testing universes:

| World | Owns | Lives in | Executes via |
|---|---|---|---|
| **A — API Test Platform** | `ApiSpec` (OpenAPI/YAML/JSON) → `WorkflowGraph` → `DataGenerator` → `TestCase[]` queue → `Running` results | `abr-react-ts-grid` (MultiTest tabs) | `fetch()` from the WebView, env-targeted |
| **B — Bot Job** | `bot_job` → ordered `block[]` → ordered `instruction[]` (xpath / action / variable) | scanner DB (SQLite/Postgres/Access) | AR Web Engine (Selenium) against `home_url` |

There is no first-class concept that says *"login to the bank UI, then call POST /transfer with the account ID we just scraped, then verify the confirmation dialog appeared."* That is a real business scenario — what the user calls a **Use Case** — and it cannot be expressed today without manually orchestrating two disconnected tools.

This roadmap introduces a third layer that **composes** the two worlds:

- A `UseCase` is an ordered DAG of `Step`s.
- A `Step` is one of: `UiStep` (references a `bot_job` block or instruction range), `ApiStep` (references an `ApiSpec` operation + payload), `WaitStep`, `AssertStep`.
- A `VariablePool` flows between steps. Outputs of one step (HTTP response field, scraped DOM text, captured screenshot OCR) become inputs of the next.
- A graph editor lets the user draw the use case visually.
- A sequential runner walks the graph, dispatches each step to the right executor, and streams progress back to the editor.

## 2. Non-goals (v1)

To keep v1 shippable in ~9 weeks, the following are explicitly **out of scope** and parked for v2:

- Branching (if/else), loops, retries with logic. v1 is a **straight-line DAG**: linear or fan-out-then-rejoin only.
- Parallel step execution. v1 is **strictly sequential**.
- Mobile/Appium steps. (Lives in sibling repo `ar-web-mobile`; can be added later via the same `Step` abstraction.)
- AI-generated use cases. (AI Assistant tab is currently hidden per the latest UI changes.)
- Visual diff / screenshot comparison as a step type.
- Cross-environment use cases (a use case binds to one environment per run).
- Use case versioning / git-style history. v1 stores the latest version only; previous runs are immutable snapshots.

## 3. Decisions locked in

| Decision | Choice | Rationale |
|---|---|---|
| Persistence | New tables in the **same scanner DB** the bot jobs already live in. Migration class follows the `db/migrations/M<YYYYMMDD>_*.java` pattern. | Avoids a second DB; FK to `bot_job(id)` and reuse of the existing `MigrationRunner`. |
| Storage of API specs | A use case stores a **reference** (spec name + operationId) plus a **snapshot of the request template**. Re-importing the spec does not silently break old use cases. | Reproducibility. The platform keeps the as-imported request body for audit. |
| Step executor location | UI steps execute in **Java** (existing `PerformActions` / `executeJob` path). API steps also execute in **Java** (new lightweight `ApiStepExecutor` using JDK `HttpClient`). The React editor only authors and watches. | Single, deterministic runner; no CORS/scheme issues; one consistent log + screenshot pipeline. The React `fetch` path remains for the standalone API test queue. |
| Variable pool | A typed `Map<String, JsonNode>` per run, with explicit declared `inputs[]`/`outputs[]` per step. Captures use **JSONPath** (API responses) or **CSS/XPath + text/attribute** (DOM scrape). | Same JSONPath dialect MultiTest already uses for response assertions; same locator vocabulary the scanner already speaks. No new DSL. |
| Editor library | **Extend the existing hand-rolled SVG renderer** in `ApiWorkflowTab` for v1 (`curvePath`, draggable nodes). Re-evaluate `reactflow` for v2 if interactions grow. | Zero new deps; consistent visual language with the existing workflow tab. |
| Communication | New socket verbs over the existing `SimpleWebSocketServer` switch: `useCase.list`, `useCase.save`, `useCase.run`, `useCase.cancel`, plus an event stream `useCase.stepEvent`. | Reuses the established pattern. No HTTP. |
| Environment binding | A use case binds to **one** `home_banking_id` + `home_url_id` at run time. The same use case can be re-run against a different env without edits. | Mirrors how `bot_job` already binds to env. |
| Reuse of existing entities | UI steps reference **existing** `block` rows (not copies). API steps reference **`ApiSpec` payload templates** stored as JSON. Editing the underlying block changes future runs of any use case that references it. | Single source of truth. v2 can introduce pinning ("freeze block version X") if drift becomes a real problem. |
| Runner concurrency | One use case at a time per scanner instance (the scanner is already single-instance via `.ARWebScanner.lock`). | Avoids resource contention with the WebDriver session. |
| Delete cascade | Deleting a `bot_job` cascades to use case steps that reference it (CASCADE). The use case itself stays but is flagged `broken=1` until the user re-points the step. | Mirrors existing FK behaviour; no silent data loss. |

## 4. Domain model (new)

```text
use_case
  id                BIGINT PK AUTOINCREMENT
  name              TEXT UNIQUE NOT NULL
  description       TEXT
  home_banking_id   INTEGER  FK → home_banking(id)  ON DELETE SET NULL
  home_url_id       INTEGER  FK → home_url(id)      ON DELETE SET NULL
  active            INTEGER NOT NULL
  broken            INTEGER NOT NULL DEFAULT 0    -- set when a referenced block/spec was deleted
  created_at        TEXT
  updated_at        TEXT

use_case_step
  id                BIGINT PK AUTOINCREMENT
  use_case_id       BIGINT NOT NULL  FK → use_case(id)  ON DELETE CASCADE
  step_order        INTEGER NOT NULL
  name              TEXT NOT NULL
  step_type         TEXT NOT NULL          -- 'UI' | 'API' | 'WAIT' | 'ASSERT'
  -- UI step fields
  ref_block_id      INTEGER  FK → block(id)              ON DELETE CASCADE
  ref_instruction_from BIGINT  FK → instruction(id)      ON DELETE SET NULL
  ref_instruction_to   BIGINT  FK → instruction(id)      ON DELETE SET NULL
  -- API step fields (stored as JSON in payload_json for forward-compat)
  api_spec_name     TEXT
  api_operation_id  TEXT
  payload_json      TEXT    -- request body template + headers + path params
  -- common
  inputs_json       TEXT    -- ["accountId", "sessionToken"]
  outputs_json      TEXT    -- {"accountId": "$.data.id", "sessionToken": "$.headers.X-Session"}
  on_failure        TEXT NOT NULL DEFAULT 'STOP'   -- 'STOP' | 'CONTINUE'
  timeout_seconds   INTEGER
  active            INTEGER NOT NULL DEFAULT 1

use_case_run
  id                BIGINT PK AUTOINCREMENT
  use_case_id       BIGINT  FK → use_case(id)  ON DELETE CASCADE
  started_at        TEXT
  ended_at          TEXT
  status            TEXT     -- 'RUNNING' | 'PASSED' | 'FAILED' | 'CANCELLED'
  env_snapshot_json TEXT     -- frozen home_url + banking at run time
  variable_pool_json TEXT    -- final state of the pool

use_case_step_run
  id                BIGINT PK AUTOINCREMENT
  run_id            BIGINT  FK → use_case_run(id)  ON DELETE CASCADE
  step_id           BIGINT  FK → use_case_step(id) ON DELETE SET NULL
  started_at        TEXT
  ended_at          TEXT
  status            TEXT     -- 'PASSED' | 'FAILED' | 'SKIPPED'
  evidence_json     TEXT     -- screenshots paths, API request/response, captured vars
  error_message     TEXT
```

Edges between steps for v1 are implicit (`step_order`). When v2 introduces fan-out, an explicit `use_case_edge(from_step_id, to_step_id)` table will be added — backward-compatible.

## 5. Repository layout

### Java side — `ar-web-selenium`

```text
src/main/java/com/allinweb/ch/
├── model/
│   ├── UseCaseDTO.java                    NEW   (id, name, env, steps[], broken)
│   ├── UseCaseStepDTO.java                NEW   (type, refs, payload, inputs, outputs)
│   ├── UseCaseStepTypeEnum.java           NEW   UI | API | WAIT | ASSERT
│   ├── UseCaseRunDTO.java                 NEW   (status, env snapshot, var pool)
│   ├── UseCaseStepRunDTO.java             NEW   (status, evidence, error)
│   └── VariablePoolDTO.java               NEW   typed Map<String, JsonNode> wrapper
├── facade/
│   ├── PerformUseCase.java                NEW   singleton CRUD + load/save
│   ├── UseCaseOrchestrator.java           NEW   sequential runner; dispatches by step type
│   ├── ApiStepExecutor.java               NEW   JDK HttpClient; resolves vars; captures via JSONPath
│   ├── UiStepExecutor.java                NEW   wraps PerformActions / executeJob for a block range
│   ├── WaitStepExecutor.java              NEW   trivial; honours timeout_seconds
│   └── AssertStepExecutor.java            NEW   evaluates JSONPath / regex / equality on the pool
├── socket/
│   └── (extend) SimpleWebSocketServer.java +verbs: useCase.list/save/delete/run/cancel/stepEvent
├── db/migrations/
│   └── M20260518_UseCaseOrchestrator.java NEW   creates the four tables in §4
├── component/scene/
│   └── ARUseCaseScene.java                NEW   thin wrapper hosting the React tab when run from Java UI
└── util/
    ├── JsonPathBinder.java                NEW   resolve "${accountId}" → pool value; capture JSONPath → pool
    └── EvidenceWriter.java                NEW   centralised evidence JSON + screenshot path writer
```

### React side — `abr-react-ts-grid`

```text
src/components/MultiTest/
├── UseCaseTab.tsx                         NEW   top-level tab container (list + editor + runner)
├── useCase/
│   ├── UseCaseList.tsx                    NEW   left rail with saved use cases + create button
│   ├── UseCaseEditor.tsx                  NEW   the DAG editor (extends curvePath SVG approach)
│   ├── StepPalette.tsx                    NEW   drag source: API operations + bot-job blocks + utility
│   ├── StepInspector.tsx                  NEW   right rail: per-step inputs/outputs/payload editor
│   ├── VariablePoolPanel.tsx              NEW   live view of the pool during a run
│   ├── RunController.tsx                  NEW   start/pause/cancel + per-step status pills
│   └── useCaseStore.ts                    NEW   client-side store; mirrors socket events
└── App.tsx                                MODIFY  add { id: "usecase", ..., hidden: false } between report and library
```

Locale additions go in `MultiTest/locales/en.mt.json` (and the canonical `public/locales/en/mt.json`). Other languages get the English fallback automatically thanks to the bundled-fallback layer added in the previous task.

## 6. Phasing

| Phase | Duration | Deliverable | Success criteria |
|---|---|---|---|
| **0. Requirements & mockups** | 1 week | This doc + 3 wireframes (list / editor / runner) + a worked example use case in JSON | User can read the example JSON and explain back what would happen at each step |
| **1. Domain model + migration** | 1 week | `M20260518_UseCaseOrchestrator.java` runs cleanly on a fresh DB **and** a populated DB. New DTOs compile. Unit test loads/saves a hand-built use case round-trip. | `mvn test` green, DB inspected with sqlite3 shows expected schema |
| **2. UseCaseTab skeleton + list + create** | 1 week | New tab visible (between Report and Library). User can create a named, empty use case bound to the active bot job's env. Persists. Reloads on refresh. | Create → reload → see in list. No editor yet — just metadata. |
| **3. DAG editor — UI steps only** | 2 weeks | Drag a `block` from the bot-job palette onto the canvas; saves as a `UI` step. Connect steps with edges. Save graph → reload → identical layout. | A 3-step linear UI flow saves and reloads. |
| **4. API steps + payload editor** | 1 week | Drag an `ApiSpec` operation onto canvas. Inspector shows method, URL template, JSON body editor. Save/reload. | Add a POST step with a custom body, save, reload — body intact. |
| **5. Variable pool + bindings** | 1 week | Per-step `inputs[]` (var name) and `outputs[]` (var name → JSONPath OR CSS+attribute). Inspector autocompletes from declared outputs of upstream steps. | Pool diagram in `VariablePoolPanel` shows producer→consumer links. |
| **6. Sequential runner + live events** | 2 weeks | `useCase.run` over socket. Java orchestrator walks step_order, dispatches by type, emits `useCase.stepEvent` per state change. React updates per-step pill (queued/running/passed/failed) in real time. Run record persists. | Mixed UI+API 5-step use case runs end-to-end and the report row appears. |
| **7. Run history + report sheet** | 1 week | `Past Runs` panel under each use case (last 50 runs). Excel report gains a `UseCaseRuns` sheet (extends existing `ExcelWriter` chain — same pattern as ROADMAP_6 used for Coverage). | Excel export contains pass/fail and per-step durations for the last run. |

**Total: ~9 weeks** of focused work, single developer.

## 7. Worked example (the one we'll build first as the acceptance test)

Use case **"Transfer EUR 100 from main account"** against the Avaloq dev environment:

```json
{
  "name": "Transfer EUR 100 from main account",
  "homeBankingId": 3, "homeUrlId": 7,
  "steps": [
    { "order": 1, "name": "Login", "type": "UI",
      "refBlockId": 42,
      "outputs": { "sessionCookie": { "source": "cookie", "name": "JSESSIONID" } } },

    { "order": 2, "name": "GET /accounts", "type": "API",
      "apiSpecName": "avaloq-banking.yaml", "apiOperationId": "listAccounts",
      "payload": { "method": "GET", "path": "/accounts", "headers": { "Cookie": "${sessionCookie}" } },
      "outputs": { "mainAccountId": { "source": "jsonpath", "expr": "$.data[?(@.type=='MAIN')].id" } } },

    { "order": 3, "name": "POST /transfers", "type": "API",
      "apiSpecName": "avaloq-banking.yaml", "apiOperationId": "createTransfer",
      "payload": { "method": "POST", "path": "/transfers",
                   "headers": { "Cookie": "${sessionCookie}", "Content-Type": "application/json" },
                   "body": { "from": "${mainAccountId}", "amount": 100, "currency": "EUR" } },
      "outputs": { "transferId": { "source": "jsonpath", "expr": "$.id" } } },

    { "order": 4, "name": "Open Transfers UI page", "type": "UI",
      "refBlockId": 51 },

    { "order": 5, "name": "Verify confirmation dialog", "type": "ASSERT",
      "expr": "domText('//div[@id=\"confirmation\"]') matches 'Transfer .* successful'" }
  ]
}
```

The runner output a humans-readable timeline:

```
✓ 1.2s  Login                              (UI block #42)
✓ 0.4s  GET /accounts                      → mainAccountId=acc-7821
✓ 0.6s  POST /transfers                    → transferId=tx-9913
✓ 0.9s  Open Transfers UI page             (UI block #51)
✓ 0.2s  Verify confirmation dialog         "Transfer EUR 100 successful"
```

If a step fails, the orchestrator stops (default `on_failure=STOP`), persists evidence (HTTP body, screenshot, captured vars at the failure point), and emits a final `useCase.runEvent` with `status=FAILED`.

## 8. Open questions to confirm before kickoff

1. **Spec snapshotting strategy.** Do we store the full `ApiSpec` JSON in the use case (heavy but bulletproof for replay) or just the operationId + a hash check (light but breaks when the spec changes)? Recommendation: **store the resolved request template only** (path, method, body skeleton), not the entire spec. The graph editor still works against the loaded spec; the run uses the snapshot.
2. **Block range vs single block for UI steps.** Should one `UseCaseStep` reference exactly one `block` or a range `[from, to]` of instructions inside a block? Recommendation: **one block per step** for v1 (matches the bot job's natural granularity); range support in v2.
3. **Where does the env binding live — at use case level or per step?** Recommendation: **use case level** for v1 (one banking + URL per run). Per-step env override in v2 if cross-tenant testing becomes real.
4. **Should API steps also be runnable from the existing Ready-for-Test queue, or is the use case the new home?** Recommendation: **leave Ready-for-Test untouched.** The two coexist — API-only test sweeps stay in Ready-for-Test; orchestrated business scenarios live in Use Cases.
5. **Authentication injection.** Many APIs need a bearer token captured from a previous login API call OR from the UI session cookie. Is the variable-pool capture (JSONPath / cookie / scrape) enough, or do we need a first-class `Auth` step type? Recommendation: **variable pool is enough for v1**; promote to a step type only if we see the same login pattern in 5+ use cases.

## 9. What I need from you to start

- ✅ **Confirm v1 scope** (sections 2 + 3) is the right cut. Anything to add or drop?
- ✅ **Pick the worked example** in §7 — is "Transfer EUR 100" representative, or do you have a more important first scenario from the Avaloq DB?
- ✅ **Answers** (or "decide later") on the 5 open questions in §8.
- ✅ **Greenlight Phase 1** — once locked, the migration class + DTOs are the next concrete deliverable.

Once those four are settled, Phase 1 can start the day after.
