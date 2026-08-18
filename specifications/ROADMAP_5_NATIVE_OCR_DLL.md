# Roadmap 5: Native OCR DLL (MultiTest-OCR)

Replace the in-process Java OCR stack (Tess4J + OpenCV Java bindings) with a
native Windows DLL written in C++ and called from Java via JNA. The DLL bundles
Tesseract, Leptonica, and OpenCV statically and embeds the four tessdata
language packs as Windows resources, so deployment is a single DLL plus the
Java bridge.

## Why

- Eliminate the JNA-style overhead of Tess4J (it already crosses a native
  boundary; we just replace the wrapper with our own and gain control)
- Drop ~80MB of OpenCV native binaries currently committed to the scanner repo
  under `src/main/resources/opencv/` and the four `tessdata/*.traineddata`
  files; both move into the DLL
- Simplify deployment to one binary instead of three (OpenCV DLL, Tesseract
  DLL, Leptonica DLL) plus the tessdata folder
- Centralize OCR primitives so the sibling `ar-web-mobile` repo can later link
  the same DLL instead of maintaining its own Tess4J stack

## Decisions locked in

| Decision | Choice |
|---|---|
| Java to native binding | JNA (no JNI codegen) |
| Project location | `D:\Projects_DevOps\multi_ocr` |
| Repo name | `MultiTest-OCR` (initial commit on `main`) |
| Tessdata strategy | Embedded as Windows `.rc` resources for `eng`, `ita`, `fra`, `deu` |
| API parity | Complete: full surface mirrors `WebPageOcrService` |
| Removal staging | Parallel: keep Java OCR working until the DLL is fully validated, remove in Phase 8 |
| Compiler | MSVC via Visual Studio 2022 Build Tools |
| Build orchestration | CMake 3.20+ with vcpkg manifest mode (`x64-windows-static` triplet) |
| Bitness | x64 only |

## Repository layout

### multi_ocr (`D:\Projects_DevOps\multi_ocr`, repo name MultiTest-OCR)

```
include/ar_ocr.h               public C ABI
src/api.cpp                    extern "C" exports
src/tesseract_engine.{h,cpp}   Phase 2
src/preprocessor.{h,cpp}       Phase 4 (CLAHE + upscale)
src/button_detector.{h,cpp}    Phase 5
src/tessdata_extractor.{h,cpp} Phase 3 (resource extract to %LOCALAPPDATA%)
src/error.{h,cpp}              thread-local last-error
resources/ar_ocr.rc            Phase 3 (embeds tessdata)
resources/tessdata/            eng/ita/fra/deu .traineddata copied from scanner
tests/java/OcrBridgeSmoke.java standalone smoke test (Phase 1)
tests/fixtures/                PNG samples + expected words (Phase 2 onward)
tools/build.ps1                phase-aware build wrapper
tools/smoke.ps1                standalone smoke runner
CMakeLists.txt                 builds ar_ocr SHARED
CMakePresets.json              phase1-msvc + x64-windows-static presets
vcpkg.json                     manifest with tesseract, leptonica, opencv4
```

### Scanner bridge (`D:\Projects\AllinWeb\ar-web-selenium`)

```
src/main/java/com/allinweb/ch/ocr/bridge/
  OcrBridge.java               JNA Library interface (singleton via Native.load)
  OcrBridgeSmokeTest.java      mvn-built version of the smoke test
  OcrConfigC.java              Phase 2 (JNA Structure)
  OcrWordC.java                Phase 2 (JNA Structure)
  OcrBridgeService.java        Phase 6 (mirrors WebPageOcrService API)
```

The `ocr.bridge` package is intentionally separate from `vision/`, which is
deleted in Phase 8.

## Phase status

| Phase | Status | Commits / Notes |
|---|---|---|
| 1. Scaffolding and toolchain proof | Done | `c8ed207`, `14282d8`, `06bd791` in MultiTest-OCR; smoke prints `ar_ocr version: 0.1.0-phase1` |
| 2. Single-pass OCR | Pending | Blocked on `VCPKG_ROOT` setup |
| 3. Embedded tessdata | Pending | |
| 4. Multi-pass + CLAHE | Pending | |
| 5. Button detection + ROI OCR | Pending | Split into 5a (detect) and 5b (per-ROI OCR) if needed |
| 6. Java bridge service | Pending | Adds `ARPropertyEnum.OCR_ENGINE` toggle |
| 7. Side-by-side validation and cutover | Pending | |
| 8. Remove Java OCR | Pending | Final sweep; deletes 17 vision classes plus pom deps plus native binaries |

## Phase 1: Scaffolding and toolchain proof (DONE)

**Goal:** prove MSVC + CMake + JNA wire end to end before adding any real code.

**Delivered:**
- C++ project skeleton with `aro_version()` returning `"0.1.0-phase1"`
- `phase1-msvc` configure preset that does NOT use vcpkg, so Phase 1 builds
  are sub-minute
- `tools/build.ps1` defaults to Phase 1, takes `-Phase 2` for the vcpkg path
- Standalone Java smoke test in `tests/java/` plus `tools/smoke.ps1` runner
  that auto-discovers JNA from the local Maven repo

