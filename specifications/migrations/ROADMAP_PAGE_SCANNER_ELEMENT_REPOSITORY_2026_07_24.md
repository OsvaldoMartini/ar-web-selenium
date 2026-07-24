# Page Scanner Element Repository and Page-Aware Playwright Self-Healing Roadmap

Date: 2026-07-24
Status: Page-collision correction implemented in source; user-owned build/tests pending
Canonical coordination document:
`CLAUDE_vs_CODEX_MIGRATION_CHECKS_2026_07_12.md`

## 1. Objective

Use repeated Page Scanner runs across the Banca Stato application to build a durable repository of
observed web elements. During `executeJob()`, when the authored XPath, CSS selector, and stored
references no longer resolve, Playwright must use that repository to find the correct element by
its names and stable attributes on the **current page**, validate that the candidate is unique, and
perform the requested action safely.

The repository is discovery data. A scan must not automatically create Bot Job instructions.
Instruction creation remains an explicit Page Scanner/Memory List Apply operation.

## 2. Direct Answer: What the Application Does Today

Yes. Every non-empty, successfully completed detached Page Scanner scan currently attempts to
insert or update rows in `scanned_element`.

The current path is:

1. `GridItemScann.tsx` sends `pageScanner.scan`.
2. `SimpleWebSocketServer.handlePageScannerCommand(...)` validates the detached workspace and
   maps the request to `PRE_SCAN_PAGE`.
3. `BotJobDetailsWorkspaceHost` queues the operation and calls
   `PreScanWorkflowService.scan(...)`.
4. The workflow scans the current shared Playwright page, keeps its actionable results, resolves
   OCR-derived names, and calls `DefaultDiagnosticsPort.persist(...)`.
5. `PerformDataBase.upsertScannedElements(...)` calls
   `ScannedElementRepository.upsert(...)`.

Important boundaries:

- A repeated element updates the existing row, increments `scan_count`, and updates
  `last_scanned_at`.
- A locator identity not seen before inserts a new row.
- Elements absent from a later scan are not deleted or marked stale.
- The two diagnostic JSON files are overwritten per scan; they are not the cumulative repository.
- Detached Page Scanner currently updates `scanned_element`, but does not update the older
  `element_locator`/`element_locator_rename` repository.
- A scan does not insert into `instruction` or `reference`. Only explicit Apply does that.

## 3. Production Database Evidence

Read-only snapshot from:

`D:\Projects\ARWebBancaStato\ARWeb\database.db`

Snapshot time: 2026-07-24

| Measurement | Observed value |
|---|---:|
| Applied migration | `2026-07-04__scanned_element` |
| Bot Job | ID 5, `Saldo Banca Stato` |
| Unique `scanned_element` rows | 537 |
| Total scan observations (`SUM(scan_count)`) | 15,885 |
| Distinct stored `page_url` values | 3 |
| Latest stored scan | `2026-07-24 10:51:49` |
| Rows with `defined_name` | 536 / 537 |
| Rows with `some_text` | 537 / 537 |
| Rows with `client_named` | 0 / 537 |
| Rows with XPath and CSS | 537 / 537 |
| Rows with custom XPath | 6 / 537 |
| Same-page duplicate `defined_name` groups | 56 |
| Rows with persisted raw OCR text/confidence | 0 / 537 |

This proves that Page Scanner is already building a cumulative registry. It also proves that names
are frequently non-unique, so an unrestricted name lookup cannot safely take the first result.

## 4. Current Persistence Contract

### 4.1 Schema and identity

`M20260704_ScannedElement` creates `scanned_element` for SQLite/TEXT, PostgreSQL, SQL Server, and
Access. The current unique identity is:

```text
(home_banking_id, bot_job_id, element_hash)
```

`element_hash` is the SHA-256 hash of:

```text
xpath | iframe_xpath | attrib_id | css_selector
```

The hash deliberately distinguishes same-name elements with different locator identities. It does
not include `page_url` or `home_url_id`.

### 4.2 Upsert behavior

For each result in one scan transaction:

- a missing scope/hash inserts a row with `scan_count = 1`;
- an existing scope/hash refreshes locator/name/page fields;
- an update increments `scan_count`;
- an update preserves a client-authored `custom_x_path` when the new raw scan does not supply one;
- the batch commits together or rolls back together inside `ScannedElementRepository`.

