# OCR Config Parameters — canonical reference

**Scope:** every knob exposed by the **OCR Config** modal (`AROcrConfigPane`) maps 1:1 to an entry in `com.allinweb.ch.model.OcrConfigDefaults.CANONICAL`. Descriptions, ComboBox enums, and Spinner ranges live in `com.allinweb.ch.model.OcrConfigMeta`. Code consumers call `OcrConfig.getBool / getInt / getDouble / getString / getJsonArray`.

Adding a new param is a three-file change:
1. Append an entry to `OcrConfigDefaults.CANONICAL`.
2. Add a description (and optional enum/range) to `OcrConfigMeta`.
3. Wire the getter into the pipeline code that consumes it (e.g. `WebPageOcrService`, `OcrDomCorrelator`, `PageOcrDumper`, `ColorMapper`).

No DB migration is needed — `OcrConfigService.reconcileDefaultProfile()` runs on every boot and inserts any new canonical entries into the `default` profile automatically. When a user opens an older custom profile, `AROcrConfigPane.loadIntoForm` detects missing canonical keys, auto-adds them with defaults, and shows a blue `NEW PARAMS DETECTED (…)` banner nudging Save-As-New to persist them into a new version.

---

## Category: `correlation`

Controls how OCR word bounding boxes are matched against DOM element rects in `OcrDomCorrelator.correlate`.

| Name | Type | Default | Range | Purpose |
|---|---|---|---|---|
| `proximity_px_global` | double | `30.0` | 0..500 | Default max center-to-center distance (CSS px) for a PROXIMITY match. |
| `proximity_px_input` | double | `30.0` | 0..500 | Used for `<input>`, `<textarea>`, `<select>`. Raise to `~80` so inputs pick up their sibling `<label>` above them (the "input rect doesn't contain label text" gotcha from BancaStato validation). |
| `proximity_px_button` | double | `30.0` | 0..500 | Used for `<button>`. Icon-only buttons usually have the label inside, so the default is fine. |
| `dedupe_iou` | double | `0.6` | 0..1 | IoU threshold for merging duplicate OCR words coming from multiple passes. Higher = only merge near-identical boxes. |

---

## Category: `engine`

Forwarded directly to Tess4J in `WebPageOcrService.createEngine`.

| Name | Type | Default | Notes |
|---|---|---|---|
| `languages` | string | `eng+ita+fra+deu` | `+`-joined Tess language codes. Only codes with traineddata bundled in `src/main/resources/tesseract/tessdata/` are honoured; others are silently dropped. |
| `psm_mode` | int (enum) | `3` | Tesseract Page Segmentation Mode. `3` AUTO · `4` single column · `6` single uniform block · `11` sparse text · `12` sparse text with OSD. |
| `user_defined_dpi` | int | `300` | 72–600. `300` is the sweet spot for web screenshots; bump to `400` when fonts are very small. |

---

## Category: `screenshot`

Controls how `WebScreenshotCapture` takes the baseline image.

| Name | Type | Default | Notes |
|---|---|---|---|
| `scope` | string (enum) | `viewport` | `viewport` = visible area only (current). `full_page` = scroll-stitched — **NOT YET IMPLEMENTED**; option `s2` deferred in Roadmap 2. |
| `pre_capture_delay_ms` | int | `0` | Wait N ms after page ready before snapping. Useful for fade-in animations. |

---

## Category: `preprocessing`

Controls the optional second OCR pass in `WebPageOcrService.recognizeMultiPass` that runs on an upscaled, CLAHE-thresholded copy of the screenshot — catches low-contrast text the raw pass misses (e.g. dark-blue-on-light-blue info boxes).

| Name | Type | Default | Range | Notes |
|---|---|---|---|---|
| `enable_clahe_pass` | bool | `false` | — | Master switch. Off by default because it roughly doubles OCR time. |
| `clahe_clip` | double | `3.0` | 0..20 step 0.5 | CLAHE clip limit. Higher = more aggressive contrast. |
| `clahe_tile` | int | `8` | 1..64 | CLAHE tile size (both dimensions). Smaller = finer local contrast. |
| `adaptive_block` | int | `21` | 3..99 step 2 | Adaptive-threshold block size. **MUST BE ODD.** The Spinner steps by 2 to preserve this. |
| `adaptive_c` | int | `10` | -50..50 | Adaptive-threshold constant C subtracted from the local mean. |
| `upscale_factor` | int | `2` | 1..8 | Integer upscale applied before OCR. **Must match** the ×2 inside `OcrPreprocessorOpenCv.preprocess` — changing here without also changing the preprocessor will misalign the mapped-back word bboxes. |

---

## Category: `color_mapping`

The big one. A JSON array of ordered ops applied to the screenshot BEFORE Tesseract runs, via `com.allinweb.ch.vision.ColorMapper`. Use this to turn problem colors (e.g. BancaStato red, dark-mode text) into Tesseract-friendly black-on-white.

| Name | Type | Default | Notes |
|---|---|---|---|
| `ops` | json | `[]` | Ordered array of op objects. See op schema below. |

### Op schema

Each op is a JSON object with a required `mode` and mode-specific keys:

```json
{
  "name": "bancastato-red-button",
  "mode": "replace_in_hsv_range",
  "hsv_lower": [0, 80, 80],
  "hsv_upper": [10, 255, 255],
  "replacement_bgr": [0, 0, 0]
}
```

HSV ranges follow OpenCV convention: **H 0..179**, **S 0..255**, **V 0..255**. BGR is also 0..255.

#### Supported modes

- **`replace_in_hsv_range`** — pixels whose HSV falls inside `[hsv_lower, hsv_upper]` get replaced with `replacement_bgr`. Everything else is untouched. Use to turn red button backgrounds into black so white text OCRs cleanly.
- **`keep_in_range_binarize`** — produces a pure black/white image: pixels in range → black, everything else → white. Good for isolating a single coloured UI element for dedicated OCR.
- **`desaturate_to_black_white`** — converts anything colored (`S > s_thresh`, default 40) OR dark (`V < v_dark`, default 180) to black; everything else becomes white. Optional `s_thresh` and `v_dark` per op. Use to flatten the whole page to Tesseract-friendly monochrome.
- **`invert`** — full-image bitwise invert. Useful for dark-mode pages.

### Cookbook: red Send button on BancaStato

```json
[
  {
    "name": "bancastato-red-button-bg",
    "mode": "replace_in_hsv_range",
    "hsv_lower": [0, 80, 80],
    "hsv_upper": [10, 255, 255],
    "replacement_bgr": [0, 0, 0]
  }
]
```

### Cookbook: the blue info box with "Gentile cliente…" text

Two options:
- Toggle `preprocessing.enable_clahe_pass = true` — adds a CLAHE pass that catches low-contrast text, no color op needed.
- OR add a `desaturate_to_black_white` op — flattens everything so Tesseract sees black text on white.

### Editor helper

The modal's `color_mapping` tab has an **+ Add template op** button at the bottom. It appends a `replace_in_hsv_range` template to the ops array. Edit the HSV and BGR values, then Save As New.

---

## Category: `button_detection`

Controls the third OCR pass in `ButtonDetectionService` — detects colored button rectangles via OpenCV HSV filtering, crops each, upscales ×3, and re-OCRs. Words are merged into the main list with original-space bboxes.

| Name | Type | Default | Notes |
|---|---|---|---|
| `enable_red` | bool | `false` | Red button pass. Catches white-on-red CTAs. |
| `enable_blue` | bool | `false` | Blue button pass. |
| `enable_any` | bool | `false` | Color-agnostic pass via Canny + morphology. Slowest, catches buttons in themes the color-specific passes miss. |

All three can be enabled together — duplicates are removed by the `correlation.dedupe_iou` threshold.

---

## Category: `output`

Controls what `PageOcrDumper` writes to `<PATH_DB>/page_diagnostics/`.

| Name | Type | Default | Notes |
|---|---|---|---|
| `save_raw_ocr` | bool | `true` | Write `ocr-HP.json` (all OCR words with confidence and bbox). Keep on for debugging. |
| `save_correlation` | bool | `true` | Write `ocr-correlation-HP.json` (per-element DOM↔OCR match quality). Keep on — consumed by Roadmap 3. |
| `save_annotated_png` | bool | `false` | Write `page-HP-annotated.png` (OCR boxes green, DOM rects red, EXACT_CONTAIN thick green, overlaid on the screenshot). Expensive to render — enable while tuning. |
| `log_level` | string (enum) | `INFO` | DEBUG / INFO / WARN / ERROR. **Reserved — not yet consumed**; the pipeline currently logs at class-level slf4j settings. |

---

## How profile resolution works at runtime

At `PageOcrDumper.runAndDump` time, `OcrConfigService.resolveFor(homebankingId, homeUrlId)` walks this order, most-specific wins:

1. A profile where `home_url_id` matches exactly (the page-specific override).
2. A profile where `homebanking_id` matches and `home_url_id IS NULL` (the bank-wide default).
3. The `is_default=true` profile (global fallback).

If none exists (shouldn't happen post-migration), the pipeline uses hardcoded fallbacks and logs a warning.

Cached per `(hbId, homeUrlId)` pair; `invalidateAll()` is called on every save/delete via `AROcrConfigPane`.

---

## Editor at a glance

- Profiles table (top) — Name · Scope · Created · Modified. Click a row to load its params into the editor.
- Name field — auto-populated as `{HomeUrl.orgName} - Login Page v{N+1}`. Edit freely; v-suffix auto-bumps on Save As New if the name clashes.
- Per-category tabs — type-aware controls:
  - `bool` → CheckBox
  - `int` → Spinner with min/max/step from `OcrConfigMeta.RANGES`
  - `double` → Spinner with decimal step
  - `string` with known enum → ComboBox from `OcrConfigMeta.ENUMS`
  - `string` without enum → TextField
  - `json` → TextArea (plus **+ Add template op** helper for `color_mapping`)
- Hover a param name or the **ⓘ** icon to see its description.
- **Test On Current Page** button — runs the OCR pipeline offline against the last cached `page-HP.png`, `page-HP-meta.json`, `page-HP-rects.json`, `elementDTO-HP.json`. Shows a blue summary line: `Test: X OCR words · Y EXACT · Z OVERLAP · N PROX · M NONE`. No DB write, no browser hit.
