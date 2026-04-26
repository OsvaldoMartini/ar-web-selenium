# FINAL ROADMAP — chat-restart resume doc

**Generated:** 2026-04-26
**Purpose:** the previous chat got too heavy and needs restart. This file is the single source of truth for what's done, what's half-done, and what's left across all four roadmaps.

---

## Spec inventory

| File | Status |
|---|---|
| `ROADMAP_1_PAGE_DIAGNOSTICS.md` | ✅ delivered |
| `ROADMAP_2_OCR_CV_CORRELATION.md` | ✅ Phase 2a + 2b delivered |
| `ROADMAP_3_BEST_TEXT_DB_FALLBACK.md` | ✅ Phase 3a + 3b + 3c-i + 3c-ii + 3c-iii (in-repo) delivered. Cross-repo Engine wire-in pending. |
| `ROADMAP_4_OCR_CONFIG_SYSTEM.md` | ✅ Phase 4a + 4b + 4c + 4c+ delivered |
| `OCR_CONFIG_PARAMS.md` | reference doc, current |
| `IN_PROGRESS.md` | resume-notes file (this supersedes it) |
| `FINAL_ROADMAP.md` | **this file** |

The four `ROADMAP_N_*.md` files have per-phase delivery details; this file is the cross-cutting summary.

---

## ✅ Done — high-level

### Roadmap 1 — Page diagnostics
On every Page Scanner / hover-pick the scanner writes 7 artefacts into `<PATH_DB>/page_diagnostics/`:
- `page-HP.html`, `page-HP-live.html`, `page-HP-meta.json`, `page-HP-shadow.json`
- `page-HP-iframes.json` (+ per-iframe HTML for same-origin frames)
- `page-HP-overlays.json` — cookie/consent/modal fingerprint
- `page-HP-rects.json` — per-element rects with occlusion + stacking-context chain
All `outputJsonElementDTO` outputs (elementDTO-HP/-PS/-PG, AI-ElementDTO-*) also redirect into the same subfolder.

### Roadmap 2 — OCR + CV correlation
- `WebPageOcrService.recognizeMultiPass` runs raw OCR + optional CLAHE preprocessing + optional red/blue/any button detection, deduped by IoU.
- `OcrDomCorrelator` emits `EXACT_CONTAIN / OVERLAP / PROXIMITY / NONE` per element with per-tag proximity thresholds.
- `ColorMapper` applies HSV ops (`replace_in_hsv_range`, `keep_in_range_binarize`, `desaturate_to_black_white`, `invert`) before OCR.
- `AnnotatedImageRenderer` writes `page-HP-annotated.png` overlaying OCR boxes + DOM rects + EXACT_CONTAIN highlights.
- All toggles consume Roadmap 4 config.
- Tessdata (eng+ita+fra+deu, ~69 MB) and Windows OpenCV DLL (~51 MB) bundled in `src/main/resources/`.

### Roadmap 3 — Best-text resolver + locator persistence + recovery
- **Phase 3a**: `TextSimilarity` + `ElementTextResolver` (7 candidate sources + OCR ×1.5 corroboration). Mutates `ElementDTO.someText` + `definedName` at SEARCH_TOOL pick time and at `runScan`.
- **Phase 3b**: `M20260428_ElementLocator` migration (4 dialects). `element_locator` keyed on `(homebanking_id, home_url_id, defined_name)` with frozen `*_original` + mutable `*_current` + `pick_count`. `ElementLocatorRepository.upsertOnPick` wired into both pick paths.
- **Phase 3c-i**: drift detection inside `upsertOnPick` writes one `element_locator_rename` row per changed field.
- **Phase 3c-ii**: `ElementRecoveryService.findOrRecover(driver, locator)` walks a 7-strategy ladder (`XPATH_CURRENT > XPATH_ORIGINAL > CSS_SELECTOR > ATTRIB_ID > ATTRIB_NAME > TEXT_FUZZY > COORDS`) with confidence scores and auto-writes a rename row on non-direct hits.
- **Phase 3c-iii (in-repo)**: `ARScannedElementPane` bot-run loop calls `findOrRecover` as the final fallback after the existing 3-step matcher (`findMatchingTargetElementByXPath` → `findMatchingTargetElement` → `searchElement`) returns null.