### 4.3 Persistence acknowledgement gap

The repository transaction is internally atomic, but its caller is best-effort:

- `PerformDataBase.upsertScannedElements(...)` catches every database exception and returns
  `[0, 0]`.
- `DefaultDiagnosticsPort.persist(...)` cannot distinguish a real zero-change result from a failed
  write.
- `PreScanWorkflowService.scan(...)` therefore emits final `done` after the persistence call even
  when the database was not updated.
- The initial `pageScanner.scanResponse` only means the asynchronous scan was accepted. It is not a
  persistence acknowledgement.

Consequently, neither `accepted` nor `done` currently proves that the element repository was saved.

## 5. Current `executeJob()` Locator and Healing Contract

The Scanner TEST RUN path is Playwright-only:

1. `ScannerRuntimeBackend.executeJob()` selects the active instruction.
2. It calls `PerformActions.performWebActions(...)`.
3. `PerformActions` delegates to `PlaywrightBridge.tryPlaywrightWebAction(...)`.
4. `ARPlaywrightDriver` delegates to `PlaywrightActionExecutor`.

### 5.1 Normal Playwright locator order

`PlaywrightActionExecutor.selectorsFor(...)` currently builds:

1. instruction XPath;
2. instruction CSS selector;
3. instruction references in their persisted order:
   - referenced XPath and CSS;
   - exact custom `AttrData:*` attributes;
   - `test-id`, `data-testid`, `data-test-id`, `data-cy`, and `data-qa`;
   - ID references;
   - HTML `name` references;
   - tag-qualified ID/name variants.

`InstructionLoad.name` is not directly converted into a live Playwright name/text locator.

### 5.2 Existing scanned-repository fallback

Partial self-healing already exists. If CLICK/OTHER or INSERT returns false,
`PlaywrightBridge.healAndRetry(...)`:

1. gets the Bot Job ID indirectly from the `ARPriorities` singleton;
2. loads every `scanned_element` row for that Bot Job;
3. applies `ScannedElementResolver`:
   - exact raw/custom XPath;
   - exact CSS;
   - unique exact `InstructionLoad.name` against `defined_name` or `some_text`;
   - duplicate name resolved by nearest coordinates;
   - fuzzy name with a minimum 0.80 ratio;
4. retries when confidence is at least 0.75.

This is useful but incomplete:

- it is Bot-Job-wide, not current-page-aware;
- it excludes `client_named` and HTML `attrib_name` from name matching;
- it does not heal OUTPUT/text operations;
- the healed instruction copies only a subset of the stored locator data;
- it uses raw XPath instead of preferring a persisted custom XPath;
- it does not validate live uniqueness before acting;
- it consults only `scanned_element`, not `element_locator`;
- coordinates are attempted inside the normal action executor **before** repository healing.

## 6. Critical Gaps Before a Large Multi-Page Scan Repository

### G1 - The stored URL is not necessarily the page that was scanned

The scan correctly reuses and scans the current Playwright page. However, persistence passes
`context.endpointUrl()`, which is the configured Bot Job endpoint, instead of
`browser.currentUrl()`, which is the live page after the user navigates.

For a multi-page Banca Stato session, repository rows can therefore be labeled with the original
endpoint instead of the page where the elements were observed.

### G2 - Element identity is not page-scoped

Because `page_url` is absent from the unique identity, two pages containing the same locator
signature collide. The later scan updates the previous row and replaces its page metadata.

### G3 - Execution loads candidates from every historical page

`resolveScannedElementByBotJob(...)` loads all rows for the Bot Job, most recently scanned first.
The active Playwright URL is available through `ARPlaywrightDriver.currentUrl()`, but is never sent
to the resolver.

An element name unique across the whole historical registry can still belong to a different page.

### G4 - Name fallback is persisted-locator fallback, not live-page name discovery

The existing resolver finds a registry row by name and then retries that row's stored XPath/CSS. If
those persisted locators have also drifted, it fails. It does not yet query the current DOM using
the known accessible name, label, text, role, or stable attributes.

### G5 - Coordinate actions happen too early

Click and fill try coordinates before `PlaywrightBridge` consults the registry. A stale coordinate
can act on the wrong current-page element and prevent the safer repository/name path from running.
Coordinates must remain the final, explicitly allowed fallback.

