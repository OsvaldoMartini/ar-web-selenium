# ROADMAP 7 — Video Recording of Bot Job Executions

**Status:** 📋 proposed
**Owner:** Osvaldo Martini
**Dependencies:** none for the recorder; ROADMAP 6 (RTM) is **complementary** — links recordings to requirements once both ship
**Feeds into:** failure analysis, audit/replay evidence, support tickets
**Kickoff:** 2026-05-06 (parallelizable with ROADMAP 6) — or 2026-06-08 if running sequentially
**Target final delivery:** **2026-06-08 (parallel track)** / **2026-07-09 (sequential track)**

## Why

When a Pre-Launch Test (`ARScannedElementPane.executeJob()`) fails, all we have today is:

- the Excel report row with status + log message
- maybe a screenshot (if the action took one)
- the operations log line

That isn't enough to answer "what did the screen actually look like during step N?" The user has to manually re-run the failing job to reproduce. Video recording closes that loop:

- one MP4 per job run, kept under `<PATH_DB>/recordings/`
- timestamped frame-accurate seek-to-step (links from each Excel report row open the video at the matching offset)
- on-failure short-burst capture (last 10 s) even when full recording is off, so failures are never lost
- works for both the visible Chrome window (Selenium / JCEF) AND the scanner UI itself, configurable

This is **operationally critical** for support engagements where the customer can't reproduce on demand.

## Decisions locked in

| Decision | Choice |
|---|---|
| Capture mechanism | **FFmpeg subprocess** capturing the screen region of the JCEF/Chrome window. Robust, well-known, no Java native lib in our process. JCodec / Monte Media are *fallbacks if ffmpeg is missing* (degraded quality / Java-only path). |
| ffmpeg distribution | Bundle a static x64 `ffmpeg.exe` under `<user.dir>/ffmpeg/`, exposed through new `ARPropertyEnum.PATH_FFMPEG`. Auto-derived if unset, mirroring the `PATH_WEBDRIVER` pattern. |
| Container / codec v1 | MP4 / H.264 (`libx264`, `-preset veryfast`, `-crf 28`). Trade size for legibility. Configurable via property. |
| Frame rate | Default 10 fps. Configurable. UI moves slowly; 10 fps is enough and keeps file sizes ~5–15 MB / minute. |
| Audio | None. UI tests are silent. |
| Capture region | Detect the Chrome/JCEF window bounds via Selenium `driver.manage().window().getRect()` + the JCEF browser bounds. Fall back to whole screen if that fails. |
| Storage layout | `<PATH_DB>/recordings/<bot_job_name>/<yyyyMMdd_HHmmss>/<job_name>.mp4` next to a sidecar `index.json` with per-step (start_offset_ms, end_offset_ms, status). |
| Retention | None enforced in v1. Add a "purge older than N days" option later. |
| Failure-only mode | Ring buffer on disk (rolling 10s segments), promoted to a permanent file when a step fails. Off by default in v1; toggleable. |
| Linking from Excel | The execution report's per-step row gets a `Recording` column with a clickable `file:///...` URL pointing at the MP4 plus `#t=<start_offset>` (Chrome / VLC understand it). |
| Settings surface | New section in `ARConfigurationScene` (Recording on/off, fps, codec preset, region: window-only / full-screen, failure-only mode). |
| Cross-platform | x64 Windows only. macOS/Linux out of scope (matches the rest of the scanner). |

## Repository layout

### New files