### Roadmap 4 — OCR Config system
- **Phase 4a**: `M20260427_OcrConfig` migration (4 dialects). `ocr_config_profile` + `ocr_config_param` tables. `OcrConfigService.reconcileDefaultProfile` auto-fills new canonical params on every boot. Profile resolution `home_url_id > homebanking_id > default`.
- **Phase 4b**: tabbed editor (`AROcrConfigPane`) with type-aware controls (CheckBox/Spinner/ComboBox/TextField/TextArea), tooltips from `OcrConfigMeta`, `+ Add template op` helper for `color_mapping`, profile auto-versioning (`{orgName} v1, v2, ...`).
- **Phase 4c**: `Test On Current Page` runs the OCR pipeline offline against cached artefacts.
- **Phase 4c+**: `AROcrTestResultsScene` review grid with colour-coded `Quality` column, per-row Approved checkbox persisted via `output.approved_xpaths`, `definedName` column (monospace blue), full-xPath text field below the table, annotated screenshot side-by-side.
- **Tier 1 fixes (2026-04-26)**: Test reads whichever of `elementDTO-HP.json` or `elementDTO-PS.json` is most recently modified; Approved state persists.
- **Logging (2026-04-26)**: new `com.allinweb.scanner` logger → `ar_web_scanner_scanner.log` captures `PIPELINE` (per-pick), `LOCATOR INSERT/UPDATE/BATCH`, `LOCATOR DRIFT`, `LOCATOR RECOVERY`, `TEST_ON_CURRENT_PAGE`.

### Migration boot path (cross-cutting fix, 2026-04-24)
`ARControlPanel` now calls `performInitializer.initialize()` on every boot regardless of whether the DB exists. Previously gated to first-time setup, so `MigrationRunner.runPending` never ran on existing installs and any new migration was silently skipped.

---

## ✅ DOM-First resolver knobs — fully wired (2026-04-26)

User asked to make these resolver-quality fixes configurable so a tuned profile can be created via the UI:
- Lower OCR weights so DOM text wins when both exist (fixes orphan rows like `access_oblems` / `forgotten_password2` / `minimum_req_te`).
- Skip `<input type="hidden">` (CSRF tokens) and `<label>`-only elements in `upsertOnPick`.
- Refuse to persist locators with `definedName` shorter than N chars.

### What landed end-to-end
- `OcrConfigDefaults.CANONICAL` carries 6 new canonical params (defaults match current hardcoded values — no behavior change):
  - `correlation.ocr_exact_contain_weight` (double, default 0.85)
  - `correlation.ocr_overlap_weight` (double, default 0.70)
  - `correlation.ocr_proximity_weight` (double, default 0.55)
  - `output.skip_hidden_inputs` (bool, default false)
  - `output.skip_label_only_elements` (bool, default false)
  - `output.min_defined_name_length` (int, default 0)
- `OcrConfigMeta` carries descriptions for all 6 + `RANGES` for the 3 doubles + 1 int.
- `ElementTextResolver` has `resolveAll(ElementDTO[], Path, OcrConfig)` overload that reads the 3 OCR weights from cfg. The legacy 2-arg signature delegates with cfg=null.
- `ElementLocatorRepository.upsertOnPick` looks up the active cfg via `OcrConfigService.resolveFor(hbId, homeUrlId)` and applies the three skip filters before any DB write. Skips are logged at DEBUG.
- `SimpleWebSocketServer.SEARCH_TOOL` (both branches) and `PerformListElements.runScan` resolve cfg once and pass it to `ElementTextResolver.resolveAll(...)` so OCR weight knobs take effect at pick time.

### How to use it
1. Open **OCR Config** on the AR Web Factory page.
2. Click **Save As New**, name the profile e.g. `DOM-First (Anti-Drift)`.
3. Tab **correlation** → set:
   - `ocr_exact_contain_weight = 0.55`
   - `ocr_overlap_weight = 0.45`
   - `ocr_proximity_weight = 0.35`
4. Tab **output** → set:
   - `skip_hidden_inputs = true`
   - `skip_label_only_elements = true`
   - `min_defined_name_length = 3`
5. Click **Save**. Next Page Scanner / hover-pick uses the new values.

### Quality bug evidence (smoke output 2026-04-26)
The default profile produced these orphan rows that the DOM-First profile is designed to eliminate:
```
access_oblems     | 1   ← OCR misread "problems"
access_problems   | 2   ← later runs read it correctly — orphan above stays
forgotten_password2 | 3 ← OCR read "?" as "2"
minimum_req_te    | 2   ← OCR garbled
oos9fqtw6diffmbr3jnpfw | 3   ← CSRF token slug — should not have been persisted
a                 | 2   ← anchor with no usable text source
user_number_2     | 3   ← <label> persisted alongside <input>
```

---

## 🔴 Pending — full roadmap items not started

### Roadmap 2 — full-page scroll-stitched screenshot
Knob exists (`screenshot.scope=full_page`) but isn't wired. Footer elements (y > viewport_height) currently come back as `NONE` in correlation. Implement scroll-stitching in `WebScreenshotCapture` and have it fire when `scope == "full_page"`.

