# AR Web Scanner — User Guide Part 3: MultiTest API Test Platform

> **Version:** 4.2 | **Audience:** QA Engineers, Test Automation Authors
>
> This guide covers the **MultiTest** React TypeScript application — the API testing and test management platform embedded inside the AR Web Scanner Bot Job View (toggled via the **API Tool** button) and also available as a standalone web application.
>
> **Part 1** covers the Java desktop Scanner application (main window, config, launch, backup).
> **Part 2** covers the Bot Job View, task blocks, AR Web Factory, and Scanner Grid in depth.
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
13. [Theme & Language](#13-theme--language)
14. [WebSocket Message Reference](#14-websocket-message-reference)
15. [Glossary](#15-glossary)

---

## 1. Overview

MultiTest is a multi-step API and functional testing workflow built in React TypeScript. It allows QA engineers to:

- **Upload OpenAPI specification files** and extract endpoints + field definitions.
- **Generate synthetic test data** from spec schemas.
- **Map API fields to Bot Job instructions** — linking back-end call parameters to recorded UI input fields.
- **Build and manage test flows** and requirements.
- **Execute test cases** against a configured API environment.
- **Monitor execution** in real time and download reports as CSV.

MultiTest communicates with the Java backend over the same WebSocket channel used by the Scanner.

![Bot Job View — API Tool toggle button opens the MultiTest React panel below the task instructions](screenshots/Bot_Job_Details_WebSite.png)

---

## 2. Accessing MultiTest

### Embedded in Bot Job View (recommended)

1. Open a bot job: Main Window → select job → **Open Job**.
2. In the Bot Job View toolbar, click **API Tool** (the toggle button).
3. The MultiTest React panel slides into view below the task instruction blocks.

### Standalone Web App

MultiTest also runs as an independent React SPA. The session ID must include `"apiTestToolAINew"` to activate the correct component. Connection parameters (WebSocket port, bot job ID, organization ID) are passed as URL query parameters or props.

---

## 3. Header & Navigation

> *The MultiTest header is visible at the top of the API Tool panel inside the Bot Job View.*

### Header Bar

| Element | Description |
|---|---|
| **A** logo | Application brand mark |
| **Environment badge** | Shows current Organization ID and name (`Environment:(N)-Name`) |
| **Bot Job badge** | Shows current Bot Job ID and name (`Bot Job:(N)-Name`) |
| **Generate Tests** button | Opens the **BizWizard** modal — auto-generates test cases from loaded API specs |
| **N API** count badge | Shows how many API spec files are currently loaded |
| **Theme toggle** | Switches between light and dark mode |

### Step Indicator (Progress Bar)

A horizontal stepper shows the 9 workflow steps as numbered circles connected by lines. Completed steps are shown in a different color from upcoming steps. Click any step to navigate directly to it.

### Tab Bar

Below the stepper is a tab row with the same 9 steps as larger clickable buttons. The active tab is highlighted.

---

## 4. Step 1 — API Files (Upload Specs)

> *Step 1 — API Files tab: drag-and-drop zone for uploading OpenAPI JSON/YAML specification files.*

### Purpose

Upload OpenAPI / Swagger JSON or YAML specification files. These specs drive all downstream tabs (Workflow, Data Gen, Functional Test, Ready for Test).

### Controls

| Control | What it does |
|---|---|
| **Drag & Drop zone** | Drop `.json` or `.yaml` files here to upload |
| **Click to browse** | Opens a system file picker |
| **Delete All** button | Removes all loaded specs and resets the data store |

### After Upload

Each loaded spec is listed with:

- File name
- API title (from `info.title`)
- Number of endpoints
- Number of fields extracted from schemas

The **N API** counter in the header updates immediately.

---

## 5. Step 2 — Workflow

> *Step 2 — Workflow tab: drag-and-drop API call sequencer for defining end-to-end chains.*

### Purpose

Define the logical chain of API calls that form an end-to-end workflow. Example: `POST /account` → `GET /account/{id}` → `DELETE /account/{id}`.

### Controls

The Workflow tab lets you drag and drop endpoints from the loaded specs into an ordered sequence. Each step in the chain can be:

- Assigned a **run group** (parallel or sequential execution)
- Linked to produce / consume resource IDs for flow-aware execution

---

## 6. Step 3 — Data Gen (Synthetic Test Data)

> *Step 3 — Data Gen tab: table of generated synthetic test case rows from spec field schemas.*

### Purpose

Automatically generate synthetic test payloads from the API spec field definitions. Each generated row becomes one test case in the **Ready for Test** queue.

### Controls

| Control | What it does |
|---|---|
| **Generate** button | Creates synthetic test cases from loaded specs and adds them to the test store |
| Generated data table | Preview of all generated rows with field values |

Generated test cases carry the source badge `Synthetic`.

---

## 7. Step 4 — Functional Test (Field Mapping)

> *Step 4 — Functional Test tab: three-column layout — API fields (left), active mappings (centre), Bot Job instructions (right).*

### Purpose

Map API specification fields to **Bot Job INPUT instructions**. This links API call parameters to the corresponding UI input fields in the recorded bot job, so that a single test run drives both the API and the UI automation together.

### How to Map Fields

1. **Click an API field card** on the left — it becomes "armed" (highlighted in the active color).
2. **Click a Bot Job input instruction** on the right — the pair is created and appears in the centre column.
3. **Click ✕** on any mapping in the centre column to remove it.

Mappings are auto-saved as a draft in `localStorage` after every change. Click **Save** to persist to the backend database.

### Use Cases

The Functional Test tab supports multiple named **Use Cases** per bot job:

| Control | What it does |
|---|---|
| **Use Case selector** | Dropdown to switch between named use cases |
| **+ New Use Case** | Creates a new named use case |
| **Rename** | Renames the currently selected use case |
| **Delete** | Deletes the selected use case and all its mappings |
| **Save** | Sends mappings to the backend via WebSocket (`funcTest.saveMappings`) |

### Field Direction Badges

| Badge | Meaning |
|---|---|
| **INPUT** | Field is write-only or a path / query parameter |
| **OUTPUT** | Field is read-only (response body only) |
| **I/O** | Field can be both read and written |

---

## 8. Step 5 — Flow Editor

> *Step 5 — Flow tab: ordered step list with up/down reorder controls and per-step remove button.*

### Purpose

Build ordered automation **flows** — named sequences of steps that correspond to bot job blocks. Flows define how instructions are chained across multiple pages or actions.

### Controls

| Control | What it does |
|---|---|
| **Flow selector** | Dropdown to switch between saved flows |
| **+ New Flow** | Creates a new flow (`flow.save`) |
| **Rename** | Renames the selected flow |
| **Delete** | Deletes the flow and all its steps |
| **+ Add Step** | Appends a new step to the current flow |
| **↑ / ↓ arrows** | Reorder steps within the flow |
| **✕ on a step** | Removes that step from the flow |
| **Save Steps** | Persists the current step order to the backend (`flow.steps.save`) |

---

## 9. Step 6 — Requirements

> *Step 6 — Requirements tab: requirement cards with coverage badges and traceability link controls.*

### Purpose

Manage test **requirements** and their **traceability links** to use cases and flows. This implements a lightweight Requirements Traceability Matrix (RTM).

### Controls

| Control | What it does |
|---|---|
| **+ New Requirement** | Creates a new requirement record (`requirement.save`) |
| **Edit** | Opens inline rename for the selected requirement |
| **Delete** | Deletes the requirement and all its traceability links |
| **Coverage badge** | Shows how many use cases and flows are linked to this requirement |
| **Link Use Cases** | Opens a multi-select dialog to attach use cases to the requirement |
| **Link Flows** | Opens a multi-select dialog to attach flows to the requirement |
| **Save Links** | Persists all traceability links (`requirement.links.save`) |

---

## 10. Step 7 — Ready for Test

> *Step 7 — Ready for Test tab: environment selector bar at top, queued test cases with method badges and status indicators below.*

### Purpose

Review all queued test cases (from Data Gen and/or file imports), configure the target environment, and start a test run.

### Environment Selector Bar

A prominent horizontal bar shows the active environment at all times:

| Element | Description |
|---|---|
| **Color dot** | Type indicator: green = local, blue = dev, red = production |
| **Tag** | LOCAL / DEV / STAGING / PROD / CUSTOM |
| **Name** | Environment name (e.g., "Avaloq UAT") |
| **Base URL** | The API base URL (e.g., `https://api.bank.com`) |
| **Switch button** | Opens the environment dropdown |

> **WARNING:** When **PRODUCTION** is selected a red warning strip appears across the top of the tab. Any test execution will hit live data.

#### Environment Dropdown Actions

| Action | How |
|---|---|
| **Select environment** | Click any row in the dropdown list |
| **Edit base URL** | Click the pencil (✎) icon on the row, type the new URL, press Enter or click Save (✓) |
| **Remove custom env** | Click ✕ on any non-built-in environment row |
| **Add custom environment** | Click **+ Add custom environment** at the bottom, fill in Name and URL, click **+ Add** |

### Import Test Cases

| Control | What it does |
|---|---|
| **Import CSV** button | Opens a file picker; parses CSV rows (columns: `method`, `path`, `body`, `runGroup`, `apiTitle`) |
| **Import JSON** button | Opens a file picker; parses a JSON array of test objects |

Imported test cases carry the source badge `File`.

### Execution Controls

| Control | What it does |
|---|---|
| **Apply Env to All** | Re-resolves every test case URL against the currently selected environment base URL |
| **Reset to Pending** | Resets all passed/failed/running cases back to pending status |
| **Execution Mode** | **Flow** — resource IDs cascade between steps (POST creates, GET/PATCH use the returned ID). **Independent** — each case runs in isolation with no shared state. |
| **Flow Timeout (s)** | Seconds to wait per step in flow mode (1–120 s, default 15 s) |
| **Execute Flow** button | Starts execution in flow mode; immediately switches to the Running tab |
| **Execute Independent** button | Starts execution in independent mode |
| **Mock Server** button | Opens the Mock Server configuration modal |

### Filtering & Pagination

| Control | Options |
|---|---|
| **Status filter** | All / Pending / Passed / Failed |
| **Method filter** | ALL / GET / POST / PATCH / PUT / DELETE |
| **Page size** | 10 / 50 / 100 / 150 rows per page |
| **Pagination** | Previous / Next with page count |

### Test Case Rows

Each row in the list represents one API test case:

| Element | Description |
|---|---|
| **Status dot** | Empty circle = pending, spinning = running, checkmark = passed, X = failed |
| **#N** | Sequential case number |
| **R N** | Run group number |
| **Method chip** | Color-coded HTTP verb: POST (green) / GET (blue) / PATCH (orange) / PUT (amber) / DELETE (red) |
| **Source badge** | Synthetic / File |
| **API title** | Name from the loaded spec |
| **Path** | API endpoint path (e.g., `/accounts/{id}`) |
| **HTTP status badge** | Response code shown after execution (200, 201, 404, ERR…) |
| **Latency** | Round-trip time in milliseconds |
| **EDITED badge** | Amber badge shown when URL, headers, or body have been manually edited |
| **Play button** | Runs this single test case immediately |
| **Expand button** | Toggles the detailed request/response panel below the row |

### Expanded Panel (per test case)

Click the expand arrow or the row title to open the detail panel.

**REQUEST section:**

| Control | Description |
|---|---|
| **Method badge** | HTTP verb |
| **Request URL** | Editable; modifications show the EDITED badge |
| **Headers** | JSON text area (editable); **Format** button to pretty-print; red border if invalid JSON |
| **Request Body** | POST/PATCH/PUT only. JSON text area with **Format** button |
| **Run button** | Executes this single request; shows "Running…" while active |
| **Reset edits** | Visible only when EDITED — reverts URL/body/headers to original values |

**RESPONSE section** (appears after execution):

| Element | Description |
|---|---|
| **HTTP status badge** | Response code and latency |
| **Response Headers** | Key:value pairs |
| **Response Body** | JSON pretty-printed; green background = passed, red = failed |

**Meta footer:** Creation time, source, original file name (if imported), EDITED warning.

---

## 11. Step 8 — Running (Live Execution)

> *Step 8 — Running tab: stat cards at top, live execution indicator, Stop button, progress bar, and paginated case list.*

The Running tab shows real-time execution progress. It polls the test store every 150 ms and auto-scrolls to the currently executing case.

### Action Bar

| Element | Description |
|---|---|
| **Live Mode** button | Execution results are held in memory only |
| **Save Mode** button | Results are written to disk as CSV files; shows selected folder name and rows-per-file setting |
| **Download Report** button | Downloads CSV + HTML report for the current or last execution |
| **Load Previous CSV** button | Opens a file picker to load and display a historical execution CSV |
| **Close Loaded** button | Visible when a previous CSV is loaded — closes it and returns to live view |

### Stat Cards

| Card | Color | What it shows |
|---|---|---|
| **Total** | Blue | Total test case count for this run |
| **Pending** | Grey | Cases not yet executed |
| **Passed** | Green | Cases that returned a 2xx response |
| **Failed** | Red | Cases with an error or non-2xx response |
| **Avg Lat** | Amber | Average round-trip latency across executed cases |

### Progress Bar

A horizontal bar fills as cases are executed. The fill color reflects the pass rate: green (100%), amber (≥70%), red (<70%).

### Live Execution Indicator

While a run is active, a pulsing amber dot appears with the label **LIVE — execution in progress**.

| Button | What it does |
|---|---|
| **Stop** | Signals the execution engine to stop after the current case finishes. Remaining cases are reset to pending. |

### Method Breakdown

After execution completes, a row of per-HTTP-method tiles shows passed/total count and average latency for each method used in the run.

### Case List & Pagination

The same expandable row format as Ready for Test (see Section 10). Each row can be expanded to inspect the full request and response even after execution.

| Control | Options |
|---|---|
| **Cases per page** | 10 / 50 / 100 / 150 |
| **Navigation** | First / Previous / Page N of M / Next / Last |

---

## 12. Step 9 — Report

> *Step 9 — Report tab: stored results from previous runs with download and summary controls.*

### Purpose

View and manage the persistent test result store. Results accumulate here as test cases are executed across sessions.

### Controls

| Control | What it does |
|---|---|
| **Open Wizard** button | Opens BizWizard to regenerate test cases from the current specs |
| **Open Report** button | Opens the last saved HTML report in the system browser |
| **Results table** | All previously executed test cases with final pass/fail status, HTTP code, and latency |

---

## 13. Theme & Language

### Dark / Light Mode

The **Theme Toggle** (sun / moon icon) in the top-right corner of the header switches the entire MultiTest application between dark and light themes. The preference is stored per browser session.

### Language

The language picker is currently disabled (English only). The i18n framework (`react-i18next`) is in place for future language additions.

---

## 14. WebSocket Message Reference

MultiTest communicates with the Java backend over the shared WebSocket channel. All messages are JSON. The session identifier in each message routes it to the correct component.

### Inbound Messages (Java → React)

| `operationId` | Session | Payload | Effect |
|---|---|---|---|
| `searchTerms` | `scannerGrid` | `{ elementDetails: ElementDTO[], botJobId, botJobName }` | Populates or clears the Scanner Grid |
| `addPickOne` / `clonedElement` | `scannerGrid` | `{ elementDetails: ElementDTO[], afterXPath }` | Adds a single picked element at the correct position in the grid |
| `activate-insert-all` | `scanner-element-pane` | — | Re-enables the Insert All Elements button |
| `activate-update-all` | `scanner-element-pane` | — | Re-enables the Update All Elements button |
| `applyOcrSuggestions` | `scanner-element-pane` | `{ suggestions: [{ xPath, clientNamed }] }` | Applies OCR-derived display names to matching grid rows |
| `SEND_DOM_REVIEW` | `scannerGrid` | `{ url, title, pcName, email, htmlSizeKb }` | Opens the DOM Review confirmation modal |
| `REQUEST_SUPPORT` | `scannerGrid` | `{ url, pcName, email }` | Opens the general Support Request modal |
| `REQUEST_SUPPORT_ELEMENTS` | `scannerGrid` | `{ url, pcName, email }` | Opens the element-specific Support Request modal |

### Outbound Messages (React → Java)

| `type` | Session | Payload | Purpose |
|---|---|---|---|
| `SEND_ALL_ELEMENTS_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, botJobName, elementDetails: ElementDTO[] }` | Bulk insert all grid elements |
| `UPDATE_ALL_ELEMENTS_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, botJobName, elementDetails: ElementDTO[] }` | Bulk update existing instruction records |
| `NEW_ELEMENT_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, botJobName, elementDetails: [one] }` | Save single element as an instruction |
| `DETAILS_ELEMENT_DTO` | `scannerTool` | `{ homeBankingId, botJobId, elementDetails: [one] }` | Highlight element in the live browser |
| `TEST_INPUT_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, elementDetails: [one] }` | Send test keystroke to live element |
| `TEST_CLICK_DTO` | `scanner-element-pane` | `{ homeBankingId, botJobId, elementDetails: [one] }` | Send test click to live element |
| `CLEAR_HOVER_PICK_FILE` | `scanner-element-pane` | `{ homeBankingId, botJobId }` | Truncate `elementDTO-HP.json` on the backend |
| `DOM_REVIEW_RESPONSE` | current sessionId | `{ action: 'send' \| 'cancel' }` | Confirm or cancel DOM review |
| `SUPPORT_REQUEST_RESPONSE` | current sessionId | `{ action, message }` | Send or cancel general support request |
| `SUPPORT_REQUEST_ELEMENTS_RESPONSE` | current sessionId | `{ action, message, elementDetails: [one] }` | Send element-specific support request |

### Functional Test Verbs

| Verb | Direction | Body | Purpose |
|---|---|---|---|
| `funcTest.loadMappings` | React → Java | `{ useCaseId }` or `{ botJobId }` | Load field mappings for a use case |
| `funcTest.saveMappings` | React → Java | `{ botJobId, useCaseId, mappings: [] }` | Save or replace field mappings |
| `useCase.list` | React → Java | `{ botJobId }` | List all use cases for a bot job |
| `useCase.save` | React → Java | `{ useCase: { ... } }` | Create or rename a use case |
| `useCase.delete` | React → Java | `{ useCaseId }` | Delete a use case |
| `flow.list` | React → Java | `{ botJobId }` | List all flows for a bot job |
| `flow.save` | React → Java | `{ flow: { ... } }` | Create or rename a flow |
| `flow.delete` | React → Java | `{ flowId }` | Delete a flow |
| `flow.steps.load` | React → Java | `{ flowId }` | Load ordered steps for a flow |
| `flow.steps.save` | React → Java | `{ flowId, steps: [ ... ] }` | Replace all steps in a flow |
| `requirement.list` | React → Java | `{ botJobId }` | List requirements with coverage info |
| `requirement.save` | React → Java | `{ requirement: { ... } }` | Create or update a requirement |
| `requirement.delete` | React → Java | `{ requirementId }` | Delete a requirement |
| `requirement.links.save` | React → Java | `{ requirementId, useCaseIds: [], flowIds: [] }` | Save traceability links |

---

## 15. Glossary

| Term | Meaning |
|---|---|
| **Bot Job** | A recorded automation scenario; maps to a set of browser instructions in the database |
| **Use Case** | A named test scenario within a Functional Test mapping session |
| **Flow** | An ordered sequence of automation steps linked to bot job blocks |
| **Requirement** | A business requirement that can be linked to use cases and flows for traceability (RTM) |
| **Field Mapping** | A pairing between an API spec field and a Bot Job instruction input |
| **Test Case** | One API call with a specific method, path, and body; source is Synthetic or File |
| **Run Group** | A numeric grouping controlling which test cases execute together in flow mode |
| **Synthetic** | A test case generated automatically from an API spec field schema |
| **Independent mode** | Execution mode where each test case runs in isolation with no shared resource IDs |
| **Flow mode** | Execution mode where resource IDs cascade between steps (e.g., POST → GET uses the created ID) |
| **elementDTO-HP.json** | Backend file accumulating hover-picked elements across sessions; cleared by Clear Grid when Hover Pick mode is active |
| **clientNamed** | User-defined display name for a scanned element; overrides `definedName`/`someText` |
| **forceCoordinates** | Flag on an element instruction controlling which locator strategy the Engine uses at run time |
| **BizWizard** | Modal wizard that auto-generates test cases from the loaded API specs |
| **RTM** | Requirements Traceability Matrix — links requirements to use cases and flows |

---

*See **Part 1** for the main window, organizations, configuration, launch, and backup.*
*See **Part 2** for the Bot Job View, task blocks, AR Web Factory, Scanner Grid, and OCR.*
