# ROADMAP 3 — Best-text extraction for `someText` / `definedName` + DB fallback mapping

**Status:** ✅ Phase 3a delivered (text resolver, no DB). ✅ Phase 3b delivered (locator tables + upsert hook). 📋 Phase 3c (recovery on xPath drift + Engine cross-repo) pending.
**Owner:** Osvaldo Martini
**Depends on:** Roadmaps 1 + 2 (OCR scores feed into candidate ranking)

## Phase 3a delivered (2026-04-26)

- `com.allinweb.ch.util.TextSimilarity` — Levenshtein distance + ratio, token-set Jaccard ratio, `humanize` (camelCase / snake / kebab → "Title Case"), `slug`, `uniquify`.
- `com.allinweb.ch.facade.ElementTextResolver.resolveAll(ElementDTO[], Path)` — gathers candidates from 7 sources, applies OCR-corroboration ×1.5 multiplier (Roadmap 2 reminder rule), picks the highest-scoring text, mutates `someText` and `definedName` in place.

### Sources implemented

| # | Source | Score | Notes |
|---|---|---|---|
| 1 | `<label for=this.id>` resolved within the same pick batch | 1.00 | Highest weight — canonical a11y pairing |
| 2a | `aria-label` attribute | 1.00 | |
| 2b | `aria-labelledby` resolved to target's `someText` within the batch | 0.95 | |
| 3 | Scanner-provided `someText` (whatever the JS scanner extracted) | 0.40 | Medium — defers to label / aria when present, but wins over the fallback humanizer when no other source applies |
| 4a | `placeholder` | 0.65 | |
| 4b | `title` | 0.60 | |
| 4c | `alt` | 0.60 | |
| 4d | `value` (when an input has a default value) | 0.45 | |
| 5a | Humanized `id` | 0.30 | "firstName" → "First Name" |
| 5b | Humanized `name` | 0.30 | |
| 7 | OCR `EXACT_CONTAIN` | 0.85 | From `ocr-correlation-HP.json` |
| 8 | OCR `OVERLAP` | 0.70 | |
| 9 | OCR `PROXIMITY` | 0.55 | |

OCR `EXACT_CONTAIN` / `OVERLAP` / `PROXIMITY` text is also used as a corroborator: when its tokens / Levenshtein agree (≥ 0.85 ratio) with any non-OCR candidate, that candidate's score is multiplied by 1.5. This ensures DOM-derived "Password" beats raw OCR misreads like "password2" while still benefiting from the visual confirmation.

### Sources deferred

- **6 — Preceding visible text node within N px**: requires a JS scanner extension to surface; not crucial when `<label for>` works.
- **9 — Ancestor card/section title**: this is exactly the bleed-through we're fighting against in the BancaStato data; intentionally not a candidate source. The scanner-provided `someText` of card ancestors keeps showing through, but those ancestor elements aren't the user-facing pick targets.

### `definedName` generation

After `someText` is resolved:
1. `slug(someText)` → snake_case ASCII, max 64 chars (`"User Number"` → `"user_number"`).
2. `uniquify` against an in-batch `Set<String>` to avoid collisions: `password`, `password_2`, `password_3`, …
3. When every candidate is empty (e.g. an unattributed `<div>`), fall back to `slug(tagName + "_" + id-or-name)` → e.g. `div_iamcontentcontainer`.

### Wire points

`SimpleWebSocketServer.SEARCH_TOOL` (both `scannerGrid` and `mobile-return-server` branches) and `PerformListElements.runScan` were reordered:

```
1. webSocket.send(splitDTO)   // raw, fast — UI sees immediately
2. PageDiagnosticDumper.dumpRectsFromElements
3. PageOcrDumper.runAndDump   // writes ocr-correlation-HP.json
4. ElementTextResolver.resolveAll   // mutates DTOs in place
5. outputJsonElementDTO("elementDTO-HP" | "elementDTO-PS")
6. outputJsonElementDTO("AI-ElementDTO-HP" | "AI-ElementDTO-PS")
```

The persisted `elementDTO-*.json` and `AI-ElementDTO-*.json` files now reflect the resolver's output. The WebSocket message to the UI grid still uses the pre-resolution snapshot; updating the live UI grid would require a follow-up refresh message and is not in scope for 3a.

## Phase 3b delivered (2026-04-26)

- Migration `M20260428_ElementLocator` creates two tables for all 4 dialects: `element_locator` (one row per `(homebanking_id, home_url_id, defined_name)` with frozen `*_original` columns + mutable `*_current` columns + `pick_count`) and `element_locator_rename` (append-only audit trail; written by Phase 3c, table created now).
- `ElementLocatorEntity` + `ElementLocatorRenameEntity` (Lombok `@Data`).
- `ElementLocatorRepository` — `findByKey`, `listForScope`, `upsertOnPick`, `upsertOnPickBatch`, `insertRename` (Phase 3c hook). `nullableEq` helper handles SQLite's NULL comparison gotcha. Logs `LOCATOR INSERT` / `LOCATOR UPDATE` lines to `com.allinweb.scanner` so the audit trail of every pick lives in `ar_web_scanner_scanner.log`.
- Wired into `SimpleWebSocketServer.SEARCH_TOOL` (scannerGrid branch) and `PerformListElements.runScan` immediately after `ElementTextResolver.resolveAll`. `homebanking_id` comes from `splitDTO.getHomeBankingId()` / the scan param; `home_url_id` is best-effort via `ARScannedElementScene.getInstance().getCurrentBotJob().getHomeUrlId()` and falls back to null when the scene isn't initialised. Failures are logged but never fatal — locator persistence is additive, never blocking the pick flow.