**Acceptance:** met. The smoke test prints `ar_ocr version: 0.1.0-phase1`.

## Phase 2: Single-pass OCR (~2-3 days)

**Prereq:** `VCPKG_ROOT` set, vcpkg bootstrapped. First Phase 2 configure
builds Tesseract + Leptonica + OpenCV from source via vcpkg manifest mode
(30-60 min, one time only).

**Exports added to `ar_ocr.h`:**
```c
typedef void* ARO_HANDLE;

typedef struct {
    int psm;
    int oem;
    const char* lang;
    int upscale;
    int clahe;
} OcrConfigC;

typedef struct {
    const char* text;
    float conf;
    int x, y, w, h;
} OcrWordC;

ARO_HANDLE   aro_open(const char* tessdata_path);
void         aro_close(ARO_HANDLE h);
int          aro_recognize(ARO_HANDLE h, const unsigned char* pixels,
                           int width, int height, int stride,
                           const OcrConfigC* cfg,
                           OcrWordC** out_words, int* out_count);
void         aro_free_words(OcrWordC* words, int count);
const char*  aro_last_error(ARO_HANDLE h);
```

**C++ work:**
- Wrap `tesseract::TessBaseAPI` lifecycle in `tesseract_engine.cpp`
- Per-handle mutex around `Recognize`/`GetIterator` (Tesseract is not thread-
  safe per engine instance)
- Convert raw pixel buffer to `Pix*` via `pixCreate` and `memcpy`
- Iterate `RIL_WORD` collecting text + bounding box + confidence
- Allocate `OcrWordC*` array on the heap; `aro_free_words` deletes it
- Thread-local last-error storage for `aro_last_error`

**Java work:**
- `OcrConfigC.java` and `OcrWordC.java` as JNA `Structure` mirrors
- `OcrBridge.java` extended with the new exports
- Manual fixture test: load a PNG, call `aro_recognize`, compare to
  `WebPageOcrService.recognize` output

**Acceptance:** on a fixed PNG fixture, the words and confidences match
`WebPageOcrService.recognize` 1:1 for text and within ~1px tolerance on
bounding boxes.

## Phase 3: Embedded tessdata (~1 day)

**Prereq:** copy `eng.traineddata`, `ita.traineddata`, `fra.traineddata`,
`deu.traineddata` from the scanner's `src/main/resources/tesseract/tessdata/`
into `multi_ocr/resources/tessdata/`. Combined size ~80MB; the resulting DLL
ends up around 90MB with all dependencies.

**C++ work:**
- `resources/ar_ocr.rc` declares each `.traineddata` as `RT_RCDATA` with a
  symbolic ID
- `tessdata_extractor.cpp` reads each resource via `FindResource` +
  `LoadResource` + `LockResource`, writes to
  `%LOCALAPPDATA%\ar_ocr\tessdata\<lang>.traineddata`
- Skip extraction if the target file exists with matching size (cheap idempotency)
- `aro_open(NULL)` triggers extraction and uses the resolved temp path; an
  explicit non-null `tessdata_path` overrides the embedded copy (useful for
  language-pack development)

**Acceptance:** delete `%LOCALAPPDATA%\ar_ocr\` and rerun the smoke; the
folder is recreated and OCR succeeds without any external tessdata dir.

## Phase 4: Multi-pass + CLAHE preprocessing (~2 days)

**Exports added:**
```c
int aro_recognize_multipass(ARO_HANDLE h, const unsigned char* pixels,
                            int width, int height, int stride,
                            const OcrConfigC* cfg,
                            OcrWordC** out_words, int* out_count);
```

**C++ work:**
- Port `OcrPreprocessorOpenCv` (CLAHE + upscale) using `cv::createCLAHE` and
  `cv::resize`
- Multi-pass loop matching the Java version: run at upscale factors
  configured in `OcrConfigC`, merge results by deduplicating overlapping
  bounding boxes (highest confidence wins)

**Java work:**
- Bridge function `aro_recognize_multipass` declared in `OcrBridge`
- Fixture test against `WebPageOcrService.recognizeMultiPass` on the same
  inputs

**Acceptance:** word-set parity (same text, same conf within 1%, same boxes
within 1px) on the regression fixtures.

## Phase 5: Button detection + ROI OCR (~3 days, splittable)

**Exports added:**
```c
typedef struct {
    int x, y, w, h;
    OcrWordC* words;
    int word_count;
} OcrButtonC;

int aro_detect_buttons_and_ocr(ARO_HANDLE h, const unsigned char* pixels,
                               int width, int height, int stride,
                               const OcrConfigC* cfg,
                               OcrButtonC** out_buttons, int* out_count);
