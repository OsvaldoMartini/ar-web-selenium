# Main Page React Dashboard Migration Roadmap

Date: 2026-07-10

## Objective

Migrate the JavaFX main page to React TypeScript using the same embedded React/JCEF pattern used by **NEW ORGANIZATION**.

The target screen is the application landing page that currently shows the top command buttons and the Bot Job list. The React version must use one operational grid only. Java remains the backend/bridge during migration; React owns the main page UI.

## Current Java Owner

Primary JavaFX screen:

- `src/main/java/com/allinweb/ch/component/scene/ARMainScene.java`
- `src/main/java/com/allinweb/ch/component/pane/ARMainPane.java`
- `src/main/java/com/allinweb/ch/component/listCell/BotJobListCell.java`

Current data source:

- `PerformDataBase.loadQuickBotJobs()`
- `PerformLists.getQuickBotJobs()`
- `BotJobLoadDTO`
- `HomeBankingLoadDTO`
- `BlockLoadDTO`

Current main-page actions:

- `Organizations`
- `New Bot Job`
- `Clone Job`
- `Config`
- `Info`
- `Launch`
- `Open Job`
- `Exit`
- row double-click opens Bot Job details
- row delete removes the selected Bot Job after confirmation

## Migration Rule

Keep the JavaFX `ARMainPane` available until the React dashboard can list, select, open, launch, clone, delete, and refresh Bot Jobs using existing database data.

Do not create a new JavaFX redesign. The Java side should only:

- open the React container
- bootstrap `window.receiveDataFromJava(...)`
- serve WebSocket commands
- keep existing scenes/actions available as backend actions during transition

Do not run Java/Maven as part of this roadmap execution unless explicitly requested.

## Actual Frontend Location And Packaging

Frontend repo:

- `/srv/projects/ar-react-ts-grid`

React source location:

- `/srv/projects/ar-react-ts-grid/src/components`

Suggested files:

- `src/components/MainDashboard.tsx`
- `src/components/MainDashboard.module.scss`
- optional later split: `src/components/mainDashboardTypes.ts`

Build and bundle flow stays the same as NEW ORGANIZATION:

```bash
cd /srv/projects/ar-react-ts-grid
npm run build
cp -r build /srv/projects/ar-web-selenium/src/main/resources/
```

React bootstrap should be added to:

- `/srv/projects/ar-react-ts-grid/src/index.tsx`

Suggested session id:

- `mainDashboard`

## Target UX

The screen should match the NEW ORGANIZATION visual language:

- blue top bar
- dense operational layout
- restrained borders
- no nested cards
- no marketing/hero layout
- all rows fit inside the container without right-side clipping
- main grid has fixed header and internal scroll
- text should not wrap inside grid cells unless explicitly allowed

There will be only one grid: **Bot Jobs**.

Suggested layout:

- Top bar:
  - title: `AR Web`
  - status pill: connection/loading/saved/error
- Command bar:
  - Organizations
  - New Bot Job
  - Clone Job
  - Config
  - Info
  - Launch
  - Open Job
  - Exit
  - Refresh
- Single Bot Jobs grid:
  - ID
  - Name
  - Description
  - Organization
  - Environment
  - Type/Priority
  - Status
  - Blocks
  - Actions

Grid behavior:

- single-click selects a Bot Job
- double-click opens Bot Job details
- delete button shows React confirmation before backend delete
- selected row enables/disables action buttons
- Launch disabled for mobile jobs, same as current Java behavior
- row status shows Active/Inactive
- grid refreshes after create/clone/delete/config changes

## Backend Contract

Use the same WebSocket bridge style as `organizationManager`.

React -> Java commands:

| Type | Payload | Result |
|---|---|---|
| `mainDashboard.list` | `{}` | Returns current quick Bot Jobs |
| `mainDashboard.openOrganizations` | `{}` | Opens React Organizations manager |
| `mainDashboard.newBotJob` | `{}` | Opens current New Bot Job flow |
| `mainDashboard.cloneBotJob` | `{ botJobId }` | Opens clone flow for selected job |
| `mainDashboard.openBotJob` | `{ botJobId }` | Opens Bot Job details |
| `mainDashboard.launchBotJob` | `{ botJobId }` | Launches selected Bot Job after existing validations |
| `mainDashboard.deleteBotJob` | `{ botJobId }` | Deletes Bot Job and returns refreshed list |
| `mainDashboard.openConfig` | `{}` | Opens configuration flow |
| `mainDashboard.openInfo` | `{}` | Opens info window |
| `mainDashboard.exit` | `{}` | Runs existing close WebDriver / exit behavior |

Java -> React events:

| Type | Payload |
|---|---|
| `mainDashboard.listResponse` | `{ botJobs: [] }` |
| `mainDashboard.actionResponse` | `{ ok, message, botJobs?, selectedBotJobId?, error? }` |
| `mainDashboard.status` | `{ level: "info"|"success"|"warning"|"error", message }` |

DTO minimum:

```ts
type MainDashboardBotJobDTO = {
  id: number;
  name: string;
  description: string | null;
  priority: string | null;
  active: boolean;
  homeBankingId: number | null;
  homeUrlId: number | null;
  organizationName: string | null;
  environmentName: string | null;
  environmentUrl: string | null;
  blockCount: number;
};
```

## Phase 1 - Backend Service Wrapper

Create a Java service around the existing main-page behavior.

Suggested class:

- `MainDashboardService`

Responsibilities:

- load quick Bot Jobs through `PerformDataBase.loadQuickBotJobs()`
- map `BotJobLoadDTO` into a compact dashboard DTO
- centralize selected-job validation
- centralize launch eligibility rules
- perform delete through `PerformDataBase.deleteBotJobData(botJobId)`
- return structured responses instead of JavaFX-only modal results where possible

Acceptance:

- service returns the same jobs currently displayed by `ARMainPane`
- active/inactive status matches JavaFX list
- block count comes from `blockLoadDTOList`
- mobile jobs are marked not launchable

## Phase 2 - WebSocket Command Handlers

Extend `SimpleWebSocketServer` with `mainDashboard.*` command routing.

Implementation notes:

- follow the command shape already used by `organization.*`
- route all responses to session `mainDashboard`
- after create/clone/delete/config actions, reload and send `mainDashboard.listResponse`
- keep existing Java scenes callable for workflows that are not migrated yet

Acceptance:

- `mainDashboard.list` returns Bot Jobs without opening JavaFX controls
- delete returns fresh grid rows
- open/clone/new/config/info commands trigger existing flows during transition

## Phase 3 - React Component

Create:

- `MainDashboard.tsx`
- `MainDashboard.module.scss`

React behavior:

- on mount, send `mainDashboard.list`
- show one grid with fixed header and internal scroll
- selection state lives in React
- buttons call WebSocket commands
- confirmation template reused for delete
- inline status area shows backend messages
- no wrapped row text; use ellipsis and tooltips where needed
- use the same spacing/border/header style as `OrganizationManager.module.scss`

Acceptance:

- the main dashboard renders correctly inside the embedded browser
- resizing/maximizing keeps the grid full-width/full-height
- no right-edge clipping on grid rows
- the grid scrolls predictably

## Phase 4 - Java React Container

Add a React container pane/scene equivalent to the Organizations container.

Suggested Java files:

- `ARMainDashboardPane`
- `ARMainDashboardScene`

Implementation notes:

- copy the anchoring pattern from `AROrganizationManagerPane`
- use `WebBuildExtractor.getIndexUrl()`
- dispatch:

```java
window.receiveDataFromJava(JSON.stringify([]), port, "mainDashboard", -1, "", -9999, "")
```

Acceptance:

- React dashboard loads from the bundled build
- WebView fills the scene when maximized
- session id activates the React `MainDashboard` component

## Phase 5 - Entry Point Swap

Replace the JavaFX main pane entry after parity is verified.

Migration options:

- Conservative: `ARMainScene` builds `ARMainDashboardPane` while keeping `ARMainPane` available behind a fallback flag.
- Final: remove JavaFX main list rendering after React dashboard is stable.

Acceptance:

- app opens directly to React main dashboard
- Organizations button still opens React Organizations manager
- New Bot Job still works
- Open Job still opens Bot Job details
- Delete refreshes the grid
- Config flow still refreshes the grid when jobs change

## Phase 6 - Tests

Add focused tests before removing the JavaFX main page:

- list Bot Jobs from SQLite test DB
- select a row and enable Open/Clone/Delete
- double-click row sends `mainDashboard.openBotJob`
- delete confirmation sends `mainDashboard.deleteBotJob`
- delete response removes the row from grid
- Launch is disabled for mobile priorities and enabled for `Web App` / `Rest Api`
- Organizations command opens `organizationManager`
- maximize/resize visual check for grid width and scroll

Playwright test style should follow the new `/srv/projects/ARWeb-Linux` SQLite/Playwright harness where possible.

## Phase 7 - Documentation And Retirement

After verification:

- update screenshots under `specifications/screenshots`
- update scanner user guide references to the main dashboard
- document fallback flag removal
- mark JavaFX `ARMainPane` and `BotJobListCell` as retired candidates