### What's persisted on every pick

For each ElementDTO with a non-empty `definedName`:
- **First sight** (no row matches `(hbId, homeUrlId, definedName)`): INSERT a new row with all `*_original` and `*_current` columns set to the same values, `pick_count = 1`.
- **Subsequent sight**: UPDATE all `*_current` columns + `pick_count++` + `updated_at = CURRENT_TIMESTAMP`. The `*_original` row stays untouched — it remains the canonical reference for Phase 3c drift detection.

### What's deferred to Phase 3c

- Drift detection (compare `*_current` vs `*_original`, write `element_locator_rename` rows when fields change).
- Multi-strategy `findOrRecover(locator)` that backs the bot-run resolution path when the live xPath stops matching.
- Cross-repo: AR Web Engine consumes the recovery path.

## Goal

Two tightly coupled capabilities:

1. At pick time, compute the strongest human-readable text for every element and persist it as `someText` + `definedName`.
2. At bot-run time, when an xPath stops matching (client rebrand, JS injection rename, DOM restructure), recover the original element via a multi-strategy lookup and **log the rename** so we keep a history of client-side drift.

## Current gap

Today `someText` is populated opportunistically (e.g. DTO id=1 gets `"Password"` — good; ids 2–9 inherit `"Access to InLinea Insert your credentials"`, which is the ancestor card title, not the element's own label — weak). No deterministic ranking; no persistence of historical mapping; no recovery path when xPath drifts.

## Text-source ranking (highest → lowest)

Per element, score every candidate from these sources and keep top N:

| # | Source | Priority | Notes |
|---|---|---|---|
| 1 | Sibling/parent `<label for="id">` | 🟢 very high | Canonical a11y pairing |
| 2 | `aria-label`, `aria-labelledby` target text | 🟢 very high | Screen-reader truth |
| 3 | Element's own `innerText` (leaf + visible) | 🟢 high | Buttons, links, labels |
| 4 | `placeholder`, `title`, `alt` | 🟡 medium | Inputs, images |
| 5 | `name`, `id` humanized (`firstName` → "First Name") | 🟡 medium | Fallback when no visible text |
| 6 | Preceding visible text node within N px (left or above) | 🟡 medium | Forms with layout labels |
| 7 | OCR `EXACT_CONTAIN` word (from Roadmap 2) | 🟠 low-med | Catches image-rendered labels |
| 8 | OCR `PROXIMITY` word | 🟠 low | Last-resort |
| 9 | Ancestor card/section title | 🔴 lowest | What ids 2–9 currently get — keep only as *context*, not as `someText` |

**`someText`** = top-1 candidate text after cleaning (trim, collapse whitespace, strip trailing `:` / `*` / `!`).
**`definedName`** = `slug(someText)` with disambiguation suffix (`password`, `password_2` when duplicate on the same page).

Both computed once at pick time by a new `ElementTextResolver`.

### Scoring multipliers (consume Roadmap 2's corroboration rule)

- Base score by source priority (1.0 = highest, 0.1 = lowest).
- `× 1.5` if the same text appears in an OCR `EXACT_CONTAIN` result for this element (Roadmap 2 reminder rule).
- `× 1.2` if two independent sources agree (e.g. `aria-label` == `<label>` text).
- `× 0.5` if the text is identical to an ancestor's `someText` (weak signal — the card title bleed-through seen in DTO ids 2–9).

## New DB tables

```sql
-- One row per element per homebanking page, created on first pick.
CREATE TABLE element_locator (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    homebanking_id          VARCHAR(64)  NOT NULL,
    page_url_hash           CHAR(64)     NOT NULL,   -- SHA-256 of normalized URL
    page_title              VARCHAR(255),
    x_path                  TEXT         NOT NULL,
    custom_x_path           TEXT,
    css_selector            VARCHAR(512),
    attrib_id               VARCHAR(255),
    attrib_name             VARCHAR(255),
    tag_name                VARCHAR(64),
    some_text_original      VARCHAR(512) NOT NULL,   -- frozen at creation
    defined_name_original   VARCHAR(255) NOT NULL,
    coords_original         VARCHAR(64),             -- "x,y" CSS px
    ocr_text_original       VARCHAR(512),            -- OCR EXACT_CONTAIN at creation
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_hb_page_defname (homebanking_id, page_url_hash, defined_name_original),
    INDEX ix_hb_page (homebanking_id, page_url_hash),
    INDEX ix_defined (defined_name_original)
);

-- Append-only history. Every time the scanner/engine observes a mismatch
-- (someText changed, xpath no longer resolves, etc.), write a row here.
CREATE TABLE element_locator_rename (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    locator_id              BIGINT       NOT NULL,
    observed_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    change_type             ENUM('TEXT','XPATH','ATTRIB_ID','ATTRIB_NAME','COORDS','OTHER') NOT NULL,
    some_text_new           VARCHAR(512),
    defined_name_new        VARCHAR(255),
    x_path_new              TEXT,
    attrib_id_new           VARCHAR(255),
    attrib_name_new         VARCHAR(255),
    coords_new              VARCHAR(64),
    ocr_text_new            VARCHAR(512),
    match_confidence        DECIMAL(5,2),    -- 0..100 from recovery algorithm
    recovery_strategy       VARCHAR(64),     -- 'TEXT_EXACT','TEXT_FUZZY','OCR','COORDS','CSS','COMBINED'
    FOREIGN KEY (locator_id) REFERENCES element_locator(id) ON DELETE CASCADE,
    INDEX ix_locator (locator_id),
    INDEX ix_observed (observed_at)
);
```

Postgres / SQLite / UCanAccess variants will be produced once the schema is approved.

## Recovery algorithm (when xPath fails at bot-run time)

```
findOrRecover(locator):
  1. Try locator.x_path and custom_x_path. If resolves + text similar → MATCH
  2. Try css_selector. If resolves + text similar → MATCH (log XPATH drift)
  3. Try attrib_id / attrib_name. If resolves → MATCH (log XPATH drift)
  4. Text-based:
       scan all interactive elements on page, score each by:
         - fuzzy(element.text, some_text_original)       [Levenshtein / token-set]
         - fuzzy(element.text, last_known some_text_new)
       keep top-3; if best ≥ threshold → MATCH, log TEXT change
  5. OCR-based (Roadmap 2):
       OCR page, fuzzy-match ocr_text_original against OCR words
       project hit back to DOM via OcrDomCorrelator, log TEXT+OCR change
  6. Coordinate-based (weakest):
       at same viewport size, pick element under coords_original (±20 px), log COORDS
  On any successful MATCH with drift detected → INSERT into element_locator_rename
```

## New Java classes in `ar-web-selenium`

```
com.allinweb.ch.model
├── ElementLocatorEntity.java           // JPA/Hibernate for element_locator
└── ElementLocatorRenameEntity.java     // JPA/Hibernate for rename history

com.allinweb.ch.facade
├── ElementTextResolver.java            // text-source ranking → someText + definedName
├── ElementLocatorRepository.java       // DB access (find/insert/rename-log)
└── ElementRecoveryService.java         // findOrRecover() algorithm

com.allinweb.ch.util
└── TextSimilarity.java                 // Levenshtein + Jaro-Winkler + token-set wrappers
```

## Wire-in points

- **At pick time** (`SEARCH_TOOL` case in `SimpleWebSocketServer`): call `ElementTextResolver.resolve(dto, pageSource, ocrResult)` to populate `someText` / `definedName` *before* the DTO is serialized. Then `ElementLocatorRepository.upsert(dto)`.
- **At bot-run time** (wherever the Engine currently resolves an element by xPath — see the priority ladder in `util/ARPriorities` and `xPath Auto.md`): wrap the call with `ElementRecoveryService.findOrRecover(locator)`. On drift, it writes a rename row automatically — callers don't need explicit logging.

## Migration path (important)

The AR Web Engine lives in a **separate repo** and historically has class-duplicated copies of `model` DTOs. Before deploying new `some_text` semantics, coordinate with Engine so its element-matching ladder (the one in `xPath Auto.md`) consumes `defined_name` and `some_text` with the same ranking.

Database schema changes go through `com.allinweb.ch.db.migrations` — add them as `M<YYYYMMDD>_ElementLocator.java` and `M<YYYYMMDD>_ElementLocatorRename.java`. `MigrationRunner` will apply them on next startup.

## Build order (after Roadmaps 1 and 2)

1. `TextSimilarity` + `ElementTextResolver` — pure Java, unit-testable offline against `elementDTO-HP.json`.
2. DB migrations for both tables (Postgres first, then SQLite and UCanAccess variants).
3. `ElementLocatorRepository` + upsert hook at `SEARCH_TOOL`.
4. `ElementRecoveryService` + swap in at Engine-side resolution call sites.
5. Smoke test:
   - Pick password input → inspect `element_locator` row.
   - Change its `id` via DevTools → re-run bot.
   - Confirm element still resolves and `element_locator_rename` contains a row with `change_type='ATTRIB_ID'`.

## Verification checklist (post-build)

- [ ] `ElementTextResolver` gives the password input `someText = "Password"` and `definedName = "password"`.
- [ ] For DTO ids 2–9 (card-body ancestors), `someText` is derived locally (empty or ancestor-context flag), not the card title bleed-through.
- [ ] Inserting a duplicate element on the same page appends `_2`, `_3` suffixes to `definedName`, never collides.
- [ ] Renaming the password input's `id` in DevTools → bot still finds it, rename table records the event with `match_confidence ≥ 80`.
- [ ] OCR corroboration (Roadmap 2) raises the score of candidates whose text matches an `EXACT_CONTAIN` OCR hit.