```
src/main/java/com/allinweb/ch/
├── recording/
│   ├── VideoRecorder.java                 NEW   high-level service (start/stop/markStep/captureFailureBurst)
│   ├── FfmpegProcess.java                 NEW   wraps `ProcessBuilder` lifecycle, gdigrab capture
│   ├── RecordingIndexWriter.java          NEW   per-step offsets → index.json
│   ├── RecordingPaths.java                NEW   path resolution + filename templating
│   ├── CaptureRegion.java                 NEW   immutable rect (x,y,w,h) + factory from WebDriver
│   ├── RecordingMode.java                 NEW   enum: OFF, FULL, FAILURE_ONLY
│   └── RingBufferRecorder.java            NEW   only used when mode=FAILURE_ONLY
├── component/pane/
│   └── ARRecordingSettingsPane.java       NEW   embedded in ARConfigurationScene
├── readersAndWriters/
│   └── RecordingLinkColumn.java           NEW   ExcelWriter chain link adding `Recording` column
└── util/
    └── FfmpegProbe.java                   NEW   detects bundled ffmpeg, falls back to PATH lookup
```

### Modified files

```
src/main/java/com/allinweb/ch/
├── component/pane/ARScannedElementPane.java   wrap executeJob() with VideoRecorder.start/stop
│                                              call markStep() before each instruction
├── facade/PerformActions.java                 emit "step boundary" event for markStep()
├── facade/PerformInitializer.java             init VideoRecorder singleton
├── component/scene/ARConfigurationScene.java  embed ARRecordingSettingsPane
├── util/ARPropertyEnum.java                   +PATH_FFMPEG, PATH_RECORDINGS, RECORDING_MODE,
│                                              +RECORDING_FPS, RECORDING_CODEC_PRESET,
│                                              +RECORDING_CRF, RECORDING_REGION
├── util/ARPropertyManager.java                setDefaults*: derive PATH_RECORDINGS = PATH_DB/recordings,
│                                              PATH_FFMPEG = user.dir/ffmpeg/ffmpeg.exe
└── readersAndWriters/ExcelWriter.java         register RecordingLinkColumn for "report" purpose
```

### Runtime layout

```
<user.dir>/
└── ffmpeg/
    └── ffmpeg.exe                       (bundled, ~80 MB static x64)

<PATH_DB>/
└── recordings/
    └── <jobName>/
        └── 20260601_141233/
            ├── <jobName>.mp4
            └── index.json               { steps: [ { i:0, t0:0, t1:1230, status:"PASS" }, ... ] }
```

## Phase status

| Phase | Working days | Window | Due | Status |
|---|---|---|---|---|
| 1. ffmpeg PoC + region capture | 3 | 2026-05-06 → 2026-05-08 | **2026-05-08 (Fri)** | Pending |
| 2. VideoRecorder service | 4 | 2026-05-11 → 2026-05-14 | **2026-05-14 (Thu)** | Pending |
| 3. Wire into executeJob | 2 | 2026-05-15 → 2026-05-18 | **2026-05-18 (Mon)** | Pending |
| 4. Settings UI | 3 | 2026-05-19 → 2026-05-21 | **2026-05-21 (Thu)** | Pending |
| 5. Storage + index.json | 2 | 2026-05-22 → 2026-05-25 | **2026-05-25 (Mon)** | Pending |
| 6. Failure-only ring buffer | 3 | 2026-05-26 → 2026-05-28 | **2026-05-28 (Thu)** | Pending |
| 7. Excel link column + per-step seek | 4 | 2026-05-29 → 2026-06-04 | **2026-06-04 (Thu)** | Pending |
| 8. Validation + docs | 2 | 2026-06-05 → 2026-06-08 | **2026-06-08 (Mon)** | Pending |

*Calendar skips weekends. Italian Republic Day (Tue 2026-06-02) is treated as non-working.*

If sequential to ROADMAP 6, shift every window by 22 working days → final delivery **2026-07-09**.

## Phase 1 — ffmpeg PoC + region capture (due 2026-05-08)

**Goal:** prove that we can capture the JCEF/Chrome window region to MP4 from a Java-launched ffmpeg subprocess.

