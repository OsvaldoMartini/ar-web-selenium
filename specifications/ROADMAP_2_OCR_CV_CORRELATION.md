# ROADMAP 2 — OCR + OpenCV screenshot correlation with `elementDTO-HP.json`

**Status:** pending approval of module-wiring decision (A / B / C below)
**Owner:** Osvaldo Martini
**Depends on:** Roadmap 1 (meta.json gives DPR + viewport + scroll — needed for coordinate alignment)
**Feeds into:** Roadmap 3 (OCR EXACT_CONTAIN result becomes a high-weight candidate for `someText` / `definedName`)

---

## 📌 REMINDER (scoring rule — do not drop)

OCR / CV results **must be cross-validated against the previously scored text candidates** derived from the DTO metadata (`someText`, `attribId`, `attribName`, `placeholder`, sibling `<label>`, `aria-*`, humanized `id`/`name`) using **coordinate proximity** as the bridge. When an OCR word sits inside or near an element's `getBoundingClientRect()`, its text becomes a **corroborating signal**: if the OCR text agrees (exact or fuzzy) with one of the DOM-derived candidates, that candidate's score is multiplied — not replaced. This produces a coordinate-anchored consensus for `someText` / `definedName`, materially boosting accuracy for icon-only buttons, image-rendered labels, and Angular custom elements where DOM text is thin or missing.

This note is binding on the scoring step inside `OcrDomCorrelator` and on the ranking stage in Roadmap 3 (`ElementTextResolver`). OCR alone is never the sole source of `someText` unless there are no DOM-derived candidates.

---

## Goal

Produce a text map of what is *visually* on the page, then link each OCR word/button to the closest DOM element from the DTO. This is the visual fallback for when xPaths and text-based locators both fail.

## What already exists in `ar-web-mobile` — reuse, do not duplicate

Package `com.allinweb.ch.vision.ocr` at `D:\Projects\AllinWeb\ar-web-mobile\src\main\java\com\allinweb\ch\vision\ocr\`:

| Class | Role |
|---|---|
| `OpenCvNativeLoader` | one-time native init (`Class.forName(...)`) |
| `OcrOpenCvUtils` | `bufferedImageToMat`, `matToBufferedImage` |
| `OcrPreprocessorOpenCv` | `preprocess` (×2 upscale, LANCZOS4), `preprocessButton`, `detectRedButtons`, `detectBlueButtons`, `detectAnyButtons` |
| `Tess4JOcrService` / `HybridOcrService` | engine wrappers |
| `OcrResult` / `OcrWord` | output DTOs |
| `GeometryFieldExtractor` | spatial helpers |
| `ImagePreprocessor`, `MlKitClient`, `OpenCvLoader` | auxiliary |

Reference implementation: `D:\Projects\AllinWeb\ar-web-mobile\src\test\java\com\allinweb\ch\facade\VisionOcrFromFileTest.java` demonstrates full-image OCR + color-button detection + ROI OCR + "map back to original coordinates" when preprocessing scales.

## Module wiring — decision required before build

- **(A)** Extract `vision.ocr` into a shared module `ar-web-vision`; both `ar-web-mobile` and `ar-web-selenium` depend on it. *Cleanest; largest diff.*
- **(B)** Add a Maven dependency from `ar-web-selenium` → `ar-web-mobile`. *Pragmatic; couples the two projects.*
- **(C)** Copy the `vision.ocr` package into `ar-web-selenium`. *Fastest; worst for drift.*

Recommendation: **(A)** if time allows, **(B)** otherwise.

## New classes to add in `ar-web-selenium`

```
com.allinweb.ch.vision
├── WebPageOcrService.java          // orchestrator
├── WebScreenshotCapture.java       // full-page PNG via Selenium (scroll-stitch if needed)
├── OcrDomCorrelator.java           // matches OCR words ↔ ElementDTO
└── OcrCorrelationResult.java       // serializable output
```

## Pipeline (runs on each pick, or on demand)

```
1. WebScreenshotCapture.fullPage(driver)
     → PNG saved to  PATH_DB/page_diagnostics/page-HP.png
     → also capture: viewport size, DPR, scrollX/Y, documentTotalHeight
       (reuses JS_META from Roadmap 1 so data lands in page-HP-meta.json)

2. WebPageOcrService.run(pngPath)
     a) Full-image OCR via Tess4J (eng+ita+fra+deu, PSM_AUTO, DPI 300)
        → List<OcrWord> wordsRaw
     b) OpenCV button passes (red / blue / any)
        → List<OcrWord> buttonsRaw (label prefix "RED-", "BLUE-", "ANY-")
     c) Merge + dedupe by bbox IoU ≥ 0.6
        → List<OcrWord> all

