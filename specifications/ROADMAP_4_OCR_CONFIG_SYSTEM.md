# ROADMAP 4 — OCR Configuration System (per-page tunable knobs)

**Status:** ✅ Phase 4a delivered (2026-04-24). ✅ Phase 4b polished editor delivered (2026-04-24) — per-param type-aware controls, tooltips, ComboBox enums, colour-mapping template helper. ✅ Phase 4c "Test On Current Page" delivered (2026-04-24). ✅ Phase 4c+ test review grid delivered (2026-04-26) — clicking Test now opens a separate `AROcrTestResultsScene` with a colour-coded results table, per-row Approved checkbox, and the annotated screenshot rendered alongside. Full visual HSV picker deferred. See [`OCR_CONFIG_PARAMS.md`](./OCR_CONFIG_PARAMS.md) for the canonical param reference.

## Phase 4c+ delivered — Test review grid

After clicking **Test On Current Page** in the editor:
1. The blue summary line still updates in place at the bottom of the editor.
2. `AnnotatedImageRenderer` produces `<PATH_DB>/page_diagnostics/page-HP-test-annotated.png` (always, regardless of the saved profile's `output.save_annotated_png`).
3. `AROcrTestResultsScene` opens (non-modal — keeps the editor reachable):
   - Header: blue summary line.
   - Left: `TableView<OcrTestResultRow>` — one row per `ElementDTO`. Columns:
     - `✓ Approved` — per-row checkbox, in-memory only today.
     - `Quality` — `EXACT_CONTAIN` (dark green) / `OVERLAP` (olive) / `PROXIMITY` (orange) / `NONE` (grey).
     - `Tag` — element's tagName.
     - `DOM Text` — `ElementDTO.someText` after the resolver ran.
     - `OCR Text` — joined primary-tier OCR words from the test run.
     - `xPath (tail)` — last 60 chars; full path on tooltip.
     - Rows are sorted by quality (EXACT first, NONE last) so the user reviews the best matches before chasing the misses.
     - Approved rows get a light-green tint.
   - Right: `ImageView` of `page-HP-test-annotated.png` — green = OCR words, red = DOM rects, thick green = EXACT_CONTAIN matches.
   - Bottom: `Approved: N / M` counter, `Mark All Approved`, `Clear All Approvals`, `Close`.

Approval state is **not yet persisted** — it lives in memory for the duration of the modal. Persisting approvals into a profile column (e.g. `output.approved_xpaths`) is the next iteration if it proves useful.
**Owner:** Osvaldo Martini
**Depends on:** Roadmap 2a ✅ (delivered), Roadmap 2b (planned)
**Relates to:** Roadmap 3 (shares DB-migration conventions; independent feature)

## Phase 4a delivered scope

- Migration `M20260427_OcrConfig` — creates `ocr_config_profile` + `ocr_config_param` tables for Postgres / SQLServer / TEXT (SQLite) / Access, seeds `default` profile with current hardcoded values.
- Model: `OcrConfigProfile`, `OcrConfigParam` (entities) + `OcrConfig` (runtime denormalized view with typed getters and JSON array accessor).
- Repository `OcrConfigRepository` — CRUD + `resolveActive(homebankingId, homeUrlId)` (most-specific-wins order).
- Service `OcrConfigService` — caches per `(hbId, homeUrlId)` pair, `invalidateAll()` on save.
- `ColorMapper` — pure OpenCV class with 4 HSV ops (`replace_in_hsv_range`, `keep_in_range_binarize`, `desaturate_to_black_white`, `invert`). Run on screenshot before OCR when `color_mapping.ops` is non-empty. Debug output `page-HP-colormapped.png` written so the user can eyeball what the op does.
- OcrConfig threaded through: `WebPageOcrService` (languages/PSM/DPI per config), `OcrDomCorrelator` (per-tag proximity thresholds — input / button / default).
- UI: "OCR Config" button with cogwheel icon on `ARScannedElementPane` (row 1, col 0 of the top grid, directly under Page Scanner). Opens `AROcrConfigScene` (modal).
- `AROcrConfigPane` — profile selector combo, name/description fields pre-filled from `HomeUrlDTO.orgName`, scope summary label, params TableView with inline-edit on the Value column, Save / Save As New / Delete / Close buttons.

What's intentionally minimal in 4a (deferred to 4b): category tabs, HSV color picker widget, Test On Current Page preview. The flat table with inline value editing covers the 80% use case: edit `proximity_px_input` to a new value, edit `color_mapping.ops` JSON, save, done.

## Why this roadmap exists

Real-world validation on the BancaStato login page (Roadmap 2a smoke test, 2026-04-24) surfaced three classes of OCR failures that are *data-driven*, not algorithmic:

| Failure | Cause | Right fix |
|---|---|---|
| Blue info box entirely missed | low-contrast blue-on-blue text defeats default Tesseract binarization | toggle CLAHE preprocessing pass |
| "Send" button read as nothing | white-on-red button glyphs → Tesseract can't segment | run a second OCR pass on color-remapped image |
| `<input>` elements got `NONE` (label sits in a sibling) | hardcoded proximity threshold (30 px) | per-tag threshold override |

Hardcoding any of these fixes is the wrong move: **every new bank / page will need different knobs**. We expose every knob as configuration, persist named profiles per homebanking, and let a human iterate on them from a UI when a new page misbehaves.

## Outcomes

1. **DB-persisted OCR configuration**, scoped to homebanking (with optional URL-pattern override for finer granularity).
2. **New "OCR Configuration" button on the AR Web Factory page** (likely `ARScannedElementPane` — confirm at build) that opens a config editor.
3. **Live-preview capability**: edit a param → click "Test On Current Page" → reruns OCR on the cached `page-HP.png` with the new config → shows the before/after correlation results side-by-side. No re-screenshotting, no slow feedback loop.
4. **Default profile** that reproduces the current Phase 2a behavior exactly, so enabling this feature is a no-op until the user creates overrides.

## Configuration categories

Every knob is typed (int / double / bool / string / enum / json) and persisted as a single key/value row, so adding new params later needs no schema migration.

| Category | Example params | Purpose |
|---|---|---|
| **correlation** | `proximity_px_global`, `proximity_px_input`, `proximity_px_button`, `dedupe_iou`, `tier_priority` | Tune the `OcrDomCorrelator` |
| **engine** | `languages`, `psm_mode`, `user_defined_dpi`, `tess_variables_extra` (JSON map) | Tune Tess4J itself |
| **screenshot** | `scope` (`viewport` / `full_page`), `scroll_into_view_first`, `pre_capture_delay_ms` | Control what the image shows |
| **preprocessing** | `enable_clahe_pass`, `clahe_clip`, `clahe_tile`, `adaptive_block`, `adaptive_c`, `upscale_factor` | Secondary OCR pass for low-contrast text |
| **color_mapping** | array of `{name, mode, hsv_lower, hsv_upper, replacement_bgr}` ops | **The big one** — color remap before OCR |
| **button_detection** | `enable_red`, `enable_blue`, `enable_any`, `red_hsv_range`, `blue_hsv_range`, `min_area`, `aspect_min/max`, `solidity_min` | Tune the button-ROI pipeline from Phase 2b |
| **output** | `save_annotated_png`, `save_raw_ocr`, `save_correlation`, `log_level` | Control pipeline artifacts |

## The `color_mapping` feature (the biggest new capability)

This is what lets the user fix problem pages without changing code. New class:

```
com.allinweb.ch.vision.ColorMapper
```

It takes the screenshot `Mat` (BGR) and applies an ordered list of ops from the active profile. Each op:

```json
{
  "name": "bancastato-red-button",
  "mode": "replace_in_hsv_range",
  "hsv_lower": [0, 80, 80],
  "hsv_upper": [10, 255, 255],
  "replacement_bgr": [0, 0, 0]
}
```

### Supported modes

- **`replace_in_hsv_range`** — pixels matching the HSV range get replaced with `replacement_bgr`; rest unchanged. Use to turn red button backgrounds into black so white text OCRs cleanly.
- **`keep_in_range_binarize`** — pixels in range → black, everything else → white. Produces a Tesseract-friendly binary mask for a specific color channel. Use when color-isolating a single UI chrome element.
- **`desaturate_to_black_white`** — converts anything colored into black; near-white stays white. "Flatten the page to monochrome" for maximum contrast.
- **`invert`** — full-image invert (bright-on-dark → dark-on-bright). Handy for dark-mode pages.

### Application order

```
1. Raw screenshot (page-HP.png)
2. Apply color_mapping ops in order
3. [Preprocessing pass if enable_clahe_pass=true] CLAHE + adaptive threshold + upscale
4. Tesseract OCR
5. Merge words from all passes, dedupe by bbox IoU
6. Run OcrDomCorrelator with active correlation thresholds
```

### Why this fixes the three failures from 2a

- Blue info box → add a `desaturate_to_black_white` op (or CLAHE toggle) → info-box text now reads.
- Red Send button → add a `replace_in_hsv_range` op mapping BancaStato red to black → second OCR pass catches "Send".
- Input/label pairing → bump `proximity_px_input` to 80 → input rows get `PROXIMITY` from their label.

All from the config UI, no code change per page.

## Database schema

Two tables. Profile rows are named, homebanking-scoped, with optional URL pattern. Params live in a key/value child so we never migrate to add a knob.

```sql
CREATE TABLE ocr_config_profile (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    name              VARCHAR(128) NOT NULL,
    description       TEXT,
    homebanking_id    VARCHAR(64) NULL,   -- NULL = global (only default should be global)
    url_pattern       VARCHAR(512) NULL,  -- optional regex; applies within homebanking
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_name_hb (name, homebanking_id),
    INDEX ix_hb_pattern (homebanking_id, url_pattern)
);

CREATE TABLE ocr_config_param (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id        BIGINT NOT NULL,
    category          VARCHAR(64) NOT NULL,   -- correlation | engine | screenshot | preprocessing | color_mapping | button_detection | output
    name              VARCHAR(128) NOT NULL,
    value_type        ENUM('int','double','string','bool','json') NOT NULL,
    value             TEXT NOT NULL,          -- JSON-encoded for json type, plain otherwise
    FOREIGN KEY (profile_id) REFERENCES ocr_config_profile(id) ON DELETE CASCADE,
    UNIQUE KEY uq_profile_cat_name (profile_id, category, name),
    INDEX ix_profile (profile_id)
);
```

Seed migration writes **one `is_default=true` profile** with every current hardcoded value from `OcrDomCorrelator`, `WebPageOcrService`, `OcrPreprocessorOpenCv` — guarantees zero behavior change until the user edits a profile.

## Profile resolution at run time

New service: `com.allinweb.ch.facade.OcrConfigService` (singleton, cached).

```
resolveForUrl(homebankingId, url) → OcrConfig
  1. Find profiles where homebanking_id = HBID AND url_pattern matches URL → most-specific wins
  2. Else profiles where homebanking_id = HBID AND url_pattern IS NULL
  3. Else the single is_default=true profile (global)
  4. Cache {(HBID, URL) → profileId} for the JVM lifetime;
     invalidated on profile save.
```

Wired at the top of `PageOcrDumper.runAndDump(...)`:
```java
String url = driver.getCurrentUrl();
OcrConfig cfg = OcrConfigService.getInstance()
        .resolveForUrl(currentBotJob.getHomeBankingId(), url);
// cfg flows into WebPageOcrService, ColorMapper, OcrPreprocessorOpenCv, OcrDomCorrelator
```

## UI design

### Entry point
Button on the "AR Web Factory" page (most likely `ARScannedElementPane`; confirm exact parent during build). Button label: **"OCR Configuration"**. Opens `AROcrConfigScene`.

### Layout (`AROcrConfigScene` + `AROcrConfigPane`)
```
┌─────────────────────────┬──────────────────────────────────────────┐
│ Profiles                │  [ Correlation | Engine | Screenshot |   │
│  • default              │    Preprocessing | Color Mapping |       │
│  • bancastato-login     │    Button Detection | Output ]           │
│  • raiffeisen-default   │                                          │
│                         │  ┌──────────────────────────────────┐    │
│ [+ New] [Duplicate]     │  │  (param editor for active tab)   │    │
│ [Delete]                │  │                                  │    │
│                         │  └──────────────────────────────────┘    │
│ Scope:                  │                                          │
│  homebanking [ ___ ▼ ]  │  [ Save ] [ Save As New ]                │
│  url pattern [ _____ ]  │  [ Test On Current Page ]  ← live preview│
└─────────────────────────┴──────────────────────────────────────────┘
```

- Each param has a type-appropriate control (spinner, slider, toggle, combo, text field, hex color picker for the `color_mapping` ops).
- Tooltips on every param explaining what it does and which pipeline stage it affects.
- `Test On Current Page` is the killer feature — see next section.

### "Test On Current Page" flow

Zero browser interaction. Fully offline:

1. Read `PATH_DB/page_diagnostics/page-HP.png` (last captured screenshot).
2. Read `PATH_DB/page_diagnostics/page-HP-rects.json` and `elementDTO-HP.json`.
3. Build a transient `OcrConfig` from the current UI form values (not yet saved).
4. Run the full pipeline in-process: `ColorMapper` → optional preprocessing pass → Tesseract → `OcrDomCorrelator`.
5. Display a side-by-side table in the UI:
   - Left column: correlation results with the **saved** profile.
   - Right column: correlation results with the **current unsaved form**.
   - Rows colored green when quality improved, red when it regressed, yellow when unchanged.
6. Optionally: render a scaled-down thumbnail of `ColorMapper` output so the user can *see* what their HSV ranges do to the image.

This is what makes the system usable: tweak sliders → 1 s feedback → commit when happy.

## New Java classes

```
com.allinweb.ch.vision
└── ColorMapper.java                  // ordered HSV ops on Mat (BGR)

com.allinweb.ch.model
├── OcrConfigProfile.java             // entity (Lombok @Data)
├── OcrConfigParam.java               // entity
└── OcrConfig.java                    // denormalised runtime view (cat → key → value)

com.allinweb.ch.facade
├── OcrConfigService.java             // resolve / load / save / cache
└── OcrConfigRepository.java          // DB access (Postgres / SQLite / UCanAccess)

com.allinweb.ch.component.scene
└── AROcrConfigScene.java

com.allinweb.ch.component.pane
├── AROcrConfigPane.java              // full editor
└── AROcrConfigTestPane.java          // "Test On Current Page" results panel
```

## Modified existing classes

- `WebPageOcrService.createEngine()` → accepts `OcrConfig`, applies `languages`, `psm_mode`, `user_defined_dpi`, `tess_variables_extra`.
- `WebPageOcrService.recognize(image)` → overload that accepts `OcrConfig`, runs raw + optional preprocessed pass, merges.
- `OcrDomCorrelator.correlate(...)` → accepts `OcrConfig`, uses per-tag thresholds, reads `tier_priority` for ordering.
- `OcrPreprocessorOpenCv` → gets wrappers that apply `OcrConfig` values; existing static methods kept for backwards compat.
- `PageOcrDumper.runAndDump(...)` → resolves profile, threads it through.

## Build order

1. **DB migrations** — `M<YYYYMMDD>_OcrConfigProfile.java` + `M<YYYYMMDD>_OcrConfigParam.java`. Seed a `default` profile with current hardcoded values.
2. **Model + repository** — `OcrConfigProfile`, `OcrConfigParam`, `OcrConfig` runtime DTO, `OcrConfigRepository` with Postgres + SQLite + UCanAccess variants.
3. **`OcrConfigService`** — resolve + cache.
4. **`ColorMapper`** — pure OpenCV, unit-testable offline against `page-HP.png`.
5. Thread `OcrConfig` through `PageOcrDumper` → `WebPageOcrService` / `OcrDomCorrelator` / preprocessor. Verify default profile reproduces Phase 2a exactly.
6. **UI** — `AROcrConfigScene` + `AROcrConfigPane`, add entry button to the Factory page.
7. **"Test On Current Page"** — offline rerun pipeline + diff view.

Phases 1–5 ship the runtime. Phases 6–7 ship the UX. Runtime can be committed independently (profile editing via direct DB is a fine workaround until the UI lands, but the UI is what makes this feature valuable in practice).

## Verification checklist

- [ ] Default profile yields identical `ocr-HP.json` and `ocr-correlation-HP.json` to Phase 2a on the BancaStato baseline.
- [ ] Editing `proximity_px_input = 80` in a profile → `input#username` correlation tier changes from `NONE` to `PROXIMITY` with `ocrText = "User number"`.
- [ ] Adding a `color_mapping` op `replace_in_hsv_range` for BancaStato red → "Send" appears in `ocr-HP.json` and correlates to the button DTO as `EXACT_CONTAIN`.
- [ ] Adding a `desaturate_to_black_white` op → blue info-box text appears in `ocr-HP.json` (words like "Gentile", "cliente", "truffe").
- [ ] Profile resolution: create two profiles for the same homebanking with different `url_pattern`s. Picks on the matching URL use the correct one.
- [ ] "Test On Current Page" shows real before/after diff in under 2 seconds without hitting the browser.
- [ ] Saving a profile invalidates the in-memory cache (next pick picks up the new values).

## Open questions to resolve at build time

1. **Exact parent for the "OCR Configuration" button** — confirm `ARScannedElementPane` is the AR Web Factory page, or locate the right one.
2. **Config storage backend** — the pom has Postgres + SQLite + UCanAccess. We need the SQL dialect for each of the three, not just MySQL-style DDL shown above. Migration classes will include variants.
3. **HSV color picker widget** — JavaFX has no native HSV picker. Either (a) roll a 3-slider with a live preview swatch, or (b) reuse a public-domain FX color picker. Prefer (a) for zero new deps.
4. **Profile export/import as JSON** — probably yes, so users can share tuned profiles between dev/staging/prod installs. Trivial to add later; list as a nice-to-have.
5. **Hot-reload** — should profile edits take effect on the *next* pick automatically (cache invalidation), or require an explicit "Apply" step? Proposal: auto on save, since we already cache-invalidate on save.

---

## Positioning vs the other roadmaps

- **Roadmap 2b** (next): adds the raw capabilities (CLAHE preprocessing pass, button detection, annotated PNG). Currently hardcoded defaults.
- **Roadmap 4** (this): exposes those capabilities + existing ones as user-tunable config. Without 2b, there's less to configure; but the configuration system itself can be built standalone and the new 2b knobs plug into it as they land.
- **Roadmap 3** (DB fallback locator): independent. Shares the DB-migration conventions.

Recommended sequence: ship 2b → ship this Roadmap 4 runtime → ship this UI → then Roadmap 3. But Roadmap 4 can slot in at any point after 2a; it's purely additive.
