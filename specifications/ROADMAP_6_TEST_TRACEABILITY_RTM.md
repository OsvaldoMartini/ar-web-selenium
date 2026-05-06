# ROADMAP 6 — Test Traceability (Requirements ↔ Test coverage)

**Status:** 📋 proposed
**Owner:** Osvaldo Martini
**Dependencies:** none (additive — does not modify existing job execution paths)
**Feeds into:** future test-management dashboard, audit/compliance exports
**Kickoff:** 2026-05-06
**Target final delivery:** 2026-06-05

## Why

The scanner today is purely a *test authoring + execution* tool. There is no concept of a **Requirement** entity, no link between bot jobs and the requirements they cover, and no coverage report. Stakeholders asking "which requirements are tested? which are not? which broke in the last run?" have no answer in the product.

This roadmap adds a minimal but complete **Requirements Traceability Matrix (RTM)** layer:

- a Requirement entity (id, external ref, title, source)
- a many-to-many link between Requirements and `BotJobLoadDTO`
- optional per-block tagging (Block ↔ Requirement) for finer granularity
- a Coverage Report sheet appended to the existing Excel execution report
- CSV import (Jira / Azure DevOps / generic) so requirements aren't hand-entered

The implementation is **additive only** — `BotJobLoadDTO` gains an optional `requirementIds` field, and execution paths (`ARScannedElementPane.executeJob`, `PerformActions`) are unchanged. RTM is a side-channel.

## Decisions locked in

| Decision | Choice |
|---|---|
| Storage | New tables in the existing scanner DB (whatever engine `configuration.properties` selects — Postgres / SQLite / Access). Same `MigrationRunner` pattern as everything else. |
| Granularity | Both Job-level and Block-level links. Step-level (per `ElementDTO`) is **out of scope** for v1 — too noisy. |
| External ref format | Free-text string (`requirements.external_ref`). No protocol-specific validation. Importers populate it; the UI displays it. |
| Import sources v1 | Generic CSV only. Jira REST and Azure DevOps adapters land in v2. |
| Export format | Extension of existing `ExcelWriter` "report" purpose — adds a `Coverage` sheet. **No** separate PDF/HTML in v1. |
| UI surface | One new pane `ARRequirementsPane` (CRUD list) + a new tab in the existing job editor for "Linked requirements". No new top-level scene. |
| ID strategy | DB auto-increment Long, exposed in DTOs. External refs are unique-per-source, NOT primary key. |
| Removal / rollback | Migration is reversible — drop tables. DTO field is `@Nullable`, so old job data is unaffected. |

## Repository layout

### New files

```
src/main/java/com/allinweb/ch/
├── model/
│   ├── RequirementDTO.java                NEW   id, externalRef, title, source, status, createdAt
│   ├── RequirementSource.java             NEW   enum: MANUAL, CSV, JIRA, AZURE_DEVOPS
│   ├── RequirementStatus.java             NEW   enum: ACTIVE, ARCHIVED
│   └── CoverageRowDTO.java                NEW   per-row coverage (req → linked jobs → last status)
├── facade/
│   ├── PerformRequirements.java           NEW   singleton CRUD + link/unlink + coverage query
│   └── RequirementImporter.java           NEW   CSV → List<RequirementDTO>, dedup on externalRef
├── db/migrations/
│   └── M20260506_Requirements.java        NEW   creates requirement, bot_job_requirement, block_requirement
├── component/pane/
│   ├── ARRequirementsPane.java            NEW   table + CRUD + import button
│   └── ARJobRequirementsTab.java          NEW   embedded in ARNewBotJobPane / job editor
├── component/scene/
│   └── ARRequirementsScene.java           NEW   wrapper for ARRequirementsPane
├── readersAndWriters/
│   └── CoverageSheetWriter.java           NEW   ExcelWriter chain link for the Coverage sheet
└── util/
    └── RequirementCsvParser.java          NEW   stateless parser (header autodetect)
```

### Modified files

```
src/main/java/com/allinweb/ch/
├── model/BotJobLoadDTO.java               +requirementIds : List<Long>
├── model/BlockLoadDTO.java                +requirementIds : List<Long>      (optional, per-block)
├── facade/PerformDataBase.java            +load/save link tables alongside job persistence
├── readersAndWriters/ExcelWriter.java     register CoverageSheetWriter for "report" purpose
└── component/pane/ARNewBotJobPane.java    add "Requirements" tab (delegates to ARJobRequirementsTab)
```

