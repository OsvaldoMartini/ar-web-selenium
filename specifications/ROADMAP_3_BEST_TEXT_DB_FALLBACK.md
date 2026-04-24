# ROADMAP 3 — Best-text extraction for `someText` / `definedName` + DB fallback mapping

**Status:** draft — DB schema needs approval before build
**Owner:** Osvaldo Martini
**Depends on:** Roadmaps 1 + 2 (OCR scores feed into candidate ranking)

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