### G6 - Boolean results cannot distinguish safe retry conditions

The current `boolean` action result conflates:

- locator not found;
- locator ambiguous;
- element found but not actionable;
- action attempted and failed;
- coordinate fallback failed.

Healing must run after `NOT_FOUND`, not after an action may already have produced a side effect.
This is required to preserve the one-action rule and prevent duplicate clicks.

### G7 - Repository freshness is not represented

Rows never seen in later scans remain eligible indefinitely. Locator drift usually changes
`element_hash`, creating another row rather than retiring the old identity. Historical candidates
can therefore turn a formerly unique name into an ambiguous name.

### G8 - The detached scan does not capture every discovered DOM element

`PreScanWorkflowService.keepActionableElements(...)` retains only elements classified as
`input`, `button`, `output`, or `label`. The phrase “every element seen by the scanner” therefore
means every retained actionable result, not every DOM node returned by the scanner.

### G9 - OCR audit columns are not populated by the current upsert SQL

The schema/model contains `ocr_text`, `ocr_match_quality`, and `ocr_confidence`, but the current
insert/update statements do not write those columns.

## 6.1 Implemented Page-Collision Correction (2026-07-24)

The following source changes supersede G1-G3 and the related parts of G4:

- `ScannedPageIdentity` defines collision-first `url-v1` identity from a valid live HTTP(S) URL.
- `PreScanWorkflowService` captures the live URL before scanning, checks it again after OCR, and
  refuses persistence when the page changed.
- `PerformListElements` applies the same before/after page guard to its scanner path.
- `M20260724_ScannedElementPageScope` appends `page_key`, backfills legacy rows, recomputes
  `element_hash` as page key + length-delimited locator identity, and creates page/hash and
  page/name indexes.
- `ScannedElementRepository` inserts, updates, loads, and applies custom XPath only inside the exact
  page scope.
- Locator Apply obtains the current Page Scanner browser URL server-side rather than trusting the
  configured endpoint.
- `PlaywrightBridge` loads fallback candidates only for `activeDriver.currentUrl()`, prefers
  `custom_x_path`, and enables the same page-aware retry for OUTPUT.
- `ScannedElementResolver` now also recognizes `client_named` and HTML `attrib_name`.

The migration deliberately retains the original cross-dialect unique constraint. Because the
stored `element_hash` is now page-scoped, that constraint no longer merges equal locator
signatures from different pages. This avoids destructive SQLite/Access table rebuilds.

Still pending: live DOM name/role uniqueness validation, healing before coordinate fallback,
freshness/retirement state, OCR audit persistence, and a typed WebSocket persistence receipt.

## 7. Target Safety and Matching Contract

The implementation must enforce this order:

1. stable authored XPath/CSS/instruction references, without coordinates;
2. current-page repository candidates;
3. unique live-page resolution using repository names and stable attributes;
4. coordinates only as the last resort and only when the instruction policy permits them.

Candidate resolution must:

- scope by organization, Bot Job, and normalized current page key;
- prefer `custom_x_path`, then current XPath/CSS and stable testing attributes;
- treat `client_named`, `defined_name`, `some_text`, HTML `name`, accessible name, and label as
  distinct aliases with explicit priority;
- retain tag, role, iframe, and shadow context;
- require exactly one live candidate before a side-effecting action;
- refuse an ambiguous candidate instead of choosing the first row;
- record the strategy, confidence, page key, candidate count, and outcome;
- never execute a second click merely because the first click changed the page before returning.

## 8. Phased Implementation Roadmap

### Phase 0 - Freeze evidence and page identity rules

- [x] Trace detached Page Scanner persistence end to end.
- [x] Trace `executeJob()` Playwright locator and healing paths.
- [x] Capture a read-only production database baseline.
- [ ] Back up the production database before any schema migration or mass scan.
- [x] Define collision-first URL normalization (`url-v1`):
  - lower-case scheme/host;
  - normalize default ports and one trailing slash;
  - preserve raw query order, duplicate parameters, empty values, and values;
  - preserve fragments and SPA route identity;
  - reject blank/non-HTTP(S) live URLs instead of writing an unknown-page observation.
- [x] Use a versioned URL-only page key:
  `url-v1:` + SHA-256(normalized live URL).
  Exact same-URL wizard states remain an explicit future `viewDiscriminator` case.

