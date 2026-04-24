# ROADMAP 1 — Page Diagnostic Dump to `PATH_DB/page_diagnostics/`

**Status:** ✅ built (extended scope: iframe patch + Tier A occlusion + Tier B overlay fingerprint folded in)
**Owner:** Osvaldo Martini
**Dependencies:** none
**Feeds into:** Roadmap 2 (OCR correlation), Roadmap 3 (fallback locator DB)

## Delivered scope

Beyond the original five files, this build also captures iframes, cookie/consent overlays, and per-element occlusion + stacking context. Total artifacts per pick:

```
<PATH_DB>/page_diagnostics/
├── page-HP.html                 driver.getPageSource() (top frame)
├── page-HP-live.html            document.documentElement.outerHTML
├── page-HP-meta.json            viewport, DPR, scroll, readyState, UA
├── page-HP-shadow.json          shadow-host survey (top document)
├── page-HP-iframes.json         per-iframe rect / src / sameOrigin / size
├── page-HP-iframe-N.html        HTML of each same-origin iframe (one file per)
├── page-HP-overlays.json        cookie/consent/modal fingerprint
│                                (OneTrust, Cookiebot, Didomi, Usercentrics,
│                                 role=dialog, <dialog open>, popover, and
│                                 matching IDs/classes — walks top doc,
│                                 open shadow roots, and same-origin iframes)
└── page-HP-rects.json           per DTO xPath, with:
                                  - rect (CSS px, viewport-relative)
                                  - computedStyle (position, zIndex, display,
                                    visibility, opacity, pointer-events, overflow)
                                  - elementAtCenter + occluded flag (elementFromPoint)
                                  - inTopLayer (:modal, :popover-open)
                                  - ancestorPointerEventsNone
                                  - stackingAncestors[] (ancestors that create
                                    their own stacking context)
                                  - frame info when iFrameXPath was used
                                    (crossOrigin flag, iframeXPath)
```

## Occlusion signal (the gold standard)

For each picked element, the dumper runs `document.elementFromPoint(centerX, centerY)` and compares the returned node to the xPath-resolved node. If they differ AND the returned node is not a descendant of the picked node, the element is **visually occluded** — a cookie banner or other overlay is on top of it at pick time. That's the cleanest "was the pick actually clickable?" signal.

## Goal

Every time the user enters clone mode + picks an element, write a matched set of diagnostic artifacts next to `elementDTO-HP.json`. These artifacts let us verify xPath / proximity / OCR claims against the ground truth of the rendered DOM.

## Output layout

```
<PATH_DB>/
├── elementDTO-HP.json              (unchanged, existing)
├── AI-ElementDTO-HP.json           (unchanged, existing)
└── page_diagnostics/               (NEW — auto-created)
    ├── page-HP.html                Selenium getPageSource()
    ├── page-HP-live.html           document.documentElement.outerHTML via JS
    ├── page-HP-meta.json           viewport, DPR, URL, UA, scroll, readyState, timestamp
    ├── page-HP-shadow.json         shadow-root survey
    └── page-HP-rects.json          getBoundingClientRect() for every xPath in the DTO
```

## Files and cadence

| File | Source | When |
|---|---|---|
| `page-HP.html` | `driver.getPageSource()` | clone mode ON |
| `page-HP-live.html` | JS `document.documentElement.outerHTML` | clone mode ON |
| `page-HP-meta.json` | viewport / DPR / URL / UA / readyState | clone mode ON |
| `page-HP-shadow.json` | shadow-root survey | clone mode ON |
| `page-HP-rects.json` | `getBoundingClientRect()` for every xPath in the DTO | per pick |

## Implementation

### Helper class

Create `com.allinweb.ch.util.PageDiagnosticDumper` — self-contained, no new dependencies beyond Selenium + Gson.

Two public methods:

```java
PageDiagnosticDumper.dumpPage(WebDriver driver, String pathDb, String prefix);
PageDiagnosticDumper.dumpRects(WebDriver driver, List<String> xPaths, String pathDb, String prefix);
```

Both resolve the subfolder internally:
```java
Path diagDir = Paths.get(pathDb, "page_diagnostics");
Files.createDirectories(diagDir);
```

This keeps call sites clean — callers pass `PATH_DB`, the helper handles the subfolder.

### JavaScript payloads

- **`JS_OUTER_HTML`** — `return document.documentElement.outerHTML;`
- **`JS_META`** — returns JSON with: `url, userAgent, devicePixelRatio, innerWidth, innerHeight, scrollX, scrollY, documentWidth, documentHeight, readyState, timestamp`.
- **`JS_SHADOW_SURVEY`** — walks the element tree; every host whose `shadowRoot` is non-null gets recorded as `{host, hostTag, mode}`.
- **`JS_RECTS`** — accepts `arguments[0]` = list of xPaths; for each, resolves via `document.evaluate(...)`, extracts `tagName/id/className` and `getBoundingClientRect()`.

### Call sites

**Site 1 — clone-mode activation** (one-shot baseline)

In the existing `checkCloneElement.setOnMouseClicked` handler, immediately before the `periodicPickOneCloneThread` launch:

```java
if (checkCloneElement.isSelected()) {
    PageDiagnosticDumper.dumpPage(
            performActions.getCurrentDriver(),
            arPropertyManager.getProperty(ARPropertyEnum.PATH_DB),
            "page-HP");
    // ... existing periodicPickOneCloneThread launch
}
```

**Site 2 — `SEARCH_TOOL` case in `SimpleWebSocketServer`** (per pick)

Right after the existing `outputJsonElementDTO(..., "elementDTO-HP", jsonPath)` call:

```java
List<String> xPaths = splitDTO.getElementDetails().stream()
        .map(ElementDTO::getXPath)
        .filter(Objects::nonNull)
        .toList();
PageDiagnosticDumper.dumpRects(
        performActions.getCurrentDriver(), xPaths, jsonPath, "page-HP");
```

The existing `jsonPath` variable already resolves to `PATH_DB`, so no new property lookup is needed.

## Build order

1. Create `PageDiagnosticDumper.java` under `com.allinweb.ch.util`.
2. Wire Site 1 (clone-mode handler).
3. Wire Site 2 (`SEARCH_TOOL` case, both `scannerGrid` and `mobile-return-server` branches).
4. Trigger one pick → confirm all five files appear under `PATH_DB/page_diagnostics/`.

## Verification checklist (post-build)

- [ ] `page-HP.html` and `page-HP-live.html` both non-empty.
- [ ] `page-HP-meta.json` contains a sensible `devicePixelRatio` (usually 1.0–2.0).
- [ ] `page-HP-rects.json` has exactly N entries where N = `splitDTO.getElementDetails().size()`.
- [ ] Each rects entry has `found: true` (xPath resolves live).
- [ ] For the password-input baseline test: rect of `input#password` matches DTO `coordinates` (after DPR adjustment).
- [ ] `page-HP-shadow.json` is an empty array `[]` for plain Angular apps; non-empty warns of shadow-DOM breakage risk in locator resolution.
