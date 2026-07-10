# Clone Job React Backend Migration Roadmap

Date: 2026-07-10

## Objective

Migrate the JavaFX **Clone Job** dialog to React TypeScript using the same embedded React/JCEF pattern used by **NEW ORGANIZATION**, **Main Dashboard**, and **New Bot Job**.

The target is functional parity with `ARSaveClonePane`: clone a selected Bot Job, choose or create the target Organization Environment URL, duplicate the Excel data file, clone all dependent database rows, refresh the main dashboard, and report success or failure clearly.

Java remains the backend/bridge during migration. React owns the form and user interaction.

## Current Java Owner

Primary JavaFX screen:

- `src/main/java/com/allinweb/ch/component/scene/ARSaveCloneScene.java`
- `src/main/java/com/allinweb/ch/component/pane/ARSaveClonePane.java`

Current caller:

- `src/main/java/com/allinweb/ch/component/pane/ARMainDashboardPane.java`
- `src/main/java/com/allinweb/ch/facade/MainDashboardService.java`
- WebSocket command: `mainDashboard.cloneBotJob`

Current backend/database methods:

- `PerformDBEngine.loadHomeBanking(null)`
- `PerformDBEngine.loadHomeUrls(null)`
- `PerformLists.getQuickBotJobs()`
- `PerformLists.getListHomeBanking()`
- `PerformLists.getListHomeUrl()`
- `PerformLists.getHomeUrlsByBankId(homeBankingId)`
- `PerformDataBase.createNewHomeUrl(homeBankingId, url)`
- `PerformDataBase.getNewHomeUrlId()`
- `PerformDataBase.cloneBotJob(homeUrlDTO, sourceBotJobId, newName, newDescription)`
- `PerformDataBase.cloneBlock(sourceBotJobId)`
- `PerformDataBase.cloneInstructions(sourceBotJobId)`
- `PerformDataBase.cloneVariables(sourceBotJobId)`
- `PerformDataBase.cloneUpdateInstruction(sourceBotJobId)`
- `PerformDataBase.cloneReferences(sourceBotJobId)`
- `PerformDataBase.getNewBotBojId(sourceBotJobId)`
- `PerformDataBase.deleteBotJobData(newBotJobId)`
- `PerformDataBase.loadQuickBotJobs()`
- `ExcelUtils.createExcelDataFile(selectedBotJob, newBotJobName)`

Current Java behavior:

- Dashboard clone action requires a selected Bot Job.
- The clone modal opens with the source Bot Job name and source Organization Environment URL.
- User edits the new Bot Job name and description.
- User chooses an existing target environment URL from the same source organization, refreshes environments, opens `Orgs / Environments`, or types a URL.
- Blank Bot Job name is rejected.
- Duplicate Bot Job name is rejected against `performLists.getQuickBotJobs()`.
- Blank URL is rejected.
- `ExcelUtils.createExcelDataFile(...)` runs before database cloning.
- If the typed URL is not the source organization URL and no matching `HomeUrlDTO` exists, Java creates a new `home_url` row and then clones the job.
- Clone sequence inserts the new `bot_job`, then clones blocks, instructions, variables, update-instruction references, and saved references.
- On clone failure after a new Bot Job id exists, Java deletes the partial cloned job with `deleteBotJobData(newBotJobId)`.
- On success, Java shows the new Bot Job id/name/description and closes the modal.
- After the modal closes, the main dashboard reloads quick Bot Jobs and refreshes the legacy list view.

## Migration Rule

Keep `ARSaveClonePane` available until the React version is verified end to end.

Do not create another JavaFX form. The Java side should only:

- open the React container
- bootstrap `window.receiveDataFromJava(...)`
- serve WebSocket commands
- reuse existing database methods
- keep the existing rollback behavior
- refresh the main dashboard after success

Do not run Java/Maven as part of this roadmap execution unless explicitly requested.

## Actual Frontend Location And Packaging

Frontend repo:

- `/srv/projects/ar-react-ts-grid`

React source location:

- `/srv/projects/ar-react-ts-grid/src/components`

Suggested files:

- `src/components/CloneJobManager.tsx`
- `src/components/CloneJobManager.module.scss`

React bootstrap should be added to:

- `/srv/projects/ar-react-ts-grid/src/index.tsx`

Suggested session id:

- `cloneJobManager`

Build and bundle flow remains:

```bash
cd /srv/projects/ar-react-ts-grid
npm run build
cp -r build /srv/projects/ar-web-selenium/src/main/resources/
```

## Target UX

Use the same compact operational style as `OrganizationManager`, `MainDashboard`, and `NewBotJobManager`.