### Phase 1 - Make each scan persistence result authoritative

- [x] Capture the live `browser.currentUrl()` before scanning and verify it again before
  persistence; refuse the write when navigation changes the page key.
- [ ] Pass an immutable `ScanPersistenceContext` containing configured endpoint, actual URL,
  normalized page key, organization ID, Bot Job ID, Home URL ID, and scan request ID.
- [ ] Replace `[inserted, updated]` with a typed receipt:
  `inserted`, `updated`, `unchanged`, `failed`, `pageKey`, `scanId`, and error details.
- [x] Add a strict interactive persistence path so a Page Scanner database failure reaches the
  workflow failure state instead of becoming fake `[0,0]` success.
- [ ] Publish a correlated final Page Scanner persistence response.
- [ ] Display “Saved: N new / N updated” or a professional failure message in Page Scanner.
- [x] Keep JSON diagnostic failure separate from database repository failure.

### Phase 2 - Introduce page-aware schema without editing the applied migration

- [x] Add append-only `M20260724_ScannedElementPageScope`; do not modify
  `M20260704_ScannedElement`.
- [x] Add normalized `page_key`.
- [x] Make uniqueness page-aware:

```text
(home_banking_id, bot_job_id, page_key, element_hash)
```

- [x] Backfill legacy rows conservatively from stored `page_url`.
- [x] Preserve rows whose historical page cannot be proven; mark their page identity as legacy
  instead of assigning them silently to the current page.
- [x] Add unique page/hash and page/name indexes. `element_hash` now includes the page key, keeping
  the original SQLite/Access unique constraint safe without destructive table rebuilds.
- [ ] Add `last_seen_scan_id`/freshness state so absence can be observed without destructive delete.
- [ ] Decide whether to consolidate `element_locator` into this repository or synchronize detached
  scans with it. Do not maintain two silently divergent sources of truth.
- [ ] Persist OCR audit fields when OCR contributes to the resolved name.

### Phase 3 - Build a deterministic page-aware resolver

- [x] Add a repository query scoped by Bot Job and normalized current page key.
- [ ] Pass the Bot Job ID directly in immutable execution context instead of deriving it only from
  mutable `ARPriorities`.
- [x] Extend persisted-name aliases to include `client_named`, `defined_name`, `some_text`, and
  HTML `attrib_name`. Live accessible-name/label/role resolution remains pending.
  accessible name, label, role, and configured custom attributes.
- [ ] Score stable attributes above fuzzy text.
- [ ] Prefer recent/frequently confirmed rows only after exact page and stable-attribute matching.
- [ ] Reject ambiguous same-name candidates without a safe discriminator.
- [x] Prefer `custom_x_path` over raw scanner XPath.
- [ ] Return a structured resolution result with candidate count, strategy, confidence, and reason.

### Phase 4 - Resolve and validate against the live current page

- [ ] Add a Playwright live candidate resolver using current-page role/label/text/name selectors.
- [ ] Use repository data to constrain tag, role, iframe, shadow root, attributes, and expected text.
- [ ] Require one visible/actionable candidate for CLICK/INSERT and one readable candidate for
  OUTPUT.
- [ ] Verify that a stored XPath/CSS candidate and a live name candidate describe the same element
  when both resolve.
- [ ] Cache only within the current navigation/document generation; invalidate on navigation.

### Phase 5 - Integrate safe self-healing into `executeJob()`

- [ ] Split locator discovery from action execution.
- [ ] Replace the boolean result with statuses such as:
  `SUCCESS`, `NOT_FOUND`, `NOT_UNIQUE`, `NOT_ACTIONABLE`, and `ACTION_FAILED`.
- [ ] Run repository/name healing only for safe pre-action failure states.
- [ ] Move coordinate fallback after repository/live-name resolution.
- [x] Extend the existing repository healing retry to OUTPUT/text operations.
- [ ] Copy all required locator/reference/shadow/iframe fields into the healed instruction.
- [ ] Execute the final side-effecting Playwright action exactly once.
- [ ] Remove the stale “falling back to Selenium” log message.

### Phase 6 - Repository operations and observability

