## Main-page migration verdict

Use a **hybrid React + virtualized grid architecture**. Keep the page header, menus, search box, buttons, confirmation dialogs, and status messages as normal React DOM.

My production choice is:

1. **AG Grid Community / virtualized DOM first**
2. **Glide Data Grid Canvas only if benchmarks prove Canvas is necessary**
3. Do not use Three.js, shaders, or C++ for the operational grid

Canvas should never own the entire page.

### Why

The current bottlenecks are broader than drawing:

| Current issue | Evidence |
|---|---|
| Every filtered row and cell becomes a DOM node | [GridTemp_A.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/GridTemp_A.tsx:323) |
| Search scans every row and searchable column synchronously | [GridTemp_A.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/GridTemp_A.tsx:132) |
| Sorting copies and sorts the complete filtered result | [GridTemp_A.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/GridTemp_A.tsx:165) |
| WebSocket messages are retained forever by copying the array | [useWebSocket.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/useWebSocket.tsx:100) |
| Every response can replace the complete Bot Job array | [MainDashboard.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/MainDashboard.tsx:406) |
| Backend returns complete snapshots | [MainDashboardService.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/facade/MainDashboardService.java:38) |
| Database loads every Bot Job joined with every Block | [PerformDataBase.java](/D:/Projects/AllinWeb/ar-web-selenium/src/main/java/com/allinweb/ch/facade/PerformDataBase.java:2236) |

A GPU renderer cannot fix full database loads, oversized snapshots, synchronous filtering, or unbounded message history.

