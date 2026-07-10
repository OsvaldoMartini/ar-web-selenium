# New Bot Job React Backend Migration Roadmap

Date: 2026-07-10

## Objective

Migrate the JavaFX **New Bot Job** dialog to React TypeScript using the same embedded React/JCEF pattern now used by **NEW ORGANIZATION** and the **Main Dashboard**.

The first target is functional parity with `ARNewBotJobPane`: create a Bot Job from a selected Organization Environment, validate the form, insert `bot_job`, refresh the main dashboard list, and open Bot Job Details for the newly created job.

Java remains the backend/bridge during migration. React owns the form and the user interaction.

## Current Java Owner

Primary JavaFX screen:

- `src/main/java/com/allinweb/ch/component/scene/ARNewBotJobScene.java`
- `src/main/java/com/allinweb/ch/component/pane/ARNewBotJobPane.java`

Current backend/database methods:

- `PerformDBEngine.loadHomeBanking(null)`
- `PerformDBEngine.loadHomeUrls(null)`
- `PerformLists.getListHomeUrl()`
- `PerformLists.getListBotJob()`
- `PerformLists.getHomeUrlByBankId(homeBankingId, homeUrlId)`
- `PerformLists.getHomeBankingById(homeBankingId)`
- `PerformDataBase.createNewBotJob(BotJobLoadDTO createdBotJob)`
- `PerformDataBase.getNewBotJobId()`
- `PerformDataBase.loadQuickBotJobs()`

Current Java behavior:

- User chooses app type: `Web Apps`, `Android Apps`, `iOS Apps`; `Rest Api` exists but is hidden.
- User fills Bot Job name.
- User optionally fills description, but `PerformDataBase.createNewBotJob(...)` currently stores `name + " description"` instead of the typed description.
- User selects one `HomeUrlDTO` environment from a dropdown.
- User can refresh environments.
- User can open `Orgs / Environments`.
- On success, Java opens `ARViewBotJobScene` for the newly created Bot Job and closes the New Bot Job modal.

## Migration Rule

Keep `ARNewBotJobPane` available until the React version is verified end to end.

Do not create another JavaFX form. The Java side should only:

- open the React container
- bootstrap `window.receiveDataFromJava(...)`
- serve WebSocket commands
- reuse existing database methods
- open existing Bot Job Details after successful creation

Do not run Java/Maven as part of this roadmap execution unless explicitly requested.

## Actual Frontend Location And Packaging

Frontend repo:

- `/srv/projects/ar-react-ts-grid`

React source location:

- `/srv/projects/ar-react-ts-grid/src/components`

Suggested files:

- `src/components/NewBotJobManager.tsx`
- `src/components/NewBotJobManager.module.scss`

React bootstrap should be added to:

- `/srv/projects/ar-react-ts-grid/src/index.tsx`

Suggested session id:

- `newBotJobManager`

Build and bundle flow remains:

```bash
cd /srv/projects/ar-react-ts-grid
npm run build
cp -r build /srv/projects/ar-web-selenium/src/main/resources/
```

## Target UX

Use the same compact operational style as `OrganizationManager` and `MainDashboard`.

Suggested layout:

- Top bar:
  - title: `New Bot Job`
  - status pill: socket/data/save state
- App type segmented control:
  - `Web App`
  - `Android`
  - `iOS`
  - optional future: `Rest Api`
- Form fields:
  - Bot Job Name
  - Description
  - Organization/Environment dropdown
- Environment detail strip:
  - Organization
  - Environment name
  - URL
- Actions:
  - `Create Bot Job`
  - `Refresh Environments`
  - `Organizations / Environments`
  - `Cancel`

Design constraints:

- no nested cards
- no marketing layout
- no row or dropdown text wrapping
- dropdown uses ellipsis and tooltip/title for long URLs
- form must resize cleanly inside the WebView
- validation errors show inline/status, not only Java modals

## Backend Contract

Use the same WebSocket bridge style as `organizationManager` and `mainDashboard`.

React -> Java commands:

| Type | Payload | Result |
|---|---|---|
| `newBotJob.bootstrap` | `{}` | Returns app type options and environments |
| `newBotJob.environments` | `{}` | Reloads and returns environments |
| `newBotJob.create` | `{ name, description, priority, homeBankingId, homeUrlId, openAfterCreate }` | Creates Bot Job |
| `newBotJob.openOrganizations` | `{}` | Opens React Organizations manager |
| `newBotJob.cancel` | `{}` | Closes the New Bot Job container/modal |

Java -> React events:

| Type | Payload |
|---|---|
| `newBotJob.bootstrapResponse` | `{ appTypes: [], environments: [] }` |
| `newBotJob.environmentsResponse` | `{ environments: [] }` |
| `newBotJob.createResponse` | `{ ok, message, botJob?, botJobs?, error? }` |
| `newBotJob.actionResponse` | `{ ok, message, environments?, error? }` |
| `newBotJob.status` | `{ level: "info"|"success"|"warning"|"error", message }` |

DTO minimum:

```ts
type NewBotJobEnvironmentDTO = {
  id: number;
  name: string;
  url: string;
  homeBankingId: number;
  orgName: string;
};

type NewBotJobCreateRequest = {
  name: string;
  description: string;
  priority: "Web App" | "Android" | "iOS" | "Rest Api";
  homeBankingId: number;
  homeUrlId: number;
  openAfterCreate: boolean;
};
```

## Phase 1 - Backend Service Wrapper

Create a Java service around the current `ARNewBotJobPane.createBotJob()` logic.

Suggested class:

- `NewBotJobService`

Responsibilities:

- reload organizations/environments
- return environments as DTOs
- validate Bot Job name
- sanitize name using the same current rules from `nameFileOnWindows(...)`
- validate duplicate names
- validate selected environment exists
- map selected app type to current priority values:
  - `Web App`
  - `Android`
  - `iOS`
  - `Rest Api`
- call `PerformDataBase.createNewBotJob(...)`
- call `PerformDataBase.getNewBotJobId()`
- call `PerformDataBase.loadQuickBotJobs()` after success
- return the created Bot Job DTO and refreshed main dashboard rows

Important compatibility note:

- Decide whether to preserve the current bug/behavior where DB description is `name + " description"` or fix it to use the typed description. The React UI should send `description`; the backend service should make this behavior explicit before implementation.

Acceptance:

- service can list environments without JavaFX controls
- service rejects blank names
- service rejects duplicate names
- service rejects missing/removed environments
- service creates `bot_job` with correct `home_banking_id`, `home_url_id`, priority, and active status

## Phase 2 - WebSocket Command Handlers

Extend `SimpleWebSocketServer` with `newBotJob.*` command routing.

Implementation notes:

- follow `organization.*` and `mainDashboard.*`
- responses should go back to session `newBotJobManager`
- after successful create, also return fresh main dashboard row data
- if `openAfterCreate` is true, Java should open `ARViewBotJobScene` for the created Bot Job

Acceptance:

- `newBotJob.bootstrap` returns environments
- `newBotJob.create` creates a DB row and returns the new id
- main dashboard can refresh after creation

## Phase 3 - React Component

Create:

- `NewBotJobManager.tsx`
- `NewBotJobManager.module.scss`

React behavior:

- on mount, send `newBotJob.bootstrap`
- default app type is `Web App`
- environment dropdown displays `Organization | Environment | URL`
- `Refresh Environments` sends `newBotJob.environments`
- `Organizations / Environments` sends `newBotJob.openOrganizations`
- `Create Bot Job` validates locally before sending
- disable Create while saving
- after successful create:
  - show success status
  - optionally clear form
  - backend opens Bot Job Details if `openAfterCreate=true`

Acceptance:

- form creates a Bot Job from existing environments
- validation messages are visible in React
- dropdown refreshes without closing the screen
- long URLs do not break layout

## Phase 4 - Java React Container

Add a React container pane/scene equivalent to Organizations and Main Dashboard.

Suggested Java files:

- `ARNewBotJobManagerPane`
- `ARNewBotJobManagerScene`

Implementation notes:

- copy the anchoring/WebView pattern from `AROrganizationManagerPane` or `ARMainDashboardPane`
- use `WebBuildExtractor.getIndexUrl()`
- dispatch:

```java
window.receiveDataFromJava(JSON.stringify([]), port, "newBotJobManager", -1, "", -9999, "")
```

Acceptance:

- React form loads in the existing modal area
- WebView fills the scene
- maximize/resize does not clip form controls

## Phase 5 - Entry Point Swap

Change the Main Dashboard `New Bot Job` action to open the React New Bot Job manager instead of `ARNewBotJobScene`.

Migration options:

- Conservative: keep `ARNewBotJobScene` available but call `ARNewBotJobManagerScene` from `ARMainDashboardPane.openNewBotJob()`.
- Final: retire `ARNewBotJobPane` after React parity and tests pass.

Acceptance:

- Main Dashboard `New Bot Job` opens React form
- creating a Bot Job refreshes Main Dashboard
- creating a Bot Job opens Bot Job Details as before
- Organizations shortcut opens React Organizations manager

## Phase 6 - Tests

Add tests before retiring JavaFX `ARNewBotJobPane`:

- bootstrap loads environments from SQLite
- blank name is rejected
- duplicate name is rejected
- missing environment is rejected
- valid create inserts `bot_job`
- inserted row has correct `home_banking_id`, `home_url_id`, priority, active flag
- dashboard refresh includes the new Bot Job
- `openAfterCreate` sends/opens Bot Job Details
- Organizations shortcut sends `newBotJob.openOrganizations`

Playwright/sqlite tests should follow the `/srv/projects/ARWeb-Linux` harness style.

## Phase 7 - Documentation And Retirement

After verification:

- update screenshots under `specifications/screenshots`
- update user guide references for creating a Bot Job
- document whether description behavior was preserved or fixed
- mark `ARNewBotJobPane` and `ARNewBotJobScene` as retirement candidates