void aro_free_buttons(OcrButtonC* buttons, int count);
```

**5a: ROI detection only**
- Port `ButtonDetectionService` color thresholding (red/blue) using
  `cv::inRange`, contour extraction, and bounding rectangles
- Validate count of detected ROIs against Java baseline on fixtures

**5b: Per-ROI OCR**
- For each ROI, extract `cv::Mat` slice and run Tesseract on it
- Each ROI gets its own `OcrWordC*` array; the overall `OcrButtonC*` array
  ties ROI rect to its words
- Mind the per-handle mutex: serialize Tesseract calls within a single handle

**Acceptance:** ROI count and per-ROI text match the Java implementation on
the regression fixtures.

## Phase 6: Java bridge service (~2 days)

**Goal:** drop-in replacement at the call sites, switchable via config.

**Java work:**
- `OcrBridgeService.java` mirrors `WebPageOcrService`'s static API exactly
  (same method signatures, same return types, same checked exceptions)
- New config key `ARPropertyEnum.OCR_ENGINE` with values `java` (default) or
  `native`
- Single helper: `OcrEngine.isNative()` reads the config once and is
  consulted at every call site
- Update callers to:
  ```java
  OcrResult result = OcrEngine.isNative()
      ? OcrBridgeService.recognize(...)
      : WebPageOcrService.recognize(...);
  ```
- Known caller list (from inventory):
  - `vision/WebPageOcrService` (self-call patterns)
  - `vision/ButtonDetectionService`
  - `vision/PageOcrDumper`
  - `vision/OcrDomCorrelator` (no direct OCR calls; just consumes results)
  - `facade/ElementRecoveryService`
  - `component/pane/AROcrTestResultsPane` (Accept OCR Name flow)

**Acceptance:** flipping `OCR_ENGINE=native` in config makes every OCR call
go through the DLL with no behavioral diffs in the test grid UI.

## Phase 7: Side-by-side validation and cutover (~2 days)

- Run both engines on the same input set in a debug mode that logs diffs
- Threading stress: 4 concurrent recognize calls, no crashes, no leaks
- Memory leak check: 1000 iterations against a representative image, watch
  RSS via Process Explorer
- Performance: native should be at worst neutral vs Tess4J; if it's slower,
  investigate before cutover
- Flip the default of `OCR_ENGINE` to `native`
- One full manual run through `AROcrTestResultsPane` workflow

**Acceptance:** all panes work with native default, no regressions reported.

## Phase 8: Remove Java OCR (~1 day)

Files to delete:
- `src/main/java/com/allinweb/ch/vision/` (entire package, 17 classes)
- `src/main/java/com/allinweb/ch/model/OcrConfigMeta.java`
- `src/main/java/com/allinweb/ch/component/pane/AROcrTestResultsPane.java`
- `src/main/java/com/allinweb/ch/component/pane/AROcrConfigPane.java`
- `src/main/java/com/allinweb/ch/component/scene/AROcrTestResultsScene.java`
- `src/main/resources/opencv/` (and any other platform variants)
- `src/main/resources/tesseract/`
- `specifications/ROADMAP_2_OCR_CV_CORRELATION.md`
- `specifications/ROADMAP_4_OCR_CONFIG_SYSTEM.md`
- `specifications/OCR_CONFIG_PARAMS.md`
- `OCRS README.md`

Pom changes:
- Drop `net.sourceforge.tess4j:tess4j:5.13.0`
- Drop `org.opencv:opencv:4.10.0`

Cleanup:
- Delete the `OcrEngine.isNative()` toggle helper, always-native now
- Update CLAUDE.md to remove the Tess4J + OpenCV reference and point at
  MultiTest-OCR

## Cross-cutting concerns

- **Threading:** every export holds a per-handle mutex around Tesseract.
  Callers wanting parallelism open multiple handles.
- **Errors:** every export returns an `int` status; `aro_last_error(handle)`
  returns the message. Java throws `OcrNativeException` on non-zero return.
- **Logging:** DLL writes to `%LOCALAPPDATA%\ar_ocr\log.txt` at WARN+ by
  default. Optional callback registration export added if the scanner
  decides it needs structured log forwarding.
- **DLL deployment:** copied to `<scanner-install>\native\ar_ocr.dll` at
  build time. `ARControlPanel` static block sets
  `System.setProperty("jna.library.path", ...)`.
- **Memory ownership:** all output arrays (`OcrWordC*`, `OcrButtonC*`) are
  allocated by the DLL and freed by the matching `aro_free_*` export. JNA
  copies into Java structures before the free call.

## Reference paths

- DLL build output: `D:\Projects_DevOps\multi_ocr\build\Release\ar_ocr.dll`
- Standalone smoke runner: `D:\Projects_DevOps\multi_ocr\tools\smoke.ps1`
- Phase-aware build: `D:\Projects_DevOps\multi_ocr\tools\build.ps1 [-Phase N]`
- vcpkg checkout: `D:\dev\vcpkg` (set `VCPKG_ROOT` accordingly)
- vcpkg triplet: `x64-windows-static`
- Tessdata source: `D:\Projects\AllinWeb\ar-web-selenium\src\main\resources\tesseract\tessdata\`
- Scanner bridge package: `com.allinweb.ch.ocr.bridge`
- Mobile sibling repo (unaffected): `D:\Projects\AllinWeb\ar-web-mobile`