**Deliverables:**
- Bundle `ffmpeg.exe` under `<user.dir>/ffmpeg/` (gitignored; document download URL in README)
- `FfmpegProbe` resolves the path; logs which one was picked (bundled, PATH, none)
- Standalone smoke entry point: takes a window title, captures 10 seconds, writes `smoke.mp4`
- ffmpeg cmdline: `ffmpeg -y -f gdigrab -framerate 10 -offset_x X -offset_y Y -video_size WxH -i desktop -c:v libx264 -preset veryfast -crf 28 -pix_fmt yuv420p out.mp4`

**Acceptance:** running the smoke entry point while a Chrome window is in the foreground produces a playable `smoke.mp4` showing the window content.

## Phase 2 — VideoRecorder service (due 2026-05-14)

**Goal:** singleton with the lifecycle the rest of the codebase will call.

**Deliverables:**
- `VideoRecorder.start(jobName, captureRegion)` → returns a session handle
- `VideoRecorder.markStep(int stepIndex, long elapsedMs, String status)` → appends to the in-memory index
- `VideoRecorder.stop()` → terminates ffmpeg (via stdin `q\n`, fallback `Process.destroyForcibly` after 5s), flushes `index.json` next to the MP4
- Thread-safe; never throws into the executeJob path — failures log a WARN and the recorder no-ops
- Hooked into `PerformInitializer` for singleton init / shutdown hook

**Acceptance:** unit-style test that starts the recorder, sleeps 3 s while marking 5 step boundaries, stops, and verifies the MP4 is non-empty and `index.json` has 5 entries.

## Phase 3 — Wire into executeJob (due 2026-05-18)

**Goal:** pre-launch test runs are recorded when the mode is `FULL`.

**Deliverables:**
- `ARScannedElementPane.executeJob` (around `executorServicePreLaunch` submission) wrapped with `VideoRecorder.start/stop`
- `PerformActions` instrumented to call `VideoRecorder.markStep` immediately before each instruction
- Capture region resolved from the live `WebDriver` window rect; fallback to full primary screen
- Region must include the JCEF browser AND the scanner toolbar overlay so the user can see what was clicked

**Acceptance:** run a 6-step job through "Launch Bot Job"; one MP4 is produced under `<PATH_DB>/recordings/...`; `index.json` shows exactly 6 step boundaries.

## Phase 4 — Settings UI (due 2026-05-21)

**Goal:** user-facing toggle.

**Deliverables:**
- `ARRecordingSettingsPane` embedded in `ARConfigurationScene`:
  - Mode: OFF / FULL / FAILURE_ONLY (radio)
  - FPS: number spinner (5–30, default 10)
  - Preset: combo (`ultrafast`, `superfast`, `veryfast`, `faster`, `fast`)
  - CRF: spinner (18–32)
  - Region: combo (`Browser window only`, `Full screen`)
  - Path override (read-only display of resolved `PATH_RECORDINGS`)
- All bound to new `ARPropertyEnum` keys; persisted on Save through `ARPropertyManager`

**Acceptance:** changing values and reopening the app preserves the choice; toggling Mode = OFF skips Phase 3 wiring (no MP4 produced).

## Phase 5 — Storage + index.json finalization (due 2026-05-25)

**Goal:** final on-disk schema, robust rotation.

**Deliverables:**
- Filename template: `<jobName>/<yyyyMMdd_HHmmss>/<jobName>.mp4`
- `index.json` schema versioned: `{ "version": 1, "jobName": ..., "fps": 10, "startedAt": ISO8601, "steps": [...] }`
- Atomic write of `index.json` (write to `.tmp`, then rename) so a crash mid-job leaves either nothing or a complete file
- Disk-space guard: if `<PATH_DB>` free space < 1 GB, recorder logs WARN and disables itself for the run

**Acceptance:** crash the JVM mid-recording; restart; the partial MP4 is intact and `index.json` either exists fully or is absent (never half-written).

## Phase 6 — Failure-only ring buffer (due 2026-05-28)

**Goal:** mode `FAILURE_ONLY` records continuously to a rolling buffer, promotes the last 10 s to a permanent file when a step fails.