Suggested layout:

- Top bar:
  - title: `Clone Job`
  - status pill: socket/data/clone state
- Source Bot Job summary:
  - ID
  - name
  - description
  - organization
  - current environment name and URL
  - block/instruction counts if already available in the dashboard DTO
- Clone form:
  - New Bot Job Name
  - Description
  - target Organization Environment dropdown
  - typed URL field for creating or matching an environment URL
- Environment detail strip:
  - Organization
  - Environment name
  - URL
- Actions:
  - `Clone Bot Job`
  - `Refresh Environments`
  - `Organizations / Environments`
  - `Cancel`

Design constraints:

- no nested cards
- no marketing layout
- no row or dropdown text wrapping
- dropdown rows use ellipsis and tooltip/title for long URLs
- form must resize cleanly inside the WebView
- validation errors show inline/status, not only Java modals
- right-side limits must not clip in maximized containers

## Backend Contract

Use the same WebSocket bridge style as `organizationManager`, `mainDashboard`, and `newBotJob`.

React -> Java commands:

| Type | Payload | Result |
|---|---|---|
| `cloneJob.bootstrap` | `{ sourceBotJobId }` | Returns source Bot Job and environment options |
| `cloneJob.environments` | `{ sourceBotJobId }` | Reloads and returns environments for the source organization |
| `cloneJob.validateName` | `{ sourceBotJobId, name }` | Optional immediate duplicate/blank validation |
| `cloneJob.clone` | `{ sourceBotJobId, name, description, homeBankingId, homeUrlId, url, createExcelDataFile, openAfterClone }` | Clones the Bot Job |
| `cloneJob.openOrganizations` | `{}` | Opens React Organizations manager |
| `cloneJob.cancel` | `{}` | Closes the Clone Job container/modal |

Java -> React events:

| Type | Payload |
|---|---|
| `cloneJob.bootstrapResponse` | `{ sourceBotJob, environments }` |
| `cloneJob.environmentsResponse` | `{ environments }` |
| `cloneJob.validateNameResponse` | `{ ok, message, error? }` |
| `cloneJob.cloneResponse` | `{ ok, message, sourceBotJobId, clonedBotJobId?, clonedBotJob?, botJobs?, error? }` |
| `cloneJob.actionResponse` | `{ ok, message, environments?, error? }` |
| `cloneJob.status` | `{ level: "info"|"success"|"warning"|"error", message }` |

DTO minimum:

```ts
type CloneJobSourceDTO = {
  id: number;
  name: string;
  description: string | null;
  priority: string | null;
  homeBankingId: number;
  homeUrlId: number;
  organizationName: string | null;
  environmentName: string | null;
  environmentUrl: string | null;
};

type CloneJobEnvironmentDTO = {
  id: number;
  homeBankingId: number;
  orgName: string;
  name: string | null;
  url: string;
};

type CloneJobRequest = {
  sourceBotJobId: number;
  name: string;
  description: string;
  homeBankingId: number;
  homeUrlId: number | null;
  url: string;
  createExcelDataFile: boolean;
  openAfterClone: boolean;
};
```

## Phase 1 - Backend Service Wrapper

Create a Java service around the current `ARSaveClonePane.cloneBotJobSteps(...)` logic.

Suggested class:

- `CloneJobService`

Responsibilities:

- load organizations and environments when needed
- find and validate the source Bot Job by id
- return source Bot Job summary and valid environments for the source organization
- validate blank clone name
- validate duplicate clone name against quick Bot Jobs
- validate target URL is not blank
- match an existing `HomeUrlDTO` for the source organization when possible
- create a new `home_url` row when the typed URL does not already exist
- call `ExcelUtils.createExcelDataFile(...)` before database cloning, preserving current behavior
- run clone sequence:
  - `cloneBotJob`
  - `cloneBlock`
  - `cloneInstructions`
  - `cloneVariables`
  - `cloneUpdateInstruction`
  - `cloneReferences`
- call `getNewBotBojId(sourceBotJobId)` after clone
- call `deleteBotJobData(newBotJobId)` on clone failure after a new id exists
- call `loadQuickBotJobs()` after success
- return refreshed main dashboard rows

Acceptance:

- service rejects missing source Bot Job
- service rejects blank clone name
- service rejects duplicate clone name
- service rejects blank URL
- service can clone into an existing environment URL
- service can create a new environment URL and clone into it
- service rolls back partial cloned Bot Job data on downstream clone failure
- service returns new Bot Job id and refreshed dashboard rows after success

## Phase 2 - WebSocket Command Handlers