### Roadmap 2 — visual HSV color picker
The `color_mapping.ops` JSON is currently edited in a TextArea + `+ Add template op` button. A real slider-based picker with live preview swatch would reduce friction. Maybe ~100 LOC of JavaFX.

### Roadmap 2 — cross-platform native libs
Bundled DLL is Windows-only. Add `.dylib` (macOS) and `.so` (Linux) under `src/main/resources/opencv/` if a non-Windows install is ever needed. Tess4J already has cross-platform natives bundled.

### Roadmap 3 — cross-repo Engine wire-in (Phase 3c-iii outside this repo)
The AR Web Engine (separate repo, runs `AR_Web_Engine.jar`) has its own resolution path that mirrors `ARScannedElementPane`'s. Same `ElementRecoveryService.findOrRecover` fallback should be added there. The model + repo + service are designed to be portable — pull in via Maven dep or copy.

### Roadmap 4 — `output.log_level` knob is decorative
Persists in DB but nothing reads it. Either wire it to a runtime logger reconfiguration, or remove from `OcrConfigDefaults`/`OcrConfigMeta` to avoid misleading users.

### Roadmap 4 — profile export / import as JSON
Nice-to-have for dev → prod profile sharing. Trivial — Gson over the entity + params list.

---

## Recommended next session order

1. **Finish the DOM-First resolver knobs** (steps 1–4 in the half-done section above). Estimated ~30 min once the chat is fresh.
2. User creates the **"DOM-First (Anti-Drift)"** profile via UI and re-runs the smoke test. Verify no more orphan rows for the same logical element.
3. **Persistent locator hygiene** — one-shot SQL or migration to drop the orphan rows that exist in DB right now (or keep them — they're inert).
4. **Roadmap 2 full-page screenshot** if the footer-element NONE results are biting QA.
5. **Cross-repo Engine wire-in** when there's a real bot-run drift incident.

---

## Files / classes the next session needs to know

### Code orientation
- Pick paths: `SimpleWebSocketServer.SEARCH_TOOL` (hover-pick + mobile) and `PerformListElements.runScan` (Page Scanner). Both run `dumpRects → runAndDump → resolveAll → upsertOnPickBatch → outputJsonElementDTO`.
- Bot-run resolution: `ARScannedElementPane` around line 4886 (`findMatchingTargetElementByXPath` → `findMatchingTargetElement` → `searchElement` → `ElementRecoveryService.findOrRecover`).
- Config plumbing: `OcrConfigDefaults` (canonical) → `OcrConfigMeta` (UX) → `OcrConfigService.resolveFor(hbId, homeUrlId)` → consumers.

### Memory files (from prior sessions)
Located at `C:\Users\osval\.claude\projects\D--Projects-AllinWeb-ar-web-selenium\memory\`:
- `runtime_paths.md` — real plugin / config paths on this dev machine.
- `plugin_ui_mapping.md` — "Page Scanner" button → `searchListAsync` (not `pageScanner`).
- `migrations_boot_path.md` — the gating bug we already fixed.
- `feedback_no_mvn.md` — **don't run `mvn` from Claude; user runs builds themselves.**

### Logs
- `ar_web_scanner_scanner.log` — scanner pipeline + locator audit (this session's new file).
- `ar_web_scanner_operations.log` — `logOperations` calls including the recovery info line.
- `ar_web_scanner_base.log` — root logger.

### Current branch
`VERSION-4-2-NEW`. Last pushed commit (`73c64a77`) covers Phase 3b only.

**Uncommitted in working tree at chat-restart time:**
- Phase 3c-i drift detection (in `ElementLocatorRepository.java`)
- Phase 3c-ii `ElementRecoveryService.java` (new file)
- Phase 3c-ii compile fix (multi-catch → single `RuntimeException`)
- Phase 3c-iii bot-run wire-in (in `ARScannedElementPane.java`)
- DOM-First knobs partial: 6 new entries in `OcrConfigDefaults.java` only (resolver / repo / wire-in still TODO per "What's NOT done" above)
- Spec updates in `ROADMAP_3_BEST_TEXT_DB_FALLBACK.md` and `IN_PROGRESS.md`
- This `FINAL_ROADMAP.md`

**Recommended first action in the new chat:** review `git status` + `git diff`, then commit + push the 3c work before continuing with the DOM-First wiring. The partial `OcrConfigDefaults.java` change is safe to ship even unfinished — the new entries default to current hardcoded values, so behavior is unchanged until the resolver/repo wire-in lands.