**Deliverables:**
- `RingBufferRecorder` runs ffmpeg in segment mode: `-f segment -segment_time 2 -segment_wrap 5` writing 5 × 2-second segments
- On step failure detected by `PerformActions`, segments are concatenated into a single MP4 named `<step_index>_FAIL.mp4` under the same job directory
- Buffer cleared at end-of-run if no failure happened

**Acceptance:** run a job with one intentional failure (e.g. wrong xPath); produces exactly one `<n>_FAIL.mp4` containing roughly the 10 seconds preceding the failure.

## Phase 7 — Excel link column + per-step seek (due 2026-06-04)

**Goal:** the Excel report becomes the navigation index for the recording.

**Deliverables:**
- `RecordingLinkColumn` ExcelWriter chain link adds a `Recording` column to the per-step rows
- Cell value: hyperlink with display text `Open at 0:23` and target `file:///<absolute>/<jobName>.mp4#t=23`
- A footer row gets a hyperlink to `index.json` for tooling consumption
- Coverage sheet from ROADMAP 6 (if shipped) gets a `Last recording` column with the same link pattern (last-run only)

**Acceptance:** open the produced `.xlsx`; click the Recording link on row 4; default media player opens the MP4 seeked to that step's offset.

## Phase 8 — Validation + docs (due 2026-06-08)

**Goal:** ship-ready.

**Deliverables:**
- End-to-end test on each `RecordingMode` (OFF / FULL / FAILURE_ONLY)
- Long-run soak: a 30-minute job; verify file size stays in expected range and ffmpeg doesn't leak handles
- Update `CLAUDE.md` with a new "Video recording" subsection
- Update `README.md` with the bundled-ffmpeg note and the `<PATH_DB>/recordings/` path
- Move this roadmap status to ✅ built

**Acceptance:** all phases ✅; clean run produces a playable MP4 + a clickable link in the Excel report.

## Out of scope (v2 candidates)

- Annotation overlay (red box around the clicked element, drawn into the video)
- Side-by-side recording: scanner UI + browser
- WebM / VP9 alternative codec
- Cloud upload (S3 / Azure Blob) on completion
- Embedded HTML5 player in a JavaFX `WebView` ("Play recording" button on the report row)
- macOS / Linux capture (`avfoundation` / `x11grab`)

## Risks

- **ffmpeg subprocess shutdown** — leaving an orphan `ffmpeg.exe` after a JVM crash is the #1 risk. Mitigation: register a JVM shutdown hook in Phase 2 that always tries `q\n` then `destroyForcibly`. Add a process-name sweep at scanner startup that kills any prior `ffmpeg.exe` whose parent PID is gone.
- **gdigrab DPI scaling** — Windows DPR > 1.0 makes the captured pixel rect not match Selenium's CSS pixel rect. Mitigation: in Phase 1, multiply the rect by the Windows scale factor (read via `GetDpiForMonitor` through JNA — already on classpath).
- **Disk pressure on long runs** — 10 fps + CRF 28 ≈ 8 MB/min. A 4-hour job ≈ 2 GB. Phase 5's free-space guard is mandatory; document the math in README.
- **JCEF window vs Selenium window** — recording must follow whichever browser is actually being driven. The current scanner uses both at different times. Phase 3 must consult `ARWebDriver` to decide.
- **Bundled ffmpeg license** — LGPL build is fine to redistribute; pick that flavor when bundling and note the license in the README and in the repo `NOTICES` file.

## Calendar summary

| Track | Mode | Final delivery |
|---|---|---|
| ROADMAP 6 (RTM) — alone | sequential | 2026-06-05 |
| ROADMAP 7 (Video) — alone | sequential after RTM | 2026-07-09 |
| ROADMAP 6 + 7 in parallel | concurrent | **both done by 2026-06-08** |

The two are independent — different packages, different DB tables, different UI panes. Run in parallel if there's bandwidth; otherwise RTM first because it's the smaller and lower-risk of the two.