### Database schema (new)

```sql
-- M20260506_Requirements.java
requirement (
  id              BIGINT PK AUTOINCREMENT,
  external_ref    VARCHAR(255),
  title           VARCHAR(1024) NOT NULL,
  description     TEXT,
  source          VARCHAR(32) NOT NULL,        -- enum
  status          VARCHAR(16) NOT NULL,        -- enum
  created_at      TIMESTAMP NOT NULL,
  updated_at      TIMESTAMP NOT NULL,
  UNIQUE (source, external_ref)
)

bot_job_requirement (
  bot_job_id       BIGINT NOT NULL FK -> bot_job(id) ON DELETE CASCADE,
  requirement_id   BIGINT NOT NULL FK -> requirement(id) ON DELETE CASCADE,
  PRIMARY KEY (bot_job_id, requirement_id)
)

block_requirement (
  block_id         BIGINT NOT NULL FK -> block(id) ON DELETE CASCADE,
  requirement_id   BIGINT NOT NULL FK -> requirement(id) ON DELETE CASCADE,
  PRIMARY KEY (block_id, requirement_id)
)
```

## Phase status

| Phase | Working days | Window | Due | Status |
|---|---|---|---|---|
| 1. DB schema + migration | 3 | 2026-05-06 → 2026-05-08 | **2026-05-08 (Fri)** | Pending |
| 2. DTO + facade persistence | 2 | 2026-05-11 → 2026-05-12 | **2026-05-12 (Tue)** | Pending |
| 3. Requirements pane (CRUD) | 5 | 2026-05-13 → 2026-05-19 | **2026-05-19 (Tue)** | Pending |
| 4. Job ↔ Requirement linking UI | 3 | 2026-05-20 → 2026-05-22 | **2026-05-22 (Fri)** | Pending |
| 5. CSV import | 3 | 2026-05-25 → 2026-05-27 | **2026-05-27 (Wed)** | Pending |
| 6. Coverage Excel sheet | 4 | 2026-05-28 → 2026-06-03 | **2026-06-03 (Wed)** | Pending |
| 7. Validation + docs | 2 | 2026-06-04 → 2026-06-05 | **2026-06-05 (Fri)** | Pending |

*Calendar skips weekends. Italian Republic Day (Tue 2026-06-02) is treated as non-working.*

## Phase 1 — DB schema + migration (due 2026-05-08)

**Goal:** new tables exist on every supported engine, migration runs idempotently on every boot (per the boot-path fix from 2026-04-24).

**Deliverables:**
- `db/migrations/M20260506_Requirements.java` with the three tables above
- Engine-specific dialect handling (Postgres `BIGSERIAL`, SQLite `INTEGER PRIMARY KEY AUTOINCREMENT`, Access `AUTOINCREMENT`) — follow the pattern in `M20260427_OcrConfig.java`
- Smoke test verifying tables exist after a fresh boot AND after a re-boot (idempotency)

**Acceptance:** boot the app twice on each engine; tables exist exactly once; no migration errors in `ar_web_scanner_operations.log`.

## Phase 2 — DTO + facade persistence (due 2026-05-12)

**Goal:** Java side reads/writes the new tables; existing job save/load flow unchanged for jobs with no requirements.

**Deliverables:**
- `RequirementDTO`, `RequirementSource`, `RequirementStatus`
- `BotJobLoadDTO.requirementIds : List<Long>` (default `Collections.emptyList()`)
- `BlockLoadDTO.requirementIds : List<Long>` (default `Collections.emptyList()`)
- `PerformRequirements` singleton: `list()`, `getById()`, `create()`, `update()`, `archive()`, `linkJob()`, `unlinkJob()`, `linkBlock()`, `unlinkBlock()`, `coverageRows()`
- `PerformDataBase.saveBotJob()` / `loadBotJob()` extended to persist link tables in the same transaction as the job

**Acceptance:** unit test that creates a job + 2 requirements, links them, reloads the job, and gets back both requirementIds.

## Phase 3 — Requirements pane (CRUD) (due 2026-05-19)

**Goal:** users can list, create, edit, archive requirements from the UI.