- [ ] Show scan ID, actual page, inserted/updated counts, and repository failure in Page Scanner.
- [ ] Add a read-only repository view grouped by page and name.
- [ ] Show duplicate/ambiguous names and stale candidates before TEST RUN.
- [ ] Add an explicit client action to retire/merge bad observations; do not delete automatically.
- [ ] Record successful and rejected healing attempts without logging credentials or input values.

### Phase 7 - Focused verification

- [x] Add focused SQLite source tests proving the same locator on two pages remains two page-scoped
  observations (test execution is user-owned).
- [x] Add focused source tests proving a rescan/custom-XPath mutation updates only its page.
- [x] Scope execution lookup by the active Playwright page so cross-page name candidates are not
  loaded.
- [ ] A duplicate current-page name without a stable discriminator is refused.
- [x] Add source coverage showing custom XPath survives a same-page rescan and is page-local.
- [x] Route OUTPUT through page-aware healing.
- [ ] Registry healing occurs before coordinates.
- [ ] Locator discovery can try several sources while the final click/fill runs once.
- [ ] Persistence failure produces a correlated UI failure, not `done`.
- [x] Add source tests for fragments, query order/duplicates, trailing slash, invalid live URLs,
  default ports, and SPA routes.
- [ ] Switching Bot Jobs cannot query the previous Bot Job's repository.
- [ ] PostgreSQL, SQL Server, SQLite/TEXT, and Access migrations preserve existing observations.
- [ ] A production-copy Banca Stato pilot scans multiple pages, then replays locator-drift fixtures
  without touching the live production database.

## 9. Primary Files Expected to Change

Backend:

- `src/main/java/com/allinweb/ch/facade/PreScanWorkflowService.java`
- `src/main/java/com/allinweb/ch/facade/PerformDataBase.java`
- `src/main/java/com/allinweb/ch/db/ScannedElementRepository.java`
- `src/main/java/com/allinweb/ch/facade/ScannedElementResolver.java`
- `src/main/java/com/allinweb/ch/facade/actions/PlaywrightBridge.java`
- `src/main/java/com/allinweb/ch/facade/PlaywrightActionExecutor.java`
- `src/main/java/com/allinweb/ch/driver/ARPlaywrightDriver.java`
- a new append-only class under `src/main/java/com/allinweb/ch/db/migrations/`
- Page Scanner response contracts in `SimpleWebSocketServer` and workspace services

Frontend, only when the persistence receipt/repository view is implemented:

- `abr-react-ts-grid/src/components/GridItemScann.tsx`
- Page Scanner protocol/controller files
- a dedicated read-only element-repository component if Phase 6 is approved

Focused tests:

- `ScannedElementRepositoryTest`
- `ScannedElementResolverTest`
- `PlaywrightBridgeTest`
- `PlaywrightActionExecutorSelectorTest`
- `PlaywrightActionExecutorSingleShotTest`
- `PreScanWorkflowServiceTest`
- Page Scanner WebSocket/workspace integration tests

## 10. Acceptance Criteria

- [ ] Every completed Page Scanner run states whether repository persistence succeeded.
- [x] The stored page identity is the actual live page scanned, not merely the configured start URL.
- [x] The same locator signature on different pages remains independently addressable.
- [x] `executeJob()` queries only the active page's repository observations during healing.
- [ ] When authored locators drift, a unique current-page name/stable-attribute candidate can heal
  CLICK, INSERT, and OUTPUT.
- [ ] Ambiguous candidates fail safely with diagnostics.
- [ ] Coordinates are used only after page-aware healing and only when explicitly allowed.
- [ ] One instruction produces at most one side-effecting Playwright action.
- [ ] Repository changes remain separate from explicit Bot Job instruction creation.
- [ ] Mass scanning Banca Stato improves the repository without silently overwriting cross-page
  observations or hiding database failures.

## 11. Current Operational Recommendation

The page-collision correction is implemented in source. A scan now uses the exact live Playwright
URL, refuses persistence if navigation changes during the scan, and upserts independently by
organization + Bot Job + page + locator.

Before initiating a systematic Banca Stato scan campaign:

1. back up `database.db`;
2. compile/package the backend so the append-only migration is present at startup;
3. run the focused migration/repository/workflow tests listed in this roadmap;
4. expose the richer correlated persistence receipt before beginning an unattended mass scan;
5. use a copy of production data for resolver validation;
6. finish live candidate uniqueness and healing-before-coordinates before treating self-healing as
   fully safe for side-effecting actions.