AG Grid renders only visible rows and columns, preserving native controls and accessibility. [AG Grid DOM virtualization documentation](https://www.ag-grid.com/react-data-grid/dom-virtualisation/)

Glide is the strongest Canvas candidate: Canvas rendering, lazy cells, native scrolling, custom renderers, editing and selection. However, sorting/filtering remain application-owned, and its latest stable npm release is relatively old, so it requires a dependency-health checkpoint. [Glide Data Grid](https://github.com/glideapps/glide-data-grid), [DataEditor API](https://docs.grid.glideapps.com/api/dataeditor)

## Target architecture

```text
MainDashboard
    |
    +-- MainDashboard controller
    |      WebSocket, status, dialogs, launch/delete rules
    |
    +-- MainBotJobsGridModel
    |      normalized rows, search, sort, primary selection,
    |      bulk selection and stable Bot Job IDs
    |
    +-- MainBotJobsGridPort
           |
           +-- LegacyGrid adapter
           +-- VirtualDomGrid        <- recommended production default
           +-- CanvasGrid            <- benchmark-gated alternative
```

The grid renderer emits typed intents only:

```text
SELECT_PRIMARY
TOGGLE_BULK
TOGGLE_ALL_LOADED
OPEN_BOT_JOB
DELETE_ONE
DELETE_SELECTED
CHANGE_SORT
CHANGE_SEARCH
```

It must never call the WebSocket directly or duplicate launch/delete business rules.

## Existing behavior that must remain exact

- Single click selects a Bot Job.
- Double-click opens it.
- Primary selection and bulk checkbox selection remain independent.
- Checkbox and delete actions never bubble into row selection or opening.
- “Select all” means all loaded Bot Jobs, including filtered-out rows.
- Search continues covering name, description, organization, environment name/URL, type and status.
- Sort remains stable and cycles ascending → descending → original order.
- Launch remains unavailable when `launchable === false`.
- Deletion keeps confirmation, correlation, timeout and reconciliation.
- Identity always uses Bot Job ID—never the visible row index.

These contracts are already covered in [MainDashboard.test.tsx](/D:/Projects/AllinWeb/abr-react-ts-grid/src/components/MainDashboard.test.tsx:46).

## Migration checkpoints

### Phase 1 — Baseline

- Measure actual production Bot Job counts.
- Benchmark 1,000, 10,000, 50,000 and 100,000 synthetic rows.
- Record payload size, JSON parsing, filtering, sorting, first render, scrolling and retained heap.
- Save Chrome/Edge traces on the slowest supported Windows machine.

### Phase 2 — Isolated Main grid model

Create a Main-only feature folder:

```text
main-dashboard/
  MainBotJobsGridPort.ts
  MainBotJobsGridModel.ts
  MainBotJobsColumns.ts
  MainBotJobsIntents.ts
  MainBotJobsGrid.module.scss
```

Extract filtering, sorting, ordered IDs and selections while the existing grid continues rendering. This produces a rollback-safe checkpoint without changing behavior.

### Phase 3 — Correct data execution

- Store rows as `Map<botJobId, BotJobRow>`.
- Maintain a separate ordered array of IDs.
- Keep column definitions and event callbacks stable.
- Introduce bounded or callback-based WebSocket consumption for Main only.
- Batch visual updates once per `requestAnimationFrame`.
- Above a measured threshold, send the normalized searchable index once to a Web Worker; subsequent searches send only the query.

Web Workers move expensive processing away from the UI thread. [Web Workers API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API)

### Phase 4 — Virtualized DOM renderer

Implement AG Grid Community or TanStack Virtual behind:

```text
mainGridRenderer=legacy|virtual|canvas
```

AG Grid is the safer first production renderer because Main contains real checkboxes, delete buttons, keyboard navigation, tooltips and confirmation flows. TanStack Virtual is a lighter alternative when exact existing markup and styling matter more. [TanStack Virtual](https://tanstack.com/virtual/v3/docs/introduction)

### Phase 5 — Canvas benchmark

Only proceed if virtualized DOM misses the agreed performance budget.

Use Glide Data Grid with:

- Canvas-rendered text, status and block-count cells.
- Frozen checkbox/delete columns.
- DOM overlay controls for visible checkboxes and delete buttons.
- Existing React `ConfirmationDialog`.
- Device-pixel-ratio scaling and `ResizeObserver`.
- Canvas fallback to the virtualized renderer if the graphics context fails.

Only visible rows need DOM overlays, so thousands of Bot Jobs still produce approximately 20–40 live controls.

### Phase 6 — Canvas interaction contract

Use one delegated Pointer Events controller:

1. Convert pointer coordinates using scroll offset and display scaling.
2. Resolve the row and column hit zone.
3. Use pointer capture for column resizing or dragging.
4. Require press/release on the same hit target with a movement threshold for clicks.
5. Emit `OPEN_BOT_JOB` on activation/double-click.
6. Consume checkbox/delete activation before row behavior.
7. Use keyboard Enter/Space for activation and arrows for navigation.
8. Keep an accessible DOM representation for focus and Playwright selectors.

The Pointer Events standard provides unified mouse, pen and touch input plus pointer capture. [W3C Pointer Events](https://www.w3.org/TR/pointerevents/)

Canvas accessibility requires one-to-one focusable fallback regions for interactive areas. [WHATWG Canvas accessibility guidance](https://html.spec.whatwg.org/multipage/canvas.html)

### Phase 7 — Protocol scaling, separately approved

Keep the current snapshot contract initially. If Main must truly support hundreds of thousands or millions of jobs, a later backend contract needs:

- Snapshot revision
- Insert/update/delete deltas
- Server-side filtering and sorting
- Cursor-based windows
- Compact block counts without loading complete Block DTOs

This is a separate backend change. Canvas alone cannot make a complete million-row JSON snapshot scalable.

### Phase 8 — Rollout

- Run the same behavioral suite against legacy, virtual and Canvas renderers.
- Shadow-compare sorted/filtered Bot Job IDs.
- Test Windows scaling at 100%, 125%, 150% and 200%.
- Test resizing, horizontal scrolling, keyboard operation, tooltips and rapid snapshot replacement.
- Keep the legacy renderer selectable until live parity passes.
- Commit each phase as an independent `CODEX-` checkpoint.

## Performance acceptance gates

On the slowest supported Windows reference machine:

- 60 FPS scrolling target; p95 frame below 16.7 ms.
- No interaction long task over 50 ms.
- Click-to-highlight below 50 ms.
- Search-to-result below 100 ms after worker initialization.
- First visible render below 250 ms after receiving a 10,000-row dataset.
- No retained-memory growth after 100 refresh cycles or a one-hour session.
- Constant visible DOM node count regardless of total rows.
- Zero behavioral differences across legacy and new renderer tests.

## Rejected options

| Technology | Verdict |
|---|---|
| PixiJS | Viable gaming-style 2D engine, but we would have to build grid navigation, text, editors and accessibility ourselves. Its event system is good, but excessive for this table. [PixiJS events](https://pixijs.com/8.x/guides/components/events) |
| Three.js/WebGPU | Wrong abstraction. Mouse interaction requires 3D raycasting, and its WebGPU renderer remains experimental. [Three.js Raycaster](https://threejs.org/docs/pages/Raycaster.html), [WebGPU renderer](https://threejs.org/manual/en/webgpurenderer) |
| C++/Direct2D | Maximum control but forces a complete rewrite of accessibility, text layout, DPI handling, events and tests. |
| Entire page in Canvas | Breaks normal focus, buttons, menus, text selection, screen readers and Playwright semantics. |
| Windows WebView2 host | Good future packaging option that can retain React and the selected grid renderer, but it is not itself a grid solution. [Microsoft WebView2](https://learn.microsoft.com/en-us/microsoft-edge/webview2/) |

No code was modified, built, committed, pushed or deployed during this investigation. The first implementation checkpoint should be **Phase 1 baseline plus Phase 2 isolated grid-model extraction**.