3. OcrDomCorrelator.correlate(all, elementDTO, meta)
     For each ElementDTO e:
       - compute e.bbox from page-HP-rects.json (Roadmap 1 output)
       - adjust for DPR and scroll so OCR pixel space == DTO CSS space:
           ocr_x_css = ocr_x_px / dpr
           ocr_y_css = ocr_y_px / dpr   (+ scroll offsets for stitched captures)
       - find OCR words whose center is inside e.bbox  → EXACT_CONTAIN
       - else words whose bbox intersects e.bbox       → OVERLAP
       - else nearest word within 30 px of e.center    → PROXIMITY
       - else                                          → NONE
       - keep top-K by (containment > proximity, then confidence)

       APPLY REMINDER SCORING RULE:
         - for each kept OCR word, fuzzy-compare against every DOM-derived
           text candidate already scored for this element
         - if agreement (Levenshtein ratio ≥ 0.85 OR token-set ratio ≥ 0.9):
             that DOM candidate's final score *= 1.5
         - else: OCR word is recorded as its own candidate with base weight
                 scaled by match-quality tier (EXACT_CONTAIN > OVERLAP > PROXIMITY)

     Emit OcrCorrelationResult {
        elementId, xPath,
        domText,             // existing someText
        ocrText,             // joined words inside bbox
        ocrNearestText,      // joined nearest words (if no containment)
        ocrConfidence,
        ocrWordBoxes,
        matchQuality,        // EXACT_CONTAIN | OVERLAP | PROXIMITY | NONE
        corroboratedCandidates  // DOM candidates whose score was boosted
     }

4. Persist (all under PATH_DB/page_diagnostics/):
     page-HP.png                          raw screenshot
     ocr-HP.json                          full OCR dump (words + buttons)
     ocr-correlation-HP.json              per-element match
     page-HP-annotated.png                debug: OCR + DOM boxes drawn
```

## Coordinate alignment — the tricky part

Three spaces must agree before correlation:

| Space | Source | Unit |
|---|---|---|
| DTO `coordinates` | `ElementDTO.coordinates` | CSS px |
| `getBoundingClientRect()` | Roadmap 1's `page-HP-rects.json` | CSS px (viewport-relative) |
| Screenshot pixels | `page-HP.png` | device px = CSS × DPR |

Convert screenshot → CSS: divide by `devicePixelRatio`. For stitched full-page captures, also add `scrollY` for rows that were off-screen at capture time. `page-HP-meta.json` already carries DPR, viewport, and scroll — that's why Roadmap 1 precedes Roadmap 2.

## Match-quality tiers (input to Roadmap 3)

- **EXACT_CONTAIN** — OCR word center inside DTO bbox → very strong signal.
- **OVERLAP** — OCR word bbox intersects DTO bbox but center is outside → strong.
- **PROXIMITY** — nearest OCR word within ≤ 30 px of DTO center → medium.
- **NONE** — no OCR text nearby (icon-only button, unlabeled input) → weak; fall back to color-button label (`Button(RED)-N`).

## Build order (after Roadmap 1 ships)

1. Agree module wiring (A / B / C).
2. Add `WebScreenshotCapture` (Selenium full-page PNG; handle scroll-stitching when `documentHeight > innerHeight`).
3. Add `WebPageOcrService` that calls the existing `Tess4JOcrService` + `OcrPreprocessorOpenCv` detectors.
4. Add `OcrDomCorrelator` and wire it into the `SEARCH_TOOL` branch right after `dumpRects(...)`.
5. Implement the reminder's scoring rule inside `OcrDomCorrelator.correlate`.
6. Validate on `input#password`: OCR should place the word "Password" inside its DTO bbox; correlation tier should be `EXACT_CONTAIN`; the existing DTO candidate with text "Password" should have its score multiplied by 1.5.

## Verification checklist (post-build)

- [ ] `page-HP.png` matches viewport + scroll captured in `page-HP-meta.json`.
- [ ] OCR returns non-empty word list; top-confidence words are recognizable (Italian "Accedi", "Password", etc.).
- [ ] For the password-input baseline: `matchQuality == EXACT_CONTAIN` and `ocrText` contains "Password".
- [ ] Red/blue button detector locates at least one button; button ROI OCR yields readable text for large labeled buttons.
- [ ] Coordinate round-trip: DTO `coordinates` (CSS px) reconstructs to screenshot pixel center within ±2 px after applying DPR.
- [ ] `ocr-correlation-HP.json` has exactly one entry per DTO element; `corroboratedCandidates` is populated for at least the labeled interactive elements.