Extend `SimpleWebSocketServer` with `cloneJob.*` command routing.

Implementation notes:

- follow the patterns already used by `organization.*`, `mainDashboard.*`, and `newBotJob.*`
- responses should go back to session `cloneJobManager`
- `mainDashboard.cloneBotJob` should open the React Clone Job container instead of `ARSaveCloneScene`
- after successful clone, return fresh main dashboard row data
- if `openAfterClone` is true, Java may open `ARViewBotJobScene` for the cloned Bot Job after the clone succeeds

Acceptance:

- `cloneJob.bootstrap` returns source Bot Job and environments
- `cloneJob.environments` refreshes environment options
- `cloneJob.clone` returns structured success/failure
- failures do not depend on JavaFX modal parsing
- main dashboard can refresh after clone

## Phase 3 - React Clone Job Manager

Create:

- `/srv/projects/ar-react-ts-grid/src/components/CloneJobManager.tsx`
- `/srv/projects/ar-react-ts-grid/src/components/CloneJobManager.module.scss`

Frontend responsibilities:

- bootstrap using `cloneJob.bootstrap`
- show source Bot Job summary
- prefill new name from the source name, but make the user responsible for choosing a unique final name
- prefill description from the source description when available
- list target environments for the source organization
- update the URL field when the dropdown changes
- allow typed URL for new environment creation
- show inline validation for name, duplicate name, and blank URL
- disable Clone button while saving
- show final success/failure status
- close on Cancel

Acceptance:

- component renders inside the current JCEF container
- all text stays within its container at normal and maximized sizes
- environment dropdown does not wrap long URLs
- clone action sends one complete request
- success updates status and can close or open cloned job details based on backend behavior

## Phase 4 - Java React Container

Create Java container classes matching the existing React modal pattern.

Suggested classes:

- `ARCloneJobManagerScene`
- `ARCloneJobManagerPane`

Responsibilities:

- hold the source Bot Job id/context
- open React session `cloneJobManager`
- size the modal at least as large as the old 800 x 450 dialog, with better maximize behavior
- close from `cloneJob.cancel`
- reload quick Bot Jobs and refresh dashboard after successful clone

Acceptance:

- Main Dashboard Clone action opens React, not the old JavaFX pane
- modal/container maximization expands the React root cleanly
- no JavaFX clone form is shown during the new flow

## Phase 5 - Entry Point Swap

Modify current callers:

- `ARMainDashboardPane.openCloneBotJob(...)`
- `MainDashboardService.cloneBotJob(...)`

Target behavior:

- dashboard validates selected Bot Job
- Java opens `ARCloneJobManagerScene` with source Bot Job id
- React handles clone form
- backend clone response refreshes dashboard data

Keep old `ARSaveCloneScene` and `ARSaveClonePane` in the repo until the new flow is stable.

Acceptance:

- Clone button on Main Dashboard opens React Clone Job manager
- selecting/canceling/refreshing works without JavaFX clone dialog
- successful clone appears in Main Dashboard grid after refresh

## Phase 6 - Test Plan

Do not use Maven/Java compilation for this migration unless explicitly requested.

Recommended tests:

- React build:
  - `npm run build` in `/srv/projects/ar-react-ts-grid`
- Bootstrap:
  - valid source Bot Job returns source summary and environments
  - missing source Bot Job returns a clear error
- Validation:
  - blank name is rejected
  - duplicate name is rejected
  - blank URL is rejected
- Clone existing environment:
  - new `bot_job` row exists
  - cloned blocks exist
  - cloned instructions exist
  - cloned variables exist
  - cloned references exist
- Clone new environment URL:
  - new `home_url` row exists
  - cloned Bot Job points to the new `home_url_id`
- Failure handling:
  - forced downstream clone failure deletes partial cloned Bot Job data
- UI:
  - dropdowns do not wrap or clip
  - maximized container expands React content
  - status messages appear inline
  - dashboard refresh shows cloned job

## Phase 7 - Retirement

After the React Clone Job flow is verified:

- mark `ARSaveClonePane` and `ARSaveCloneScene` as legacy
- remove direct JavaFX clone entry points only after all callers use React
- keep database clone methods centralized in `CloneJobService`
- document any retained behavior from the old flow, especially Excel data file creation before database clone

## Open Questions Before Implementation

- Should the default cloned name be exactly the source name, source name plus suffix, or blank?
- Should `openAfterClone` default to opening Bot Job Details, or should the user stay on Main Dashboard?
- Should creating a new environment URL also require an environment name now that environments support names?
- Should Excel file creation be optional in the UI or always preserved silently for compatibility?
