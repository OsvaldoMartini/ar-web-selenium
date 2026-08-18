# AR Web Scanner — User Guide Part 2: MultiTest API Test Platform

> **Version:** 4.2 | **Audience:** QA Engineers, Test Automation Authors
>
> This guide covers the **MultiTest** React TypeScript application — the API testing and test management platform embedded inside the AR Web Scanner Bot Job View (toggled via the **API Tool** button) and also available as a standalone web application.
>
> **Part 1** covers the Java desktop Scanner application.
>
> Screenshots are embedded from `specifications/screenshots/`.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Accessing MultiTest](#2-accessing-multitest)
3. [Header & Navigation](#3-header--navigation)
4. [Step 1 — API Files (Upload Specs)](#4-step-1--api-files-upload-specs)
5. [Step 2 — Workflow](#5-step-2--workflow)
6. [Step 3 — Data Gen (Synthetic Test Data)](#6-step-3--data-gen-synthetic-test-data)
7. [Step 4 — Functional Test (Field Mapping)](#7-step-4--functional-test-field-mapping)
8. [Step 5 — Flow Editor](#8-step-5--flow-editor)
9. [Step 6 — Requirements](#9-step-6--requirements)
10. [Step 7 — Ready for Test](#10-step-7--ready-for-test)
11. [Step 8 — Running (Live Execution)](#11-step-8--running-live-execution)
12. [Step 9 — Report](#12-step-9--report)
13. [Scanner Grid (Element Picker Panel)](#13-scanner-grid-element-picker-panel)
14. [Theme & Language](#14-theme--language)
15. [WebSocket Message Reference](#15-websocket-message-reference)

---

## 1. Overview

MultiTest is a multi-step API and functional testing workflow built in React TypeScript. It allows QA engineers to:

- **Upload OpenAPI specification files** and extract endpoints + field definitions.
- **Generate synthetic test data** from spec schemas.
- **Map API fields to Bot Job instructions** (linking back-end calls to UI automation steps).
- **Build and manage test flows** and requirements.
- **Execute test cases** against a configured API environment.
- **Monitor execution** in real time and download reports as CSV.

MultiTest communicates with the Java backend over the same WebSocket channel used by the Scanner.

> *MultiTest is accessed via the **API Tool** toggle button inside the Bot Job View. See Bot Job View screenshot in Part 1.*

---

## 2. Accessing MultiTest

### Embedded in Bot Job View (recommended)

1. Open a bot job (Main Window → select job → **Open Job**).
2. In the Bot Job View toolbar, click **API Tool** (the toggle button).
3. The MultiTest React panel slides into view below the task instructions.

![Bot Job View — API Tool toggle opens the MultiTest React panel](../specifications/screenshots/Bot_Job_Details_WebSite.png)

### Standalone Web App

MultiTest also runs as an independent React SPA. The session ID must include `"apiTestToolAINew"` to activate the correct component. Connection parameters (socket port, bot job ID, organization ID) are passed as URL query parameters or props.

---

## 3. Header & Navigation

> *The MultiTest header is visible at the top of the API Tool panel inside the Bot Job View. See Part 1 Bot Job View screenshots.*

### Header Bar

| Element | Description |
|---|---|
| **A** logo | Application brand mark |
| **Environment badge** | Shows current Organization ID and name (`🏢 Environment:(N)-Name`) |
| **Bot Job badge** | Shows current Bot Job ID and name (`📋 Bot Job:(N)-Name`) |
| **Generate Tests** button | Opens the **BizWizard** modal — auto-generates test cases from loaded API specs |
| **N API** count badge | Shows how many API spec files are loaded |
| **Theme toggle** | Switches between light and dark mode (☀️ / 🌙) |

### Step Indicator (Progress Bar)

A horizontal stepper shows the 9 workflow steps as numbered circles connected by lines. Completed steps are shown in a different color from upcoming steps. Clicking any step navigates directly to it.

### Tab Bar

Below the stepper is a tab row with the same 9 steps as larger clickable buttons. The active tab is highlighted.

---

## 4. Step 1 — API Files (Upload Specs)

> *Step 1 — API Files tab: drag-and-drop zone for OpenAPI JSON/YAML files.*

### Purpose

Upload OpenAPI / Swagger JSON or YAML specification files. These specs drive all downstream tabs (Workflow, Data Gen, Functional Test, Ready for Test).

### Controls

| Control | What it does |
|---|---|
| **Drag & Drop zone** | Drop `.json` or `.yaml` files here |
| **Click to browse** | Opens file picker |
| **Delete All** button | Removes all loaded specs and resets the data store |

### After Upload

Each loaded spec is listed with:
- File name
- API title (from `info.title`)
- Number of endpoints
- Number of fields

The **N API** counter in the header updates immediately.

---

## 5. Step 2 — Workflow

> *Step 2 — Workflow tab: drag-and-drop API call sequencer.*

### Purpose

Define the logical chain of API calls that form an end-to-end workflow (e.g., `POST /account` → `GET /account/{id}` → `DELETE /account/{id}`).

### Controls

The Workflow tab lets you drag-and-drop endpoints from the loaded specs into an ordered sequence. Each step in the chain can be:
- Assigned a **run group** (parallel or sequential execution)
- Linked to produce/consume IDs for flow-aware execution

---

## 6. Step 3 — Data Gen (Synthetic Test Data)

> *Step 3 — Data Gen tab: shows generated synthetic test case rows from spec schemas.*

### Purpose

Automatically generate synthetic test payloads from the API spec field definitions. Each generated row becomes one test case in the **Ready for Test** queue.

### Controls

| Control | What it does |
|---|---|
| **Generate** button | Creates synthetic test cases from loaded specs and adds them to the test store |
| Generated data table | Preview of generated rows with all field values |

Generated cases have source badge `⚗ Synthetic`.

---

## 7. Step 4 — Functional Test (Field Mapping)

> *Step 4 — Functional Test tab: three-column layout — API fields (left), active mappings (centre), Bot Job instructions (right).*

### Purpose

Map API specification fields (left column) to **Bot Job INPUT instructions** (right column). This links the automated API call parameters to the corresponding UI input fields in the recorded bot job.

### How to Map Fields

1. **Click an API field card** on the left — it becomes "armed" (highlighted in the active color).
2. **Click a Bot Job input instruction** on the right — the pair is created and appears in the centre column.
3. **Click ✕** on any mapping in the centre to remove it.

Mappings are auto-saved as a draft in `localStorage` after every change. Click **💾 Save** to persist to the backend database.

### Use Cases

The Functional Test tab supports multiple **Use Cases** per bot job:

| Control | What it does |
|---|---|
| **Use Case selector** | Dropdown to switch between named use cases |
| **+ New Use Case** | Creates a new named use case |
| **Rename** | Renames the selected use case |
| **🗑 Delete** | Deletes the selected use case (and its mappings) |
| **💾 Save** | Sends mappings to backend via WebSocket (`funcTest.saveMappings`) |

### Field Direction Badges

| Badge | Meaning |
|---|---|
| **INPUT** | Field is write-only or a path/query parameter |
| **OUTPUT** | Field is read-only (response-only) |
| **I/O** | Field can be read and written |

> *Functional Test centre column: each mapped pair shows field name → instruction name with a ✕ remove button.*

---

## 8. Step 5 — Flow Editor

> *Step 5 — Flow tab: ordered step list with ↑↓ reorder controls and per-step ✕ remove.*

### Purpose

Build ordered automation **flows** — named sequences of steps that correspond to bot job blocks. Flows define how instructions are chained across multiple pages or actions.

### Controls

| Control | What it does |
|---|---|
| **Flow selector** | Dropdown to switch between flows |
| **+ New Flow** | Creates a new flow (`flow.save`) |
| **Rename** | Rename the selected flow |
| **🗑 Delete** | Deletes the flow and its steps |
| **+ Add Step** | Appends a new step to the flow |
| **↑ / ↓** arrows | Re-order steps |
| **✕** on a step | Removes that step |
| **💾 Save Steps** | Persists the step order to backend (`flow.steps.save`) |

---

## 9. Step 6 — Requirements

> *Step 6 — Requirements tab: requirement cards with coverage badges and traceability link controls.*

### Purpose

Manage test **requirements** and their **traceability links** to use cases and flows. This implements a Requirements Traceability Matrix (RTM).

### Controls

| Control | What it does |
|---|---|
| **+ New Requirement** | Creates a new requirement record (`requirement.save`) |
| **Edit** | Inline rename of a requirement |
| **🗑 Delete** | Deletes the requirement and all its links |
| **Coverage badge** | Shows how many use cases / flows are linked to this requirement |
| **Link Use Cases** | Opens a dialog to select which use cases cover this requirement |
| **Link Flows** | Opens a dialog to select which flows cover this requirement |
| **💾 Save Links** | Persists traceability links (`requirement.links.save`) |

---

## 10. Step 7 — Ready for Test

> *Step 7 — Ready for Test tab: environment selector bar at top, queued test cases with method badges and status below.*

### Purpose

Review all queued test cases (from Data Gen and/or file imports), configure the execution environment, and start a test run.

### Environment Selector Bar

A prominent horizontal bar at the top of the tab shows the active environment:

| Element | Description |
|---|---|
| **Color dot** | Environment type indicator (green = local, blue = dev, red = production) |
| **Tag** | LOCAL / DEV / STAGING / PROD / CUSTOM |
| **Name** | Environment name (e.g., "Local Dev", "Avaloq UAT") |
| **Base URL** | The API base URL (e.g., `https://api.bank.com`) |
| **▼ Switch** button | Opens the environment dropdown |

> **⚠ WARNING:** When **PRODUCTION** is selected a red warning strip appears — test execution will hit live data.

#### Environment Dropdown Actions

| Action | How |
|---|---|
| **Select environment** | Click any row in the dropdown |
| **Edit base URL** | Click the ✎ pencil button on the row, type new URL, press Enter or click ✓ Save |
| **Remove custom env** | Click ✕ on a non-built-in env row |
| **Add custom environment** | Click **+ Add custom environment** at the bottom, fill Name + URL, click **+ Add** |

> *Environment dropdown: rows show LOCAL / DEV / STAGING / PROD with edit ✎ and remove ✕ per-row controls.*

### Import Test Cases

| Control | What it does |
|---|---|
| **Import CSV** button | Opens a file picker; parses CSV rows into test cases (columns: `method`, `path`, `body`, `runGroup`, `apiTitle`) |
| **Import JSON** button | Opens a file picker; parses JSON array of test objects into test cases |

Imported cases have source badge `📄 File`.

### Execution Controls

| Control | What it does |
|---|---|
| **Apply Env to All** button | Re-resolves every test case's URL against the currently selected environment base URL |
| **↺ Reset to Pending** button | Resets all passed/failed/running cases back to pending status |
| **Execution Mode** | **Flow** = resource IDs cascade between steps (POST creates, GET/PATCH use the returned ID) / **Independent** = each case runs in isolation |
| **Flow Timeout (s)** | Seconds to wait per step in flow mode (1–120, default 15) |
| **▶▶ Execute Flow** button | Starts execution in **flow** mode; immediately switches to the Running tab |
| **⚗ Execute Independent** button | Starts execution in **independent** mode |
| **Mock Server** button | Opens the Mock Server configuration modal |

### Filtering & Pagination

| Control | Options |
|---|---|
| **Filter** | All / Pending / Passed / Failed |
| **Method filter** | ALL / GET / POST / PATCH / PUT / DELETE |
| **Page size** | 10 / 50 / 100 / 150 rows per page |
| **Pagination** | « ‹ Prev | Page N of M | Next › » |

### Test Case Rows

Each row shows one API test case:

| Element | Description |
|---|---|
| **Status dot** | ○ pending / ● running (spinning) / ✓ passed / ✗ failed |
| **#N** | Sequential case number |
| **R N** | Run group number |
| **Method chip** | Color-coded HTTP verb: POST (green) / GET (blue) / PATCH (orange) / PUT (amber) / DELETE (red) |
| **Source badge** | ⚗ Synthetic / 📄 File |
| **API title** | Name from the spec |
| **Path** | API path (e.g., `/accounts/{id}`) |
| **HTTP status badge** | Response status after execution (200, 201, 404, ERR…) |
| **Latency** | Round-trip time in milliseconds |
| **EDITED badge** | Shown in amber when you have modified the URL/body/headers |
| **▶ Play button** | Runs this single test case immediately |
| **▼/▲ button** | Expands / collapses the detailed request/response panel |

> *Test case expanded row: shows Request URL, Headers (with Format button), Body (with Format button), Run button, ↺ Reset; below that the Response section with status code and body.*

#### Expanded Panel (per test case)

When you click ▼ or the title area, the case expands to show:

**REQUEST section:**
- **Method badge** — HTTP verb
- **Request URL** — Editable text input (changes turn the border amber and show EDITED badge)
- **Headers** — JSON text area (editable); **Format** button to pretty-print; validation error shown in red if invalid JSON
- **Request Body** (POST/PATCH/PUT only) — JSON text area with **Format** button
- **Run button** — Executes this single request; shows ⟳ Running… while active
- **↺ Reset edits** button (shown only when EDITED) — reverts URL/body/headers to original values

**RESPONSE section** (shown after any execution):
- **◀ RESPONSE** header with HTTP status badge and latency
- Response Headers — key:value display
- Response Body — JSON pretty-printed (green = passed, red = failed)

**Meta footer:**
- Created time, Source, File name (if imported), EDITED warning

---

## 11. Step 8 — Running (Live Execution)

> *Step 8 — Running tab: stat cards (Total / Pending / Passed / Failed / Avg Latency), Live Mode toggle, Stop button, progress bar, and paginated case list.*

The Running tab shows real-time execution progress. It polls the test store every 150 ms and auto-scrolls to the currently executing case.

### Action Bar (always visible)

| Element | Description |
|---|---|
| **🔴 Live Mode** | Execution results live in memory only |
| **💾 Save Mode** | Results written to a chosen folder (CSV files) |
| **📁 folder badge** | (Save Mode) Shows selected folder name and rows-per-file |
| **⬇ Download Report** button | Downloads CSV + HTML report for the current or last execution |
| **📂 Load Previous CSV** button | Opens a file picker to load and view a previous execution CSV |
| **✕ Close Loaded** button | Closes a previously loaded execution and returns to live view |

### Stat Cards

| Card | Color | Shows |
|---|---|---|
| Total | Blue | Total test case count |
| Pending | Grey | Cases not yet executed |
| Passed | Green | Cases with 2xx response |
| Failed | Red | Cases with error or non-2xx |
| Avg Lat | Amber | Average latency across executed cases |

### Progress Bar

A horizontal bar fills as cases are executed. The percentage shows the pass rate (green = 100%, amber = ≥70%, red = <70%).

### Live Execution Indicator

While running, a pulsing amber dot appears:

> **● LIVE — execution in progress**

| Button | What it does |
|---|---|
| **■ Stop** | Signals the execution engine to stop after the current case. Remaining cases are reset to pending. |

### Method Breakdown (post-run)

After execution completes, a row of per-method stat tiles shows: passed/total count and average latency for each HTTP method used.

### Case List

Same expandable row format as Ready for Test (see above). Each row can be expanded to inspect the full request and response even after execution.

### Pagination

| Control | Options |
|---|---|
| **Cases per page** | 10 / 50 / 100 / 150 |
| **Navigation** | « ‹ N › » with page numbers |

---

## 12. Step 9 — Report

> *Step 9 — Report tab: stored results from previous runs with Download CSV button and pass/fail summary.*

### Purpose

View and manage stored test execution results (test store). Results accumulate here as test cases are executed.

### Controls

| Control | What it does |
|---|---|
| **Open Wizard** button | Opens the BizWizard to regenerate test cases from current specs |
| **Open Report** button | Opens the last saved HTML report |
| Results table | Shows all previously executed test cases with their final status |

---

## 13. Scanner Grid (Element Picker Panel)

The Scanner Grid is a separate React panel hosted in the **ARScannedElementScene** Java window. It is the primary interface for reviewing and saving web elements picked from the browser.

![Scanner Grid (AR Web Factory) — Input Text, Button, Link blocks with element rows and toolbar](screenshots/AR_Web_Factory.png)

### Opening the Grid

1. In the Bot Job View, click **Open Scanner**.
2. The browser opens and the ARScannedElementScene window appears alongside it.
3. Pick elements in the browser — they appear immediately in the grid.

### Grid Toolbar

| Control | What it does |
|---|---|
| **Find** text input | Filters rows in real time. Searches: tag, name, text, XPath, id, attribute values |
| **Insert All Elements** | Bulk-inserts all grid elements to the database. Button shows "Sending…" and is disabled until complete. |
| **Update All Elements** | Bulk-updates all existing database records. Shows "Updating…" during operation. |
| **Keep All** | Ticks every row's Keep checkbox |
| **Clear Keeps** | Clears all Keep ticks |
| **Delete Unchecked (N)** | Shows count of unchecked elements; click to open confirmation dialog then deletes them. Button turns red when there are elements to delete. |
| **Clear Grid All** | Empties the entire grid. Button turns dark when elements are present. |
| **Hover Pick** checkbox | When ticked, enables hover-pick cumulative mode. Clearing the grid also sends `CLEAR_HOVER_PICK_FILE` to the backend to truncate the running `elementDTO-HP.json` accumulation file. |
| **Rows per page** | 5 / 10 / 20 / 50 |

### Element-Type Blocks

Elements are automatically grouped by HTML tag type into named blocks:

| Tag | Block Label | Icon |
|---|---|---|
| `input`, `textarea` | **Input Text** | Input field icon |
| `select` | **Select Text** | Input field icon |
| `button` | **Button** | Click icon |
| `a`, `link` | **Link** | Chain link icon |
| `label` and others | **Output** | Output icon |

Each block has a collapsible header:

| Control | What it does |
|---|---|
| **−/+** icon | Collapse or expand the block |
| **#N** | Block sequence number |
| **Type icon + label** | Element type name |
| **(N)** count | Number of elements in this block |
| **Prev / Next** | Per-block pagination (only shown when count > rows-per-page) |
| **✕** (right end) | Removes entire block from the grid (all elements of that type) |

![Scanner Grid — element block header showing type icon, element count, Prev/Next pagination](screenshots/AR_Web_Factory.png)

### Per-Row Controls

| Control | What it does |
|---|---|
| **Keep checkbox** | Marks this element as "keep" — protects it from Delete Unchecked |
| **Display name** | Shows: `clientNamed` (user rename) → `definedName` → `someText` → `tagName`. For multi-token `someText` (comma/semicolon separated), a dropdown shows choices. |
| **Force badges** (CompForce) | Small letter badges: **F** = force XPath, **E** = force element, **T** = force text, **N** = none, **S** = force CSS selector. Click to cycle. These ride out on save as the `forceCoordinates` field. |
| **Pick button** (purple icon) | Highlights this element in the live browser (`DETAILS_ELEMENT_DTO`) |
| **Edit button** (pencil icon) | Opens inline rename. Pre-fills with the current display name. Press Enter or click Save to confirm; clear the input to remove the override. |
| **Save button** (disk icon) | Sends this element to the backend as an instruction record (`NEW_ELEMENT_DTO`) |
| **Test Input** (keyboard icon) | *Inputs and selects only.* Runs a test keystroke on the live element (`TEST_INPUT_DTO`) |
| **Test Click** (pointer icon) | Clicks the live element for verification (`TEST_CLICK_DTO`) |
| **✕** (remove) | Removes this row from the grid (frontend only) |

![Scanner Grid element row — Force badges, Pick, Edit, Save, Test Input, Test Click, Remove controls](screenshots/AR_Web_Factory.png)

### Modals

#### Alert Modal
Appears for error messages and confirmations. The **Delete Unchecked** confirmation is an Alert Modal with **Confirm** and **Cancel** buttons.

#### DOM Review Modal
Triggered automatically when the backend sends a `SEND_DOM_REVIEW` WebSocket message. Shows page URL, title, machine name, email, and HTML size. Click **Send for Review** or **Cancel**.

#### Support Request Modal
Triggered by `REQUEST_SUPPORT` or `REQUEST_SUPPORT_ELEMENTS`. Lets you attach a message before sending the element data to support.

---

## 14. Theme & Language

### Dark / Light Mode

The **Theme Toggle** in the top bar (☀️ / 🌙) switches the entire application between dark and light themes. The preference is stored per browser session.

> *Theme toggle (☀️/🌙) is in the top-right corner of the MultiTest header.*

### Language

The language picker is currently hidden (English only). The i18n framework is in place for future language additions.

---

## 15. WebSocket Message Reference

MultiTest communicates with the Java backend via the same WebSocket channel as the Scanner. All messages are JSON.

### Inbound Operations (Java → React)

| `operationId` | Session | Payload | Effect |
|---|---|---|---|
| `searchTerms` | `scannerGrid` | `{ elementDetails: ElementDTO[], botJobId, botJobName }` | Populates or clears the Scanner Grid |
| `addPickOne` / `clonedElement` | `scannerGrid` | `{ elementDetails: ElementDTO[], afterXPath }` | Adds a single picked element at the correct position |
| `activate-insert-all` | `scanner-element-pane` | — | Re-enables Insert All button |
| `activate-update-all` | `scanner-element-pane` | — | Re-enables Update All button |
| `applyOcrSuggestions` | `scanner-element-pane` | `{ suggestions: [{ xPath, clientNamed }] }` | Applies OCR-derived display names to matching rows |
| `SEND_DOM_REVIEW` | `scannerGrid` | `{ url, title, pcName, email, htmlSizeKb }` | Opens DOM Review modal |
| `REQUEST_SUPPORT` | `scannerGrid` | `{ url, pcName, email }` | Opens general Support Request modal |
| `REQUEST_SUPPORT_ELEMENTS` | `scannerGrid` | `{ url, pcName, email }` | Opens element-specific Support Request modal |

### Outbound Messages (React → Java)

| `type` | Session | Payload | Purpose |
|---|---|---|---|
| `SEND_ALL_ELEMENTS_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, botJobName, elementDetails: ElementDTO[] }` | Bulk insert all elements |
| `UPDATE_ALL_ELEMENTS_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, botJobName, elementDetails: ElementDTO[] }` | Bulk update all elements |
| `NEW_ELEMENT_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, botJobName, elementDetails: [one] }` | Save single element as instruction |
| `DETAILS_ELEMENT_DTO` | `scannerTool` | `{ homeBankingId, botJobId, elementDetails: [one] }` | Highlight element in browser |
| `TEST_INPUT_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, elementDetails: [one] }` | Test-type into a live element |
| `TEST_CLICK_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, elementDetails: [one] }` | Test-click a live element |
| `CLEAR_HOVER_PICK_FILE` | `scanner-element-pane` | `{ homeBankingId, botJobId }` | Truncate elementDTO-HP.json on backend |
| `DOM_REVIEW_RESPONSE` | current sessionId | `{ action: 'send' \| 'cancel' }` | Confirm or cancel DOM review |
| `SUPPORT_REQUEST_RESPONSE` | current sessionId | `{ action, message }` | Send or cancel support request |
| `SUPPORT_REQUEST_ELEMENTS_RESPONSE` | current sessionId | `{ action, message, elementDetails: [one] }` | Send element support request |

### Functional Test WebSocket Verbs

| Verb | Direction | Body | Purpose |
|---|---|---|---|
| `funcTest.loadMappings` | React → Java | `{ useCaseId }` or `{ botJobId }` | Load field mappings for a use case |
| `funcTest.saveMappings` | React → Java | `{ botJobId, useCaseId, mappings: [] }` | Save / replace field mappings |
| `useCase.list` | React → Java | `{ botJobId }` | List all use cases for a bot job |
| `useCase.save` | React → Java | `{ useCase: {...} }` | Create or rename a use case |
| `useCase.delete` | React → Java | `{ useCaseId }` | Delete a use case |
| `flow.list` | React → Java | `{ botJobId }` | List all flows for a bot job |
| `flow.save` | React → Java | `{ flow: {...} }` | Create or rename a flow |
| `flow.delete` | React → Java | `{ flowId }` | Delete a flow |
| `flow.steps.load` | React → Java | `{ flowId }` | Load ordered steps for a flow |
| `flow.steps.save` | React → Java | `{ flowId, steps: [...] }` | Replace all steps in a flow |
| `requirement.list` | React → Java | `{ botJobId }` | List requirements with coverage |
| `requirement.save` | React → Java | `{ requirement: {...} }` | Create or update a requirement |
| `requirement.delete` | React → Java | `{ requirementId }` | Delete a requirement |
| `requirement.links.save` | React → Java | `{ requirementId, useCaseIds: [], flowIds: [] }` | Save traceability links |

---

## Glossary

| Term | Meaning |
|---|---|
| **Bot Job** | A recorded automation scenario; maps to a set of browser instructions in the database |
| **Use Case** | A named test scenario within a Functional Test mapping session |
| **Flow** | An ordered sequence of automation steps |
| **Requirement** | A business requirement that can be linked to use cases and flows for traceability |
| **Field Mapping** | A pairing between an API spec field and a Bot Job instruction input |
| **Test Case** | One API call with a specific method, path, and body; can be Synthetic or from a File |
| **Run Group** | A numeric grouping of test cases executed together in flow mode |
| **Synthetic** | A test case generated automatically from API spec schema |
| **elementDTO-HP.json** | Backend file accumulating hover-picked elements across sessions; cleared by Clear Grid All when Hover Pick mode is active |
| **clientNamed** | User-defined display name for a scanned element (overrides `definedName`/`someText`) |
| **forceCoordinates** | Flag on an element controlling which locator strategy the Engine uses at run time (F/E/T/N/S) |

---

*See **Part 1** for the Java desktop Scanner application — Main Window, Bot Job View, Configuration, Launch, and Pre-Launch.*