**Deliverables:**
- `ARRequirementsPane` extending `ARPane`, layout: TableView on top, edit form below, toolbar with `New`, `Edit`, `Archive`, `Import CSV` (stub for Phase 5), `Refresh`
- Columns: External Ref, Title, Source, Status, Linked jobs (count)
- Edit form: External Ref, Title (required), Description (multi-line), Source (combo), Status (combo)
- `ARRequirementsScene` modal wrapper; menu item under `Tools` opens it
- Persistence via `PerformRequirements`

**Acceptance:** create / edit / archive a requirement through the UI; close + reopen the app; row is still there.

## Phase 4 — Job ↔ Requirement linking UI (due 2026-05-22)

**Goal:** when editing a job, user can attach/detach requirements.

**Deliverables:**
- New tab `Requirements` inside the job editor (`ARNewBotJobPane`), implemented as `ARJobRequirementsTab`
- Two-pane: left = available requirements (search box + filter by Source), right = linked requirements; transfer buttons in the middle
- Save propagates to `BotJobLoadDTO.requirementIds`, persisted via `PerformDataBase`
- Block-level linking deferred to a context-menu on the block list (right-click → "Link requirement..."); minimal UI to keep scope tight

**Acceptance:** link 3 requirements to a job, save, reopen the editor — same 3 are linked. Same for one block within the job.

## Phase 5 — CSV import (due 2026-05-27)

**Goal:** bulk-create requirements from a CSV (Jira/ADO export or hand-built).

**Deliverables:**
- `RequirementCsvParser` — header autodetect for: `Key`/`ID`/`External Ref` → `externalRef`, `Summary`/`Title` → `title`, `Description` → `description`. Other columns ignored.
- `RequirementImporter` — given a parsed list, dedups on `(source, externalRef)`, returns counts (created / updated / skipped)
- File chooser hooked to the `Import CSV` button in `ARRequirementsPane`; result dialog shows the counts
- Source on imported rows = `CSV` (or `JIRA` if header contains `Issue Type` / `Project Key`, heuristic only)

**Acceptance:** import a 100-row CSV; counts dialog matches expected; reimport same file → all 100 are "skipped" (idempotent).

## Phase 6 — Coverage Excel sheet (due 2026-06-03)

**Goal:** the existing execution Excel report grows a `Coverage` sheet that answers the stakeholder question.

**Deliverables:**
- `CoverageSheetWriter` registered into the `ExcelWriter` chain for the `report` purpose, runs after the existing execution sheet
- Sheet columns: External Ref, Title, Source, Status, Linked Jobs (count), Last Run Date, Last Run Status (PASS/FAIL/MIXED/NEVER), Linked Job Names (joined)
- Rows include **every active requirement**, not just the ones touched by the current run, so coverage gaps are visible
- Color coding: green PASS, red FAIL, grey NEVER, amber MIXED
- New summary row at top: `Total: X / Covered: Y / Passing: Z / Coverage %: Y/X`

**Acceptance:** run a job after creating 5 requirements (3 linked, 2 unlinked); Coverage sheet shows all 5, with the 2 unlinked marked NEVER and grey.

## Phase 7 — Validation + docs (due 2026-06-05)

**Goal:** ship-ready.

**Deliverables:**
- End-to-end manual test on all three DB engines (Postgres / SQLite / Access)
- Update `CLAUDE.md` with a new "Test traceability" subsection under *Architecture you need in your head before editing*
- Update `README.md` mentioning the new menu item and CSV format expectations
- Move this roadmap status to ✅ built and append a "Delivered scope" section in the format used by ROADMAP_1

**Acceptance:** all phases ✅; running scanner cleanly produces a Coverage sheet; documentation merged.

## Out of scope (v2 candidates)

- Jira REST / Azure DevOps live sync (read external system on a schedule)
- PDF/HTML coverage exports
- Step-level traceability (per-`ElementDTO`)
- Trend report (coverage % over time, multi-run comparison)
- Webhook on requirement status change
- Bidirectional sync — pushing test results back to Jira/ADO

These are intentionally deferred. Land v1 first; expand on demand.

## Risks

- **Schema dialect bugs on Access** — `ucanaccess` is the most fragile of the three engines. Phase 1 acceptance must include Access explicitly.
- **`BotJobLoadDTO` is shared with the Engine** — adding `requirementIds` requires the Engine to either ignore the field (`@JsonIgnoreProperties(ignoreUnknown=true)` already on its DTOs, per the *Premise* in the README) OR get the same DTO change. Verify before Phase 2 ships.
- **Existing jobs without links** must continue to load — covered by `requirementIds` defaulting to empty list.
